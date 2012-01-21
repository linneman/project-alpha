;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for radio buttons
;;;
;;; 2012-01-21, Otto Linnemann

(ns project-alpha-client.lib.radio-button
  (:require [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [project-alpha-client.lib.dispatch :as dispatch]
            [goog.ui.Button :as Button]
            [goog.ui.ToggleButton :as ToggleButton]
            [goog.ui.CustomButton :as CustomButton]
            [project-alpha-client.lib.json :as json])
  (:use [project-alpha-client.lib.logging :only [loginfo]]))


(defn- init-custom-button
  "decorates the custom button with given dom-string"
  [dom-id-str]
  (let [button (goog.ui.decorate (dom/get-element dom-id-str))]
    button))


(defn- render-button-group
  "renders a group of buttons according to specification
   which is a hash map of button id keywords and the button
   description hash table with the keys :dom-id-str and
   :action (the event to be dispatched when button is
   pressed).
   returns a hash map with the button id keyword and the
   the rendered button objects."
  [button-spec]
  (apply merge
         (map #(let [id (first %)
                     data (second %)
                     buttom (init-custom-button (:dom-id-str data))]
                 {id buttom})
              button-spec)))


(defn- apply-radio-button-events
  "applies the given event data from button-spec to a
   rendered button-group to form a group of radio button."
  [button-spec button-group]
  (doall (map
          (fn [button-def]
            (let [id (first button-def)
                  button (second button-def)
                  other-buttons (dissoc button-group id)
                  disp-evt (:action (button-spec id))]
              (. button (setAutoStates goog.ui.Component.State.CHECKED false))
              (events/listen button "action"
                             (fn [e] (when-not (. button (isChecked))
                                       (. button (setChecked true))
                                       (dispatch/fire disp-evt nil))))
              (doall (map (fn [h]
                            (let [to-disable-button (second h)
                                  mask goog.ui.Component.State.ALL]
                              (. button (setDispatchTransitionEvents mask true))
                              (events/listen button "action"
                                             (fn [e] (. to-disable-button
                                                        (setChecked false))))))
                          other-buttons))))
          button-group)))



(defn init-radio-button-group
  "renders and initializes group of buttons according to the
   given specification which is a hash map of button id
   keywords and a button description hash table.

   Example of a button-spec ('' to indicate string):

   {:status  { :dom-id-str 'nav-status-button'  :action :nav-status-clicked }
    :profile { :dom-id-str 'nav-profile-button' :action :nav-profile-clicked }
    ... }

   The function returns a hash map with the button id keyword
   and the the rendered button objects."
  [button-spec]
  (let [button-group (render-button-group button-spec)
        events (apply-radio-button-events button-spec button-group)]
    button-group))


(defn select-radio-button
  "select the specified radio button from given button group
   which should have been initialzed by the function
   init-radio-button-group."
  [select-button-key group]
  (doseq [[key button] group]
    (if (= key select-button-key)
      (. button (setChecked true))
      (. button (setChecked false)))
    group))
