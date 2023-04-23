(ns app.media-player
  (:require contrib.str
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as d]
            [hyperfiddle.electric-ui4 :as ui]))

#?(:clj
   (defonce !server-state (atom {:users {}
                                 :playing? false
                                 :current-time 0
                                 :allow-others? true
                                 :seeked-times 0
                                 :master-user nil})))

#?(:cljs
   (def !client-state (atom {:current-time 0
                             :interacted-with-document? false
                             :seeked-times 0
                             :duration 0
                             :muted? true})))

(e/def server-state (e/server (e/watch !server-state))) 

(e/def client-state (e/client (e/watch !client-state)))

(e/def session-id (e/server (get-in e/*http-request* [:headers "sec-websocket-key"])))

(e/defn Toggle-play []
  (if (and (:interacted-with-document? client-state) ; Respect browser autoplay policy
           (:playing? server-state)) 
    (.play (d/by-id "audio"))
    (.pause (d/by-id "audio"))))

(e/defn Is-master-user? []
  (= (:master-user server-state) session-id))

(e/defn Allowed-control? []
  (or (Is-master-user?.)
      (:allow-others? server-state)))

(e/defn Audio []
  "Audio element (invisible)"
  (d/audio
   (Toggle-play.)
   (d/props {:src "/avril-14th.mp3"
             :controls true
             :id "audio"
             :muted (:muted? client-state)
             :style {:display "none"}})
   (d/on "play"
         (e/fn [e]
           (set! (.-currentTime d/node) (:current-time server-state))))
   (d/on "timeupdate"
         (e/fn [e]
           (let [time (.. e -target -currentTime)]
             (swap! !client-state assoc :current-time time)
             (e/server
              (when (Is-master-user?.)
                (swap! !server-state assoc :current-time time))))))
   (d/on "loadedmetadata"
         (e/fn [e]
           (swap! !client-state assoc :duration (.-duration d/node))))
   (d/on "ended" (e/fn [e]
                   (when (Is-master-user?.)
                     (e/server (swap! !server-state assoc :playing? false)))))))

(e/defn Seek-bar []
  (d/div
   (d/style {:border "1px solid lightgray"
             :cursor (if (Allowed-control?.) "pointer" "default") 
             :height "1rem"})
   (when (not= (:seeked-times server-state) (:seeked-times client-state))
     (set! (.-currentTime (d/by-id "audio")) (:current-time server-state))
     (swap! !client-state assoc :seeked-times (:seeked-times server-state))
     (println "seeking to" (:current-time server-state)))
   (d/on "click"
         (when (Allowed-control?.)
          (e/fn [e]
           (let [progress-bar-element (.-currentTarget e)
                 progress-bar-rect (.getBoundingClientRect progress-bar-element)
                 click-x (.-clientX e)
                 progress-bar-width (.-width progress-bar-rect)
                 click-position (/ (- click-x (.-left progress-bar-rect)) progress-bar-width)
                 new-time (* click-position (:duration client-state))]
             (e/server (swap! !server-state assoc
                              :master-user session-id
                              :current-time new-time
                              :seeked-times (inc (:seeked-times @!server-state))))))))
   (d/div
    (d/style {:background (if (Allowed-control?.) "lightblue" "lightgray")
              :height "100%"
              :width (str (* 100 (/ (:current-time server-state) (:duration client-state))) "%")}))))

(e/defn Toggle-play-button []
  (ui/button
   (e/fn []
     (let [new-state (not (:playing? server-state))]
       (when new-state
         (swap! !client-state assoc :muted? false))
       (e/server (swap! !server-state assoc
                        :playing? new-state
                        :master-user session-id))
       (Toggle-play.)))
   (d/props {:disabled (not (Allowed-control?.))})
   (d/text (if (:playing? server-state) "Pause" "Play"))))

(e/defn Mute-button []
  (ui/button
   (e/fn []
     (swap! !client-state update :muted? not))
   (d/text (if (:muted? client-state)
             "Unmute"
             "Mute"))))

(e/defn Allow-others-checkbox [] 
  (when (Is-master-user?.)
    (d/label
     (ui/checkbox
      (:allow-others? server-state)
      (e/fn [e]
        (e/server (swap! !server-state update :allow-others? not))))
     (d/text "Allow others to take control?"))))

(e/defn Player-info []
  (d/div
   (d/div
    (d/text "Users: " (count (:users server-state))))
   (d/div (d/text
           (if (Is-master-user?.)
             "You are controlling the player"
             "Another user is controlling the player")))))

(e/defn Debug-info []
  (d/div
   (d/div (d/text "Server state: " server-state))
   (d/div (d/text "Client state: " client-state))
   (d/div (d/text " Diff: " (- (:current-time client-state)
                               (:current-time server-state))))))

(e/defn Media-player []
  (d/link (d/props {:rel :stylesheet :href "/style.css"}))
  (swap! !client-state assoc :seeked-times (e/server (:seeked-times @!server-state)))
  (d/on "pointerdown"
        (e/fn [e]
          (swap! !client-state assoc :interacted-with-document? true)))
  (Audio.)
  (Seek-bar.) 
  (Toggle-play-button.) 
  (Mute-button.)
  (Player-info.)
  (Allow-others-checkbox.)
  (Debug-info.)
  (e/server
   (swap! !server-state update :users assoc session-id {})
   (when (not (get (:users server-state) (:master-user server-state)))
     (println "resetting master user")
     (swap! !server-state assoc :master-user session-id))
   (e/on-unmount
    (fn []
      (swap! !server-state update :users dissoc session-id)
      (when (empty? (:users @!server-state))
        (swap! !server-state assoc :playing? false))))))