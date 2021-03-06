(ns beat-link-trigger.core
  "Top level organization for starting up the interface, logging, and
  managing online presence."
  (:require [beat-link-trigger.about :as about]
            [beat-link-trigger.logs :as logs]
            [beat-link-trigger.menus :as menus]
            [beat-link-trigger.triggers :as triggers]
            [seesaw.core :as seesaw]
            [taoensso.timbre :as timbre])
  (:import [org.deepsymmetry.beatlink DeviceFinder VirtualCdj]))

(defn try-going-online
  "Search for a DJ link network, presenting a UI in the process."
  []
  (let [searching     (about/create-searching-frame)
        want-metadata (triggers/request-metadata?)]
    (loop []
      (timbre/info "Trying to go online, Request Track Metadata?" want-metadata)
      (.setUseStandardPlayerNumber (VirtualCdj/getInstance) want-metadata)
      (if (try (.start (VirtualCdj/getInstance)) ; Make sure we can see some DJ Link devices and start the VirtualCdj
               (catch Exception e
                 (timbre/warn e "Unable to create Virtual CDJ")
                 (seesaw/invoke-now
                  (seesaw/hide! searching)
                  (seesaw/alert (str "<html>Unable to create Virtual CDJ<br><br>" e)
                                :title "DJ Link Connection Failed" :type :error))))
        (do  ; We succeeded in finding a DJ Link network
          (seesaw/invoke-soon (seesaw/dispose! searching))
          (timbre/info "Went online, using player number" (.getDeviceNumber (VirtualCdj/getInstance))))

        (do
          (seesaw/invoke-now (seesaw/hide! searching))  ; No luck so far, ask what to do
          (timbre/info "Failed going online")
          (let [options (to-array ["Try Again" "Quit" "Continue Offline"])
                choice  (seesaw/invoke-now
                         (javax.swing.JOptionPane/showOptionDialog
                          nil "No DJ Link devices were seen on any network. Search again?"
                          "No DJ Link Devices Found"
                          javax.swing.JOptionPane/YES_NO_OPTION javax.swing.JOptionPane/ERROR_MESSAGE nil
                          options (aget options 0)))]
            (case choice
              0 (do (seesaw/invoke-now (seesaw/show! searching)) (recur)) ; Try Again
              2 (seesaw/invoke-soon (seesaw/dispose! searching))          ; Continue Offline
              (System/exit 1)))))))  ; Quit, or just closed the window, which means the same

  (seesaw/invoke-now
   (triggers/start)))  ; We are online, or the user said to continue offline, so set up the Triggers window.

(defn start
  "Set up logging, make sure we can start the Virtual CDJ, then
  present the Triggers interface. Called when jar startup has detected
  a recent-enough Java version to succcessfully load this namespace."
  [& args]
  (logs/init-logging)
  (timbre/info "Beat Link Trigger starting.")
  (seesaw/invoke-now
   (seesaw/native!)  ; Adopt as native a look-and-feel as possible
   (System/setProperty "apple.laf.useScreenMenuBar" "false")  ; Except put menus in frames
   (try
     (let [skin-class (Class/forName "beat_link_trigger.TexturedRaven")]
       (org.pushingpixels.substance.api.SubstanceCortex$GlobalScope/setSkin (.newInstance skin-class)))
     (catch ClassNotFoundException e
       (timbre/warn "Unable to find our look and feel class, did you forget to run \"lein compile\"?"))))
  (menus/install-mac-about-handler)
  (try-going-online))
