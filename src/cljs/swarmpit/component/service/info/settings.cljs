(ns swarmpit.component.service.info.settings
  (:require [material.icon :as icon]
            [material.components :as comp]
            [material.component.form :as form]
            [material.component.chart :as chart]
            [material.component.label :as label]
            [swarmpit.component.common :as common]
            [swarmpit.component.action-menu :as menu]
            [swarmpit.component.state :as state]
            [swarmpit.docker.utils :as utils]
            [swarmpit.routes :as routes]
            [sablono.core :refer-macros [html]]
            [clojure.contrib.inflect :as inflect]
            [clojure.string :as str]
            [rum.core :as rum]))

(enable-console-print!)

(defonce digest-shown (atom false))

(defn resource-provided?
  [resource]
  (let [cpu (:cpu resource)
        memory (:memory resource)]
    (or (> cpu 0)
        (> memory 0))))

(rum/defc form-subheader < rum/reactive [image image-digest]
  (if image-digest
    (comp/click-away-listener
      {:onClickAway #(reset! digest-shown false)}
      (comp/tooltip
        {:PopperProps          {:disablePortal true}
         :onClose              #(reset! digest-shown false)
         :open                 (rum/react digest-shown)
         :disableFocusListener true
         :disableHoverListener true
         :disableTouchListener true
         :title                image-digest}
        (html [:span {:onClick #(reset! digest-shown true)
                      :style   {:cursor "pointer"}} image])))
    (html [:span image])))

(rum/defc form-replicas < rum/static [tasks]
  (let [data (->> tasks
                  (map (fn [task]
                         (if (= "running" (:state task))
                           {:name  (:taskName task)
                            :value 1
                            :color "#43a047"
                            :state (:state task)}
                           {:name  (:taskName task)
                            :value 1
                            :color "#6c757d"
                            :state (:state task)})))
                  (into []))]
    (chart/pie
      data
      (str (count tasks) " " (inflect/pluralize-noun (count tasks) "replica"))
      "Swarmpit-service-replicas-graph"
      "replicas-pie"
      {:formatter (fn [value name props]
                    (.-state (.-payload props)))})))

(defn- form-command [command]
  (when command
    (html [:pre {:key   "command"
                 :style {:fontSize "0.9em"
                         :margin   0}}
           (let [merged (str/join " " command)]
             (if (< 100 (count merged))
               (str/join "\n" command)
               merged))])))

(defn- form-state [state]
  (case state
    "running" (label/green state)
    "not running" (label/info state)
    "partly running" (label/yellow state)))

(rum/defc form-dashboard < rum/static [{:keys [limit reservation]} tasks]
  (html
    (cond
      (and (resource-provided? reservation)
           (resource-provided? limit))
      [:table
       [:tr
        [:td {:rowSpan 2} (form-replicas tasks)]
        [:td
         [:div "Reservation"]
         [:div
          [:span [:b "CPU: "]]
          [:span (:cpu reservation)]]
         [:div
          [:span [:b "MEMORY: "]]
          [:span (str (:memory reservation) "MB")]]]]
       [:tr
        [:td
         [:div "Limit"]
         [:div
          [:span [:b "CPU: "]]
          [:span (:cpu limit)]]
         [:div
          [:span [:b "MEMORY: "]]
          [:span (str (:memory limit) "MB")]]]]]
      (resource-provided? reservation)
      [:table
       [:tr
        [:td (form-replicas tasks)]
        [:td
         [:div "Reservation"]
         [:div
          [:span [:b "CPU: "]]
          [:span (:cpu reservation)]]
         [:div
          [:span [:b "MEMORY: "]]
          [:span (str (:memory reservation) "MB")]]]]]
      (resource-provided? limit)
      [:table
       [:tr
        [:td (form-replicas tasks)]
        [:td
         [:div "Limit"]
         [:div
          [:span [:b "CPU: "]]
          [:span (:cpu limit)]]
         [:div
          [:span [:b "MEMORY: "]]
          [:span (str (:memory limit) "MB")]]]]]
      :else (form-replicas tasks))))

(defn- autoredeploy-label
  [autoredeploy]
  (when autoredeploy (label/primary "autoredeploy")))

(rum/defc form < rum/static [service tasks actions]
  (let [image-digest (get-in service [:repository :imageDigest])
        image (get-in service [:repository :image])
        desired-tasks (filter #(not= "shutdown" (:desiredState %)) tasks)
        command (:command service)
        stack (:stack service)
        mode (:mode service)]
    (comp/card
      {:className "Swarmpit-form-card"}
      (comp/card-header
        {:title     (:serviceName service)
         :className "Swarmpit-form-card-header"
         :subheader (form-subheader image image-digest)
         :action    (menu/menu
                      actions
                      :serviceGeneralMenuAnchor
                      :serviceGeneralMenuOpened)})
      (comp/card-content
        {}
        (if (not (empty? desired-tasks))
          (form-replicas desired-tasks)
          (html
            [:span.Swarmpit-message
             (icon/info {:style {:marginRight "8px"}})
             [:span "Service has been shut down."]]))
        (form-command command))
      (comp/card-content
        {}
        (form/item-labels
          [(form-state (:state service))
           (autoredeploy-label (-> service :deployment :autoredeploy))
           (label/grey mode)]))
      (comp/card-actions
        {}
        (when stack
          (comp/button
            {:size  "small"
             :color "primary"
             :href  (routes/path-for-frontend :stack-info {:name stack})}
            "See stack"))
        (comp/button
          {:size  "small"
           :color "primary"
           :href  (routes/path-for-frontend :service-log {:id (:serviceName service)})}
          "View log"))
      (comp/divider
        {})
      (comp/card-content
        {:style {:paddingBottom "16px"}}
        (form/item-date (:createdAt service)
                        (:updatedAt service))
        (form/item-id (:id service))))))
