(ns orcpub.dnd.e5.subs
  (:require [re-frame.core :refer [reg-sub reg-sub-raw subscribe dispatch]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.registration :as registration]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.db :refer [tab-path]]
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.route-map :as routes]
            [clojure.string :as s]
            [reagent.ratom :as ra]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(reg-sub
 :db
 (fn [db _]
   db))

(reg-sub
 :registration-form
 (fn [db [_]]
   (get db :registration-form)))

(reg-sub
 :username-taken?
 (fn [db [_]]
   (get db :username-taken?)))

(reg-sub
 :email-taken?
 (fn [db [_]]
   (get db :email-taken?)))

(reg-sub
 :registration-validation
 :<- [:registration-form]
 :<- [:email-taken?]
 :<- [:username-taken?]
 (fn [args [_]]
   (apply registration/validate-registration args)))

(reg-sub
 :temp-email
 (fn [db [_]]
   (get db :temp-email)))

(reg-sub
 :locked
 (fn [db [_ path]]
   (get-in db [:locked-components path])))

(reg-sub
 :has-homebrew?
 :<- [:character]
 (fn [character _]
   (some
    (fn [[k v]]
      v)
    (get character ::entity/homebrew-paths))))

(reg-sub
 :homebrew?
 :<- [:character]
 (fn [character [_ path]]
   (get-in character
           [::entity/homebrew-paths path])))

(reg-sub
 :locked-components
 (fn [db []]
   (get db :locked-components)))

(reg-sub
 :loading
 (fn [db _]
   (get db :loading)))

(reg-sub
 :active-tabs
 (fn [db _]
   (get-in db tab-path)))

(reg-sub
 :character
 (fn [db _]
   (:character db)))

(reg-sub
 :entity-values
 :<- [:character]
 (fn [character _]
   (get-in character [::entity/values])))

(reg-sub
 :entity-value
 :<- [:entity-values]
 (fn [entity-values [_ kw]]
   (get entity-values kw)))

(reg-sub
 :entity-options
 :<- [:character]
 (fn [character _]
   (get-in character [::entity/options])))

(reg-sub
 ::char5e/ability-scores-option-value
 :<- [:entity-option :ability-scores]
 (fn [option _]
   (get option ::entity/value)))

(reg-sub
 ::char5e/ability-scores-option-key
 :<- [:entity-option :ability-scores]
 (fn [option _]
   (get option ::entity/key)))

(reg-sub
 :entity-option
 :<- [:entity-options]
 (fn [entity-options [_ kw]]
   (get entity-options kw)))

(reg-sub
 :custom-race-name
 :<- [:entity-options]
 (fn [options _]
   (get-in options [:race ::entity/value])))

(reg-sub
 :custom-subrace-name
 :<- [:entity-options]
 (fn [options _]
   (get-in options [:race
                    ::entity/options
                    :subrace
                    ::entity/value])))

(reg-sub
 :custom-subclass-name
 :<- [:character]
 :<- [:built-template]
 (fn [[character built-template] [_ path]]
   (get-in character
           (entity/get-option-value-path built-template
                                         character
                                         path))))

(reg-sub
 :custom-background-name
 :<- [:entity-options]
 (fn [options _]
   (get-in options [:background ::entity/value])))

(reg-sub
 :option-paths
 :<- [:character]
 (fn [character _]
   (entity/make-path-map character)))

(defn selected-plugin-options [character]
  (into #{}
        (comp (map ::entity/key)
              (remove nil?))
        (get-in character [::entity/options :optional-content])))

(reg-sub
 :selected-plugin-options
 :<- [:character]
 (fn [character _]
   (selected-plugin-options character)))

(reg-sub
 :available-selections
 :<- [:character]
 :<- [:built-character]
 :<- [:built-template]
 (fn [[character built-character built-template]]
   (entity/available-selections character built-character built-template)))

(reg-sub
 :template
 (fn [db _]
   (:template db)))

(reg-sub
 :plugins
 (fn [db _]
   (:plugins db)))

(reg-sub
 :page
 (fn [db _]
   (:page db)))

(reg-sub
 :route
 (fn [db _]
   (:route db)))

(reg-sub
 :previous-route
 (fn [db _]
   (-> db :route-history peek)))

(reg-sub
 :user-data
 (fn [db _]
   (:user-data db)))

(reg-sub
 :username
 (fn [db _]
   (-> db :user-data :user-data :username)))

(defn built-template [selected-plugin-options]
  (let [selected-plugins (map
                          :selections
                          (filter
                           (fn [{:keys [key]}]
                             (selected-plugin-options key))
                           t5e/plugins))]
    (if (seq selected-plugins)
      (update t5e/template
              ::t/selections
              (fn [s]
                (apply
                 entity/merge-multiple-selections
                 s
                 selected-plugins)))
      t5e/template)))

(reg-sub
 :built-template
 :<- [:selected-plugin-options]
 (fn [selected-plugin-options _]
   (built-template selected-plugin-options)))

(defn built-character [character built-template]
  (entity/build character built-template))

(reg-sub
 :built-character
 :<- [:character]
 :<- [:built-template]
 (fn [[character built-template] _]
   (built-character character built-template)))

(reg-sub
 :expanded-characters
 (fn [db _]
   (:expanded-characters db)))

(reg-sub
 :expanded-monsters
 (fn [db _]
   (:expanded-monsters db)))

(reg-sub
 :expanded-spells
 (fn [db _]
   (:expanded-spells db)))

(reg-sub-raw
  ::char5e/characters
  (fn [app-db [_]]
    (go (dispatch [:set-loading true])
        (let [response (<! (http/get (routes/path-for routes/dnd-e5-char-summary-list-route)
                                     {:accept :transit
                                      :headers {"Authorization" (str "Token " (-> @app-db :user-data :token))}}))]
          (dispatch [:set-loading false])
          (case (:status response)
            200 (dispatch [::char5e/set-characters (-> response :body)])
            401 (dispatch [:route routes/login-page-route {:secure? true}])
            500 (dispatch (events/show-generic-error)))))
    (ra/make-reaction
     (fn [] (get @app-db ::char5e/characters [])))))

(reg-sub-raw
  ::party5e/parties
  (fn [app-db [_]]
    (go (dispatch [:set-loading true])
        (let [response (<! (http/get (routes/path-for routes/dnd-e5-char-parties-route)
                                     {:accept :transit
                                      :headers {"Authorization" (str "Token " (-> @app-db :user-data :token))}}))]
          (dispatch [:set-loading false])
          (case (:status response)
            200 (dispatch [::party5e/set-parties (-> response :body)])
            401 (dispatch [:route routes/login-page-route {:secure? true}])
            500 (dispatch (events/show-generic-error)))))
    (ra/make-reaction
     (fn [] (get @app-db ::char5e/parties [])))))

(reg-sub-raw
  :user
  (fn [app-db [_ required?]]
    (go (let [response (<! (http/get (routes/path-for routes/user-route)
                                     {:accept :transit
                                      :headers {"Authorization" (str "Token " (-> @app-db :user-data :token))}}))]
          (case (:status response)
            200 (dispatch [:set-user (-> response :body)])
            401 (if required? (dispatch [:route routes/login-page-route {:secure? true}]))
            500 (if required? (dispatch (events/show-generic-error))))))
    (ra/make-reaction
     (fn [] (get @app-db :user [])))))

(reg-sub
 :following-users
 :<- [:user]
 (fn [user _]
   (into #{} (:following user))))

(reg-sub
 ::char5e/character-map
 (fn [db _]
   (::char5e/character-map db)))

(reg-sub
 ::char5e/summary-map
 (fn [db _]
   (::char5e/summary-map db)))

(reg-sub-raw
  ::char5e/character
  (fn [app-db [_ id :as args]]
    (if (some? id)
      (if (nil? (get-in @app-db [::char5e/character-map id]))
        (go (dispatch [:set-loading true])
            (let [response (<! (http/get (routes/path-for routes/dnd-e5-char-route :id id)
                                         {:accept :transit}))]
              (dispatch [:set-loading false])
              (case (:status response)
                200 (dispatch [::char5e/set-character id (-> response :body)])
                401 (dispatch [:route routes/login-page-route {:secure? true}])
                500 (dispatch (events/show-generic-error)))))))
    (ra/make-reaction
     (fn [] (get-in @app-db [::char5e/character-map id] [])))))

(reg-sub
 ::char5e/character-changed?
 (fn [[_ id]]
   [(subscribe [::char5e/internal-character id])
    (subscribe [:character])])
 (fn [[saved-character character] _]
   (and (:db/id saved-character)
        (not= character saved-character))))

(reg-sub
 ::char5e/has-selected?
 (fn [db _]
   (->> db ::char5e/selected seq)))

(reg-sub
 ::char5e/selected?
 (fn [db [_ id]]
   (get-in db [::char5e/selected id])))

(reg-sub
 ::char5e/selected
 (fn [db _]
   (get db ::char5e/selected)))

(reg-sub
 ::char5e/internal-character
 (fn [[_ id] _]
   (subscribe [::char5e/character id])) 
 (fn [character _ _]
   (char5e/from-strict character)))

(reg-sub
 ::char5e/selected-plugin-options
 (fn [[_ id] _]
   (subscribe [::char5e/internal-character id]))
 (fn [internal-character _ _]
   (selected-plugin-options internal-character)))

(reg-sub
 ::char5e/template
 (fn [db _]
   (:template db)))

(reg-sub
 ::char5e/built-template
 (fn [[_ id] _]
   (subscribe [::char5e/selected-plugin-options id]))
 (fn [selected-plugin-options _]
   (built-template selected-plugin-options)))

(reg-sub
 ::char5e/built-character
 (fn [[_ id] _]
   [(subscribe [::char5e/internal-character id])
    (subscribe [::char5e/built-template id])])
 (fn [[character built-template] _ _]
   (built-character character built-template)))

(reg-sub
 :message-shown?
 (fn [db _]
   (:message-shown? db)))

(reg-sub
 :login-message-shown?
 (fn [db _]
   (:login-message-shown? db)))

(reg-sub
 :message
 (fn [db _]
   (:message db)))

(reg-sub
 :confirmation-shown?
 (fn [db _]
   (:confirmation-shown? db)))

(reg-sub
 :confirmation-cfg
 (fn [db _]
   (:confirmation-cfg db)))

(reg-sub
 :login-message
 (fn [db _]
   (:login-message db)))

(reg-sub
 :message-type
 (fn [db _]
   (:message-type db)))

(reg-sub
 :device-type
 (fn [db _]
   (:device-type db)))

(reg-sub
 :mobile?
 :<- [:device-type]
 (fn [device-type _]
   (= :mobile device-type)))

(reg-sub
 :warning-hidden
 (fn [db _]
   (:warning-hidden db)))

(def character-subs
  {::char5e/base-swimming-speed char5e/base-swimming-speed
   ::char5e/base-flying-speed char5e/base-flying-speed
   ::char5e/base-land-speed char5e/base-land-speed
   ::char5e/speed-with-armor char5e/land-speed-with-armor
   ::char5e/unarmored-speed-bonus char5e/unarmored-speed-bonus
   ::char5e/max-hit-points char5e/max-hit-points
   ::char5e/hit-point-level-bonus char5e/hit-point-level-bonus
   ::char5e/initiative char5e/initiative 
   ::char5e/passive-perception char5e/passive-perception 
   ::char5e/character-name char5e/character-name 
   ::char5e/proficiency-bonus char5e/proficiency-bonus 
   ::char5e/save-bonuses char5e/save-bonuses 
   ::char5e/saving-throws char5e/saving-throws 
   ::char5e/race char5e/race 
   ::char5e/subrace char5e/subrace
   ::char5e/sex char5e/sex
   ::char5e/alignment char5e/alignment 
   ::char5e/background char5e/background 
   ::char5e/classes char5e/classes 
   ::char5e/levels char5e/levels 
   ::char5e/darkvision char5e/darkvision 
   ::char5e/skill-profs char5e/skill-proficiencies 
   ::char5e/skill-bonuses char5e/skill-bonuses
   ::char5e/skill-expertise char5e/skill-expertise
   ::char5e/tool-profs char5e/tool-proficiencies
   ::char5e/tool-expertise char5e/tool-expertise
   ::char5e/tool-bonus-fn char5e/tool-bonus-fn 
   ::char5e/weapon-profs char5e/weapon-proficiencies 
   ::char5e/armor-profs char5e/armor-proficiencies 
   ::char5e/resistances char5e/damage-resistances 
   ::char5e/damage-immunities char5e/damage-immunities 
   ::char5e/immunities char5e/immunities 
   ::char5e/condition-immunities char5e/condition-immunities 
   ::char5e/languages char5e/languages 
   ::char5e/abilities char5e/ability-values
   ::char5e/race-ability-increases char5e/race-ability-increases
   ::char5e/subrace-ability-increases char5e/subrace-ability-increases
   ::char5e/ability-increases char5e/ability-increases
   ::char5e/ability-bonuses char5e/ability-bonuses 
   ::char5e/armor-class char5e/base-armor-class 
   ::char5e/armor-class-with-armor char5e/armor-class-with-armor 
   ::char5e/armor char5e/normal-armor-inventory 
   ::char5e/magic-armor char5e/magic-armor-inventory 
   ::char5e/all-armor-inventory char5e/all-armor-inventory 
   ::char5e/spells-known char5e/spells-known
   ::char5e/spells-known-modes char5e/spells-known-modes
   ::char5e/spell-slots char5e/spell-slots 
   ::char5e/spell-modifiers char5e/spell-modifiers
   ::char5e/spell-slot-factors char5e/spell-slot-factors
   ::char5e/total-spellcaster-levels char5e/total-spellcaster-levels
   ::char5e/weapons char5e/normal-weapons-inventory 
   ::char5e/magic-weapons char5e/magic-weapons-inventory
   ::char5e/equipment char5e/normal-equipment-inventory
   ::char5e/magic-items char5e/magical-equipment-inventory
   ::char5e/traits char5e/traits
   ::char5e/attacks char5e/attacks
   ::char5e/bonus-actions char5e/bonus-actions
   ::char5e/reactions char5e/reactions
   ::char5e/actions char5e/actions
   ::char5e/image-url char5e/image-url
   ::char5e/image-url-failed char5e/image-url-failed
   ::char5e/faction-image-url char5e/faction-image-url
   ::char5e/faction-image-url-failed char5e/faction-image-url-failed
   ::char5e/personality-trait-1 char5e/personality-trait-1
   ::char5e/personality-trait-2 char5e/personality-trait-2
   ::char5e/xps char5e/xps
   ::char5e/ideals char5e/ideals
   ::char5e/bonds char5e/bonds
   ::char5e/flaws char5e/flaws
   ::char5e/description char5e/description
   ::char5e/critical-hit-values char5e/critical-hit-values
   ::char5e/number-of-attacks char5e/number-of-attacks
   ::char5e/has-weapon-prof char5e/has-weapon-prof
   ::char5e/weapon-attack-modifier-fn char5e/weapon-attack-modifier-fn
   ::char5e/total-levels char5e/total-levels
   ::char5e/class-level-fn char5e/class-level-fn
   ::char5e/option-sources char5e/option-sources
   ::char5e/used-resources char5e/used-resources
   ::char5e/al-illegal-reasons char5e/al-illegal-reasons
   ::char5e/feats char5e/feats})

(doseq [[sub-key char-fn] character-subs]
  (reg-sub
   sub-key
   (fn [[_ id override-built-char]]
     (if id
       (subscribe [::char5e/built-character id])
       (if override-built-char
         (atom override-built-char)
         (subscribe [:built-character]))))
   (fn [built-char _]
     (char-fn built-char))))

(reg-sub
 ::char5e/all-armor
 (fn [[_ id]]
   [(subscribe [::char5e/magic-armor id])
    (subscribe [::char5e/armor id])])
 (fn [[magic-armor armor] _]
   (merge magic-armor armor)))

(reg-sub
 ::char5e/all-weapons
 (fn [[_ id]]
   [(subscribe [::char5e/magic-weapons id])
    (subscribe [::char5e/weapons id])])
 (fn [[magic-weapons weapons] _]
   (merge magic-weapons weapons)))

(reg-sub
 :search-text
 (fn [db _]
   (:search-text db)))

(reg-sub
 :search-results
 (fn [db _]
   (:search-results db)))

(reg-sub
 :search-text?
 :<- [:search-text]
 (fn [search-text _]
   (not (s/blank? search-text))))

(reg-sub
 :orcacle-clicked?
 (fn [db _]
   (:orcacle-clicked? db)))

(reg-sub
 :orcacle-open?
 :<- [:search-text?]
 :<- [:orcacle-clicked?]
 (fn [[search-text? orcacle-clicked?]]
   (or search-text? orcacle-clicked?)))

(reg-sub
 ::char5e/selected-display-tab
 (fn [db _]
   (::char5e/selected-display-tab db)))

(reg-sub
 ::char5e/builder-tab
 (fn [db _]
   (::char5e/builder-tab db)))

(reg-sub
 ::char5e/filtered-monsters
 (fn [db _]
   (or (::char5e/filtered-monsters db)
       (sort-by :name monsters5e/monsters))))

(reg-sub
 ::char5e/filtered-spells
 (fn [db _]
   (or (::char5e/filtered-spells db)
       (sort-by :name spells5e/spells))))
