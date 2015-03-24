(ns ^:figwheel-always openlayers-om-components.core
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [openlayers-om-components.geographic-element :refer [BoxMap]]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(defonce app-state
  (atom {:text  "Hello world!"
         ;; mark could be Box or Point
         ;; Box is defined by its extent: [:box [west south east north]]
         ;; http://openlayers.org/en/v3.1.0/apidoc/ol.html#Extent
         ;; and Point by its coords: [:point [x y]]
         ;; why to prefer vector over maps to represent sum types (variants):
         ;; https://www.youtube.com/watch?v=ZQkIWWTygio
         :mark  [:box [78.88 5.2 82.5 10.33]]
         :marks [[:box [32.196 36.130 44.094 46.293]]
                 [:box [144.231 -43.591 149.568 -39.319]]
                 [:point [30 36]]]}))

(defn DisplayExtent
  [[westBoundLongitude southBoundLatitude eastBoundLongitude northBoundLatitude]
   owner]
  (reify
    om/IDisplayName (display-name [_] "DisplayExtent")
    om/IRender
    (render [_]
      (html [:span.DisplayExtent
             "North: " northBoundLatitude ", "
             "East: " eastBoundLongitude ", "
             "South: " southBoundLatitude ", "
             "West: " westBoundLongitude]))))

(defn add-mark! [marks mode coords]
  (om/transact! marks #(conj % [mode (js->clj coords)])))

(defn del-mark! [marks mark]
  (om/transact! marks #(vec (remove #{mark} %))))

(om/root
 (fn [{:keys [mark marks text]} owner]
   (reify om/IRender
     (render [_]
       (html
        [:div.container
         [:h1 text]
         [:div.row

          [:div.col-sm-6
           [:h2 "BoxMap"]
           [:p "Allows one bounding box to be drawn (hold down shift)."]
           (om/build BoxMap
                     {:value     [mark]
                      :on-boxend #(om/update! mark [:box (js->clj %)])
                      :on-click #(om/update! mark [:point (js->clj %)])})
           [:p (om/build DisplayExtent (mark 1))]]

          [:div.col-sm-6

           [:h2 "MultiBoxMap"]
           [:p "Allows multiple bounding box to be drawn (hold down shift)."]
           (om/build BoxMap
                     {:value     marks
                      :on-boxend #(add-mark! marks :box %)
                      :on-click #(add-mark! marks :point %)})

           [:table.table.table-hover
            [:thead [:tr
                     [:th "North"]
                     [:th "West"]
                     [:th "South"]
                     [:th "East"]
                     [:th]]]
            [:tbody
             (for [[mode
                    [westBoundLongitude, southBoundLatitude,
                     eastBoundLongitude, northBoundLatitude] :as mark]
                   marks]
               [:tr
                [:td (name mode)]
                (case mode
                  :box (list
                        [:td (.toFixed northBoundLatitude 3)]
                        [:td (.toFixed westBoundLongitude 3)]
                        [:td (.toFixed southBoundLatitude 3)]
                        [:td (.toFixed eastBoundLongitude 3)])
                  :point (list
                          [:td (.toFixed southBoundLatitude 3)]
                          [:td (.toFixed westBoundLongitude 3)]
                          [:td "×"] [:td "x"]))
                [:td [:button.btn.btn-xs
                      {:on-hover #()
                       :on-click #(del-mark! marks mark)}
                      [:span.glyphicon.glyphicon-remove]]]])]]]]]))))

 app-state
 {:target (. js/document (getElementById "app"))})


