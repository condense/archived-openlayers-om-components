(ns ^:figwheel-always openlayers-om-components.core
    (:require[om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [sablono.core :as html :refer-macros [html]]
              [openlayers-om-components.geographic-element :refer [BoxMap MultiBoxMap]]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(defonce app-state (atom {:text    "Hello world!"

                          ;http://openlayers.org/en/v3.1.0/apidoc/ol.html#Extent
                          :extent  [78.88 5.2 82.5 10.33]
                          :extents [[32.196 36.130 44.094 46.293]
                                    [144.231 -43.591 149.568 -39.319]]}))


(defn DisplayExtent [extent owner]
  (reify
    om/IDisplayName (display-name [_] "DisplayExtent")
    om/IRender
    (render [_]
      (let [[westBoundLongitude southBoundLatitude eastBoundLongitude northBoundLatitude] extent]
        (html [:span.DisplayExtent
               "North: " northBoundLatitude", "
               "East: " eastBoundLongitude", "
               "South: " southBoundLatitude", "
               "West: " westBoundLongitude])))))


(defn add-extent! [extents extent]
  (om/transact! extents #(conj % extent)))


(defn del-extent! [extents extent]
  (om/transact! extents #(vec (remove (partial = extent) %))))


(om/root
  (fn [data owner]
    (reify om/IRender
      (render [_]
        (html
          [:div.container
           [:h1 (:text data)]
           [:div.row

            [:div.col-sm-6
             [:h2 "BoxMap"]
             [:p "Allows one bounding box to be drawn (hold down shift)."]
             (om/build BoxMap
                       {:value     (clj->js (:extent data))
                        :on-boxend #(om/update! data :extent %)})
             [:p (om/build DisplayExtent (:extent data))]]

            [:div.col-sm-6

             [:h2 "MultiBoxMap"]
             [:p "Allows one bounding box to be drawn (hold down shift)."]
             (om/build MultiBoxMap
                       {:value     (map clj->js (:extents data))
                        :on-boxend #(add-extent! (:extents data) %)})

             [:table.table.table-hover
              [:thead [:tr
                       [:th "North"]
                       [:th "West"]
                       [:th "South"]
                       [:th "East"]
                       [:th]]]
              [:tbody
               (for [extent (:extents data)]
                 (let [[westBoundLongitude, southBoundLatitude, eastBoundLongitude, northBoundLatitude] extent]
                   [:tr
                    [:td (.toFixed northBoundLatitude 3)]
                    [:td (.toFixed westBoundLongitude 3)]
                    [:td (.toFixed southBoundLatitude 3)]
                    [:td (.toFixed eastBoundLongitude 3)]
                    [:td [:button.btn.btn-xs
                          {:on-hover #()
                           :on-click #(del-extent! (:extents data) extent)}
                          [:span.glyphicon.glyphicon-remove]]]]))]]]]]))))

  app-state
  {:target (. js/document (getElementById "app"))})


