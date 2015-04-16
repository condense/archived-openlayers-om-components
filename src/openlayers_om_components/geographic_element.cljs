(ns openlayers-om-components.geographic-element
  (:require-macros [cljs.core.async.macros :refer [go-loop alt!]]
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
            [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn fmap [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defn debounce
  "Creates a channel which will change put a new value to the output channel
   after timeout has passed. Each value change resets the timeout. If value
   changes more frequently only the latest value is put out.

   When input channel closes, the output channel is closed."
  [in ms]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)]
        (alt!
          in    ([v] (if v (recur v) (close! out)))
          timer ([_] (do (>! out val) (recur nil))))))
    out))

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

(defmethod create-mark-feature :box [[_ extent] ch]
  (let [feature (-> extent clj->js
                    (ol.proj.transformExtent "EPSG:4326" "EPSG:3857")
                    ol.geom.Polygon.fromExtent
                    ol.Feature.)]
    (.on feature "change"
         #(put! ch [:box (ol.proj.transformExtent
                          (.. % -target getGeometry getExtent)
                          "EPSG:3857" "EPSG:4326")]))
    feature))

(defmethod create-mark-feature :point [[_ coords] ch]
  (let [feature (-> coords clj->js
                    (ol.proj.transform "EPSG:4326" "EPSG:3857")
                    ol.geom.Point.
                    ol.Feature.)]
    (.on feature "change"
         #(put! ch [:point (ol.proj.transform
                            (.. % -target getGeometry getCoordinates)
                            "EPSG:3857" "EPSG:4326")]))
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

(defn replace-mark-feature [value owner]
  (let [source (om/get-state owner :source)
        feature (create-mark-feature value (om/get-state owner :>change))]
    (when-let [feature (om/get-state owner :feature)]
      (.remove source feature))
    (.push (om/get-state owner :source) feature)
    (om/set-state! owner :feature feature)))

(defn Mark [{:keys [value mark-change-debounce on-mark-change]} owner]
  (reify
    om/IDisplayName (display-name [_] "Mark")
    om/IRender (render [_])
    om/IInitState
    (init-state [_]
      (let [>change (chan)]
        {:>change >change
         :<change (debounce >change (or mark-change-debounce 200))}))
    om/IDidMount
    (did-mount [_]
      (replace-mark-feature value owner)
      (go-loop []
        (when-let [[t v] (<! (om/get-state owner :<change))]
          (when on-mark-change
            (on-mark-change [t (js->clj v)]))
          (recur))))
    om/IDidUpdate
    (did-update [_ {prev-value :value} _]
      (when-not (= value prev-value)
        (if (= (value 0) (prev-value 0))
          (update-mark-feature value (om/get-state owner :feature))
          (replace-mark-feature value owner))))
    om/IWillUnmount
    (will-unmount [_]
      (.remove (om/get-state owner :source) (om/get-state owner :feature)))))

(defn BoxMap [{:keys [on-mark-change mark-change-debounce] :as props} owner]
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
                           (map-indexed
                            (fn [idx value]
                              {:value                value
                               :idx                  idx
                               :mark-change-debounce mark-change-debounce
                               :on-mark-change       (and on-mark-change
                                                          (partial on-mark-change idx))})
                            (:value props))
                           {:init-state
                            {:source (om/get-state owner :source)}})]))))
