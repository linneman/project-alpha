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
            [project-alpha-client.lib.dispatch :as dispatch]
            [project-alpha-client.app.profile :as profile])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils
         :only [get-modal-dialog open-modal-dialog
                send-request get-element init-alpha-button]]
        [project-alpha-client.lib.math :only [floor ceil abs]]))


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
     #{:send-msg-to-user}
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

  (defn age-range-min
    "age is blurred within five years groups,
     this function calculates low boundary."
    [age]
    (* 5 (floor (/ age 5))))

  (defn age-range-max
    "age is blurred within five years groups,
     this function calculates upper boundary."
    [age]
    (* 5 (ceil (/ (+ age 0.01) 5))))

  (defn hash-col-by-key
    "reforms a sequence of tuples (hash-maps) to
     a new hash-map with the given key values as
     keys and the rest of the tuples as values"
    [col key]
    (reduce merge
            {}
            (map #(hash-map (% key) (dissoc % key)) col)))

  (defn set-all-span-elems!
    "inserts string in all span elements with given id"
    [id string]
    (let [nodelist (gdom/getElementsByTagNameAndClass "span" id)]
      (loop [i (dec (. nodelist -length))]
        (set! (. (. nodelist (item i)) -innerHTML) string)
        (when (> i 0) (recur (dec i))))))

  (defn render-user-with-id
    "renders all user details data. In a first step
     we push the different aspects into an invisible
     dom element. Afterwards we retrieve the html data
     of this element and transfer it to the dialogs
     content pane."
    [id]
    (send-request (str "/profile/" id)
                  ""
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))
                          user-data (json/parse resp)
                          root (get-element "user-details-content")
                          free-text-elem (get-element "ud-html-text" root)
                          fav-books-sec (get-element "ud-fav-books-sec" root)
                          fav-movies-sec (get-element "ud-fav-movies-sec" root)
                          fav-books-by-rank (hash-col-by-key (user-data "user_fav_books")
                                                             "rank")
                          fav-movies-by-rank (hash-col-by-key (user-data "user_fav_movies")
                                                              "rank")]
                      (set! (. free-text-elem -innerHTML) (user-data "text"))
                      (if (empty? fav-books-by-rank) (style/showElement fav-books-sec false)
                          (style/showElement fav-books-sec true))
                      (if (empty? fav-movies-by-rank) (style/showElement fav-movies-sec false)
                          (style/showElement fav-movies-sec true))
                      (doseq [k [1 2 3]]
                        (set! (. (get-element (str "ud-favbook-auth" k) root) -innerHTML)
                              ((fav-books-by-rank k {}) "author"))
                        (set! (. (get-element (str "ud-favbook-title" k) root) -innerHTML)
                              ((fav-books-by-rank k {}) "title"))
                        (set! (. (get-element (str "ud-favmovie-auth" k) root) -innerHTML)
                              ((fav-movies-by-rank k {}) "author"))
                        (set! (. (get-element (str "ud-favmovie-title" k) root) -innerHTML)
                              ((fav-movies-by-rank k {}) "title")))
                      (set-all-span-elems! "ud-name" (user-data "name"))
                      (set-all-span-elems! "ud-age-min" (age-range-min (user-data "user_age")))
                      (set-all-span-elems! "ud-age-max" (age-range-max (user-data "user_age")))
                      (set-all-span-elems! "ud-zip" (user-data "user_zip"))
                      (set-all-span-elems! "ud-cities" (profile/zip-cities-hash
                                                        (user-data "user_zip")))
                      (. (user-details-dialog :dialog) (setTitle (user-data "name")))
                      (doseq [k (range 1 11)]
                        (let [rating (user-data (str "question_" k))
                              quest-txt (. (get-element (str "prof-quest" k)) -innerText)
                              answ-txt (if rating (. (get-element
                                                      (str "prof-rating" rating)
                                                      ) -innerText)
                                           " - ")]
                          (set! (. (get-element (str "ud-quest" k)) -innerText) quest-txt)
                          (set! (. (get-element (str "ud-answer" k)) -innerText) answ-txt)))
                      (render-user-data user-details-dialog (. root -innerHTML))))
                  "GET"))

  (comment

    ;(open-user-details-dialog user-details-dialog 100 :is-in-fav-list true)

    (open-dialog 100 :is-in-fav-list true)
    (render-sample-user)
    (render-user-with-id 50)
    (close-user-details-dialog user-details-dialog)

    (render-user-with-id 6)
    (render-user-with-id 998)
    (open-dialog 998 :is-in-fav-list true)

    (defn [age]
      ())

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
