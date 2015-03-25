(ns openlayers-om-components.geographic-element
  (:require-macros [openlayers-om-components.debug :refer [inspect]])
  (:require ol.Map
            ol.Collection
            ol.layer.Tile
            ol.View
            ol.proj
            ol.extent
            ol.source.MapQuest
            ol.FeatureOverlay
            ol.style.Style
            ol.style.Fill
            ol.style.Stroke
            ol.style.Circle
            ol.interaction.Draw
            ol.interaction.Pointer
            ol.interaction.Select
            ol.interaction.Scale
            ol.interaction.Translate
            ol.events.condition
            ol.geom.Polygon
            ol.geom.Point
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn fmap [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

; TODO: Modify interactions
; http://openlayers.org/ol3-workshop/controls/modify.html
; http://openlayers.org/en/v3.1.0/apidoc/ol.interaction.Modify.html
; http://openlayers.org/en/v3.1.1/examples/draw-and-modify-features.html

; NOTE: Vector labels
; http://openlayers.org/en/v3.2.0/examples/vector-labels.html

; NOTE: Showing labels on mouse hover
; http://openlayers.org/en/v3.1.1/examples/vector-layer.html?q=box

(defn dragBox
  "Create a dragBox"
  [{:keys [on-boxstart on-boxend]
    :or {on-boxstart identity
         on-boxend identity}}]
  (let [style
        (ol.style.Style.
         #js {:fill   (ol.style.Fill.
                       #js {:color "rgba(255, 255, 255, 0.2)"})
              :stroke (ol.style.Stroke.
                       #js {:color "#ffcc33" :width 2})
              :image  (ol.style.Circle.
                       #js {:radius 7
                            :fill   (ol.style.Fill. #js {:color "#ffcc33"})})})
        my-dragbox (ol.interaction.DragBox.
                    #js {:condition ol.events.condition.shiftKeyOnly
                         :style     style})
        extent! (fn [evt]
                  (let [extent (.. my-dragbox getGeometry getExtent)]
                    (ol.proj.transformExtent extent "EPSG:3857" "EPSG:4326")))]
    (doto my-dragbox
      (.on "boxstart" #(on-boxstart (extent! %)))
      (.on "boxend" #(on-boxend (extent! %))))
    my-dragbox))

(defn features<-event [features e]
  (.clear features)
  (.. e -map (forEachFeatureAtPixel (.-pixel e) #(.push features %))))

(defn hover-interaction []
  (let [features (ol.Collection.)
        interaction
        (ol.interaction.Pointer.
         #js {:handleMoveEvent (partial features<-event features)})]
    ;; mimic OpenLayers API convention
    (set! (.-getFeatures interaction) (constantly features))
    interaction))

(defn init-map! [owner props]
  (let [node (om/get-node owner "map")
        source (ol.source.MapQuest. #js {:layer "sat"})
        raster (ol.layer.Tile. #js {:source source})
        view (ol.View. #js {:center #js [-11000000 4600000]
                            :zoom 4
                            :maxZoom 10})
        vectorSource (ol.source.Vector.
                      #js {:projection (ol.proj.get "EPSG:4326")
                           :features   #js []})
        vectorLayer
        (ol.layer.Vector.
         #js {:source vectorSource
              :projection (ol.proj.get "EPSG:4326")
              :style  (ol.style.Style.
                       #js {:fill   (ol.style.Fill.
                                     #js {:color "rgba(255, 255, 255, 0.2)"})
                            :stroke (ol.style.Stroke.
                                     #js {:color "#ffcc33"
                                          :width 2})
                            :image  (ol.style.Circle.
                                     #js {:radius 7
                                          :fill   (ol.style.Fill.
                                                   #js {:color "#ffcc33"})})})})
        select (ol.interaction.Select.
                #js {:toggleCondition ol.events.condition.never})
        selected (.getFeatures select)
        scale (ol.interaction.Scale. #js {:features selected})
        translate (ol.interaction.Translate. #js {:features selected})
        hover (hover-interaction)
        on-boxstart (get props :on-boxstart identity)
        on-boxend (get props :on-boxend identity)
        dragBox (dragBox
                 {:on-boxstart #(on-boxstart %)
                  :on-boxend   #(on-boxend %)})
        map (ol.Map. #js {:layers #js [raster vectorLayer]
                          :target node
                          :view   view})]
    (.on map "click" (fn [e]
                       (when (and (.. e -browserEvent -shiftKey)
                                  (zero? (.. e -browserEvent -button))
                                  (:on-click props))
                         ((:on-click props)
                          (ol.proj.transform (.-coordinate e)
                                             "EPSG:3857" "EPSG:4326")))))
    (doseq [i [dragBox select scale translate hover]]
      (.addInteraction map i))
    (doto owner
      (om/set-state! :selected selected)
      (om/set-state! :map map)
      (om/set-state! :dragBox dragBox)
      (om/set-state! :view view)
      (om/set-state! :vectorSource vectorSource))))

(defn bbox-value [bbox]
  (let [bboxNums (fmap js/parseFloat bbox)
        invalid? (some js/isNaN (vals bboxNums))]
    (if-not invalid? bboxNums)))

(defn fit-view! [owner]
  (let [map (om/get-state owner :map)
        view (om/get-state owner :view)
        vectorSource (om/get-state owner :vectorSource)]
    (.fitExtent view (.getExtent vectorSource) (.getSize map))))

(defmulti create-mark-feature first)

(defmethod create-mark-feature :box [[_ extent :as props]]
  (let [feature (-> extent clj->js
                    (ol.proj.transformExtent "EPSG:4326" "EPSG:3857")
                    ol.geom.Polygon.fromExtent
                    ol.Feature.)]
    (.on feature "change"
         (fn [e]
           (-> e .-target .getGeometry .getExtent
               (ol.proj.transformExtent "EPSG:3857" "EPSG:4326")
               js->clj
               (#(om/update! props [:box %])))))
    feature))

(defmethod create-mark-feature :point [[_ coords :as props]]
  (let [feature (-> coords clj->js
                    (ol.proj.transform "EPSG:4326" "EPSG:3857")
                    ol.geom.Point.
                    ol.Feature.)]
    (.on feature "change"
         (fn [e]
           (-> e .-target .getGeometry .getCoordinates
               (ol.proj.transform "EPSG:3857" "EPSG:4326")
               js->clj
               (#(om/update! props [:point %])))))
    feature))

(defmulti update-mark-feature (fn [props feature] (first props)))

(defmethod update-mark-feature :box [[_ extent] feature]
  (let [extent (-> extent clj->js
                   (ol.proj.transformExtent "EPSG:4326" "EPSG:3857"))]
    (.. feature
        getGeometry
        (setCoordinates
         (.getCoordinates (ol.geom.Polygon.fromExtent extent))))))

(defmethod update-mark-feature :point [[_ coords] feature]
  (.. feature
      getGeometry
      (setCoordinates
       (-> coords clj->js (ol.proj.transform "EPSG:4326" "EPSG:3857")))))

(defn replace-mark-feature [props owner]
  (let [source (om/get-state owner :source)
        feature (create-mark-feature props)]
    (when-let [feature (om/get-state owner :feature)]
      (.remove source feature))
    (.push (om/get-state owner :source) feature)
    (om/set-state! owner :feature feature)))

(defn Mark [props owner]
  (reify
    om/IDisplayName (display-name [_] "Mark")
    om/IRender (render [_])
    om/IDidMount
    (did-mount [_]
      (replace-mark-feature props owner))
    om/IDidUpdate
    (did-update [_ prev-props _]
      (when-not (= props prev-props)
        (if (= (props 0) (prev-props 0))
          (update-mark-feature props (om/get-state owner :feature))
          (replace-mark-feature props owner))))
    om/IWillUnmount
    (will-unmount [_]
      (.remove (om/get-state owner :source) (om/get-state owner :feature)))))

(defn BoxMap [props owner]
  (reify
    om/IDisplayName (display-name [_] "BoxMap")
    om/IInitState
    (init-state [_]
      ;; Mark's did-mount is called before init-map!
      ;; → before vectorSource created
      ;; so we make proxy: observable collection
      (let [source (ol.Collection.)]
        (doto source
          (.on "add" #(when-let [v (om/get-state owner :vectorSource)]
                        (let [feature (.-element %)
                              selected (om/get-state owner :selected)]
                          (.addFeature v feature)
                          (doto selected (.clear) (.push feature)))))
          (.on "remove" #(when-let [v (om/get-state owner :vectorSource)]
                           (let [feature (.-element %)]
                             (.remove (om/get-state owner :selected) feature)
                             (.removeFeature v feature)))))
        {:source source}))
    om/IDidMount
    (did-mount [_]
      (init-map! owner props)
      (.addFeatures (om/get-state owner :vectorSource)
                    (.getArray (om/get-state owner :source)))
      (fit-view! owner))

    om/IDidUpdate
    (did-update [_ _ _]
      ;(fit-view! owner)
      )

    om/IRender
    (render [_]
      (html [:div.map {:ref "map"}
             (om/build-all Mark
                           (:value props)
                           {:init-state
                            {:source (om/get-state owner :source)}})]))))
