;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; dialog box with information about user details
;;;
;;; 2012-04-11, Otto Linnemann

(ns project-alpha-client.app.user-details-dialog
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [project-alpha-client.lib.json :as json]
            [clojure.browser.dom :as dom]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.ButtonRenderer :as ButtonRenderer]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.ui.Dialog :as Dialog]
            [goog.Timer :as timer]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils
         :only [get-modal-dialog open-modal-dialog
                send-request get-element init-alpha-button]]))


(when (dom/get-element "user-details")

  (defn- init-user-details-dialog
    "initialize user details dialog"
    []
    (let [[dialog] (get-modal-dialog
                    :panel-id "user-details"
                    :title-id "user-details-dialog-title"
                    :ok-button-id "confirm-user-details")
          render-button (fn [id evt data]
                          (let [button (goog.ui.decorate (dom/get-element id))]
                            (events/listen
                             button "action"
                             #(dispatch/fire evt (. dialog -userId)))
                            (. button (setEnabled true))
                            button))
          add-fav-button (render-button "add-user-to-fav" :add-user-to-fav)
          rm-fav-button (render-button "rm-user-from-fav" :rm-user-from-fav)
          send-msg-button (render-button "send-msg-to-user" :send-msg-to-user)]
      {:dialog dialog ; provided macro hash-args but this is only available at compile time in clsc
       :add-fav-button add-fav-button
       :rm-fav-button rm-fav-button
       :send-msg-button send-msg-button}))


  (defn- open-user-details-dialog
    "opens dialog and assigns user id to dialog
       field 'userId'"
    [dialog user-id & {:keys [is-in-fav-list] :or {is-in-fav-list false}}]
    (let [{:keys [dialog add-fav-button rm-fav-button send-msg-button]} dialog]
      (. add-fav-button (setVisible (not is-in-fav-list)))
      (. rm-fav-button (setVisible is-in-fav-list))
      (set! (. dialog -userId) user-id)
      (. dialog (setVisible true))))


  (defn- close-user-details-dialog
    "closes dialog"
    [{:keys [dialog]}]
    (. dialog (setVisible false)))


  (defn- render-user-data
    "renders user data"
    [dialog html-txt]
    (let [{:keys [dialog]} dialog
          html-txt-elem (get-element "free-text" (. dialog (getContentElement)))]
      (set! (. html-txt-elem -innerHTML) html-txt)
      ))


  (def ^{:private true
         :doc "event handler for user details dialog"}
    user-dialog-reactor
    (dispatch/react-to
     #{:add-user-to-fav :rm-user-from-fav :send-msg-to-user}
     (fn [evt data]
       (loginfo (pr-str evt data)))))


  (def ^{:private true
         :doc "permanently allocated dialog dedicated
               rendering details about a user."}
    user-details-dialog (init-user-details-dialog))


  (def ^{:doc "open user details dialog with user-id and optional
                 flag :is-in-fav-list. This function will be later
                 enhanced to load the html content via AJAX and
                 invoke the rendering for it."}
    open-dialog
    (partial open-user-details-dialog user-details-dialog))


  (def ^{:doc "close user details dialog"}
    close-dialog
    (partial close-user-details-dialog user-details-dialog))


  (defn render-sample-user
    "illustrates how the dialog will look like"
    []
    (let [title "Profildaten zu Nutzer xxx"
          long-par (str "<p>Dies ist ein sehr langer Absatz. Er sollte korrekt umgebrochen werden und wir sollten natuerlich hier auch vermeiden, dass horizontale Scrollbalken dargestellt werden. Mal sehen, ob das klappt!</p>")
          many-lines (reduce str (map #(str "<p>Zeile " % "</p>") (range 1 50)))
          sample-user-pic-url "<img src=\"http://central-services.dnsdojo.org/~ol/ottowiki/images/thumb/9/95/Ich_pb.png/160px-Ich_pb.png\" width=\"130\" align=\"right\">"
          sample-user-desc (str title sample-user-pic-url long-par many-lines)]
      (render-user-data user-details-dialog sample-user-desc)))


  (comment

    ;(open-user-details-dialog user-details-dialog 100 :is-in-fav-list true)

    (open-dialog 100 :is-in-fav-list true)
    (render-sample-user)
    (close-user-details-dialog user-details-dialog)

    )


  ;; mail stuff later moved to own directory
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "mail-dialog"
         :title-id "mail-dialog-title"
         :ok-button-id "confirm-mail"
         :dispatched-event :mail-dialog-confirmed
         :keep-open true)]
    (def mail dialog)
    (def confirm-mail-button ok-button)
    )

  ;(open-modal-dialog mail)
  ;(. mail (setVisible false))



) ; (when (dom/get-element "user-details")
