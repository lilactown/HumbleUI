(ns io.github.humbleui.ui.window
  (:require
    [io.github.humbleui.app :as app]
    [io.github.humbleui.canvas :as canvas]
    [io.github.humbleui.core :as core]
    [io.github.humbleui.protocols :as protocols]
    [io.github.humbleui.window :as window]))

(defn window
  ([app] (window {} app))
  ([opts app]
   (let [{:keys [exit-on-close? title mac-icon screen width height x y bg-color]
          :or {exit-on-close? true
               title    "Humble 🐝 UI"
               width    800
               height   600
               x        :center
               y        :center
               bg-color 0xFFF6F6F6}} opts
         *mouse-pos (volatile! (core/ipoint 0 0))
         ref?       (instance? clojure.lang.IRef app)
         app-fn     (fn []
                      (cond
                        (instance? clojure.lang.IDeref app) @app
                        (fn? app) (app)
                        :else app))
         ctx-fn     (fn [window]
                      (when-not (window/closed? window)
                        {:window    window
                         :scale     (window/scale window)
                         :mouse-pos @*mouse-pos}))
         paint-fn   (fn [window canvas]
                      (canvas/clear canvas bg-color)
                      (let [bounds (window/content-rect window)]
                        (core/draw (app-fn) (ctx-fn window) (core/irect-xywh 0 0 (:width bounds) (:height bounds)) canvas)))
         event-fn   (fn [window event]
                      (core/when-every [{:keys [x y]} event]
                        (vreset! *mouse-pos (core/ipoint x y)))
                      (when-let [result (core/event (app-fn) (ctx-fn window) event)]
                        (window/request-frame window)
                        result))
         window     (window/make
                      {:on-close (when (or ref? exit-on-close?)
                                   #(do
                                      (when ref?
                                        (remove-watch app ::redraw))
                                      (when exit-on-close?
                                        (System/exit 0))))
                       :on-paint paint-fn
                       :on-event event-fn})
         screen     (if screen
                      (core/find-by :id screen (app/screens))
                      (app/primary-screen))
         {:keys [scale work-area]} screen
         x          (cond
                      (= :left x)   0
                      (= :center x) (-> (:width work-area) (- (* width scale)) (quot 2))
                      (= :right x)  (-> (:width work-area) (- (* width scale)))
                      :else         (* scale x))
         y          (cond
                      (= :top y)    0
                      (= :center y) (-> (:height work-area) (- (* height scale)) (quot 2))
                      (= :bottom y) (-> (:height work-area) (- (* height scale)))
                      :else         (* scale y))]
     (window/set-window-size window (* scale width) (* scale height))
     (window/set-window-position window (+ (:x work-area) x) (+ (:y work-area) y))
     (window/set-title window title)
     (when (and mac-icon (= :macos app/platform))
       (window/set-icon window mac-icon))
     (window/set-visible window true)
     (when ref?
       (add-watch app ::redraw
         (fn [_ _ old new]
           (when-not (identical? old new)
             (window/request-frame window)))))
     window)))
