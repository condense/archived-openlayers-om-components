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
  (let [style (ol.style.Style.
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

(defn features->chan [features topic & [chan]]
  (let [chan (or chan (async/chan))]
    (doto features
      (.on "add" #(async/put! chan [topic :add (.-element %)]))
      (.on "remove" #(async/put! chan [topic :remove (.-element %)])))
    chan))

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
        vectorLayer (ol.layer.Vector.
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
        translate (ol.interaction.Translate. #js {:features (.getFeatures select)})
        hover (hover-interaction)
        interactions-chan (async/chan)
        map (ol.Map. #js {:layers #js [raster vectorLayer]
                          :interactions #js [select scale translate hover]
                          :target node
                          :view   view})]
    (->> interactions-chan
         (features->chan (.getFeatures select) :select)
         (features->chan (.getFeatures hover) :hover))
    (do (.addInteraction map dragBox)
        (om/set-state! owner :interactions-mult (async/mult interactions-chan))
        (om/set-state! owner :map map)
        (om/set-state! owner :dragBox dragBox)
        (om/set-state! owner :view view)
        (om/set-state! owner :vectorSource vectorSource))))

(defn bbox-value [bbox]
  (let [bboxNums (fmap js/parseFloat bbox)
        invalid? (some js/isNaN (vals bboxNums))]
    (if-not invalid? bboxNums)))

(defn add-bbox! [owner source extent]
  (let [extent3857 (ol.proj.transformExtent extent "EPSG:4326" "EPSG:3857")
        polygon (ol.geom.Polygon.fromExtent extent3857)
        feature (ol.Feature. polygon)]
    (.addFeature source feature)))

(defn fit-view! [owner]
  (let [map (om/get-state owner :map)
        view (om/get-state owner :view)
        vectorSource (om/get-state owner :vectorSource)]
    (.fitExtent view (.getExtent vectorSource) (.getSize map))))

(defn draw-bbox! [owner]
  (let [vectorSource (om/get-state owner :vectorSource)]
    (.clear vectorSource)
    (when-let [bbox (om/get-props owner :value)]
      (add-bbox! owner vectorSource bbox))))

(defn BoxMap [props owner]
  (reify
    om/IDisplayName (display-name [_] "BoxMap")
    om/IDidMount
    (did-mount [_]
      (init-map! owner props)
      (.on (om/get-state owner :dragBox) "boxstart" #(.clear (om/get-state owner :vectorSource)))
      (draw-bbox! owner)
      (fit-view! owner))
    om/IDidUpdate
    (did-update [_ _ _]
      (draw-bbox! owner)
      (fit-view! owner))
    om/IRender
    (render [_]
      (println :Boxmap props)
      (html [:div.BoxMap.map {:ref "map"} nil]))))

(defn draw-bboxes! [owner]
  (let [vectorSource (om/get-state owner :vectorSource)]
    (.clear vectorSource)
    (when-let [bboxes (om/get-props owner :value)]
      (doseq [bbox bboxes]
        (add-bbox! owner vectorSource bbox)))))

(defn MultiBoxMap [props owner]
  (reify
    om/IDisplayName (display-name [_] "MultiBoxMap")
    om/IDidMount
    (did-mount [_]
      (init-map! owner props)
      (draw-bboxes! owner)
      ;(fit-view! owner)
      )

    om/IDidUpdate
    (did-update [_ _ _]
      (draw-bboxes! owner)
      (fit-view! owner))

    om/IRender
    (render [_]
      (html [:div.MultiBoxMap.map {:ref "map"} nil]))))
