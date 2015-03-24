(ns openlayers-om-components.geographic-element
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [openlayers-om-components.debug :refer [inspect]])
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
            [cljs.core.async :as async]
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
        on-boxstart (get props :on-boxstart identity)
        on-boxend (get props :on-boxend identity)
        dragBox (dragBox
                 {:on-boxstart #(on-boxstart %)
                  :on-boxend   #(on-boxend %)})
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
        select (ol.interaction.Select. #js {})
        scale (ol.interaction.Scale. #js {:features (.getFeatures select)})
        translate (ol.interaction.Translate.
                   #js {:features (.getFeatures select)})
        hover (hover-interaction)
        map (ol.Map. #js {:layers #js [raster vectorLayer]
                          :interactions #js [select scale translate hover]
                          :target node
                          :view   view})]
    (do (.addInteraction map dragBox)
        (om/set-state! owner :map map)
        (om/set-state! owner :dragBox dragBox)
        (om/set-state! owner :view view)
        (om/set-state! owner :vectorSource vectorSource))))

(defn bbox-value [bbox]
  (let [bboxNums (fmap js/parseFloat bbox)
        invalid? (some js/isNaN (vals bboxNums))]
    (if-not invalid? bboxNums)))

(defn fit-view! [owner]
  (let [map (om/get-state owner :map)
        view (om/get-state owner :view)
        vectorSource (om/get-state owner :vectorSource)]
    (.fitExtent view (.getExtent vectorSource) (.getSize map))))

(defmulti create-mark-feature (fn [props] (first props)))

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

(defn Mark [props owner]
  (reify
    om/IDisplayName (display-name [_] "Mark")
    om/IRender (render [_])
    om/IDidMount
    (did-mount [_]
      (let [feature (create-mark-feature props)
            source (om/get-state owner :source)]
        (.push source feature)
        (om/set-state! owner :feature feature)))
    om/IDidUpdate
    (did-update [_ prev-props _]
      (when-not (= props prev-props)
        ;; REVIEW possible optimization:
        ;; only update coordinates when mode is unchanged
        (let [source (om/get-state owner :source)
              feature (create-mark-feature props)]
          (.remove source (om/get-state owner :feature))
          (.push (om/get-state owner :source) feature)
          (om/set-state! owner :feature feature))))
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
      (let [source (ol.Collection.)
            update-vector-source
            #(when-let [v (om/get-state owner :vectorSource)]
               (doto v (.clear) (.addFeatures (.getArray source))))]
        (doto source
          (.on "add" update-vector-source)
          (.on "remove" update-vector-source))
        {:source source}))
    om/IDidMount
    (did-mount [_]
      (init-map! owner props)
      (let [v (om/get-state owner :vectorSource)
            source (om/get-state owner :source)]
        (doto v (.clear) (.addFeatures (.getArray source))))
      ;(.on (om/get-state owner :dragBox)
      ; "boxstart" #(.clear (om/get-state owner :vectorSource)))
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
