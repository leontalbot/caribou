(ns triface.model
  (:use triface.debug)
  (:use triface.util)
  (:use [clojure.contrib.str-utils])
  (:require [triface.db :as db]))

(defprotocol Field
  "a protocol for expected behavior of all model fields"
  (table-additions [this field] "the set of additions to this db table based on the given name")
  (subfield-names [this field] "the names of any additional fields added to the model by this field given this name")
  (setup-field [this] "further processing on creation of field")
  (cleanup-field [this] "further processing on creation of field")
  (target-for [this] "retrieves the model this field points to, if applicable")
  (update-values [this content values] "adds to the map of values that will be committed to the db for this row")
  (post-update [this content] "any processing that is required after the content is created/updated")
  (pre-destroy [this content] "prepare this content item for destruction")
  (field-from [this content opts] "retrieves the value for this field from this content item")
  (render [this content opts] "renders out a single field from this content item"))

(defrecord IdField [row env]
  Field
  (table-additions [this field] [[(keyword field) "SERIAL" "PRIMARY KEY"]])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values] values)
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (field-from this content opts)))
  
(defrecord IntegerField [row env]
  Field
  (table-additions [this field]
    (let [default (or (env :default) "NULL")]
      [[(keyword field) :integer (str "DEFAULT " default)]]))
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)

  (update-values [this content values]
    (let [key (keyword (row :slug))]
      (if (contains? content key)
        (try
          (let [value (content key)
                tval (if (isa? (type value) String)
                       (Integer/parseInt value)
                       value)]
            (assoc values key tval))
          (catch Exception e values))
        values)))

  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (field-from this content opts)))
  
(defrecord StringField [row env]
  Field
  (table-additions [this field] [[(keyword field) "varchar(256)"]])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values]
    (let [key (keyword (row :slug))]
      (if (contains? content key)
        (assoc values key (content key))
        values)))
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (field-from this content opts)))

(defrecord SlugField [row env]
  Field
  (table-additions [this field] [[(keyword field) "varchar(256)"]])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values]
    (let [key (keyword (row :slug))]
      (cond
       (env :link) 
         (let [icon (content (keyword (-> env :link :slug)))]
           (if icon
             (assoc values key (slugify icon))
             values))
       (contains? content key) (assoc values key (slugify (content key)))
       :else values)))
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (field-from this content opts)))

(defrecord TextField [row env]
  Field
  (table-additions [this field] [[(keyword field) :text]])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values]
    (let [key (keyword (row :slug))]
      (if (contains? content key)
        (assoc values key (content key))
        values)))
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (field-from this content opts)))

(defrecord BooleanField [row env]
  Field
  (table-additions [this field] [[(keyword field) :boolean]])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values]
    (let [key (keyword (row :slug))]
      (if (contains? content key)
        (try
          (let [value (content key)
                tval (if (isa? (type value) String)
                       (Boolean/parseBoolean value)
                       value)]
            (assoc values key tval))
          (catch Exception e values))
        values)))
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (field-from this content opts)))

(defrecord TimestampField [row env]
  Field
  (table-additions [this field] [[(keyword field) "timestamp with time zone" "NOT NULL" "DEFAULT current_timestamp"]])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values]
    (let [key (keyword (row :slug))]
      (cond
       (= key :updated_at) (assoc values key :current_timestamp)
       (contains? content key) (assoc values key (content key))
       :else values)))
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts] (content (keyword (row :slug))))
  (render [this content opts] (str (field-from this content opts))))

(defrecord ImageField [row env]
  Field
  (table-additions [this field] [])
  (subfield-names [this field] [(str field "_id")])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values])
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts])
  (render [this content opts] ""))

;; forward reference for CollectionField
(def make-field)
(def model-render)
(def invoke-model)
(def create)
(def update)
(def destroy)
(def models (ref {}))

(defn from [model content opts]
  (reduce #(assoc %1 (keyword (-> %2 :row :name)) (field-from %2 %1 opts)) content (vals (model :fields))))

(defrecord CollectionField [row env]
  Field
  (table-additions [this field] [])
  (subfield-names [this field] [])

  (setup-field [this]
    (if (or (nil? (row :link_id)) (zero? (row :link_id)))
      (let [model (models (row :model_id))
            target (models (row :target_id))
            part (create :field
                   {:name (model :name)
                    :type "part"
                    :model_id (row :target_id)
                    :target_id (row :model_id)
                    :link_id (row :id)
                    :dependent (row :dependent)
                    :_parent target})]
        (db/update :field {:link_id (-> part :row :id)} "id = %1" (row :id)))))

  (cleanup-field [this]
    (try
      (do (destroy :field (-> env :link :id)))
      (catch Exception e (str e))))

  (target-for [this] (models (row :target_id)))

  (update-values [this content values] values)

  (post-update [this content]
    (let [collection (content (keyword (row :slug)))]
      (if collection
        (let [part (env :link)
              part-key (keyword (str (part :slug) "_id"))
              model (models (part :model_id))
              updated (doall
                       (map
                        #(create
                          (model :slug)
                          (merge % {part-key (content :id)
                                    :_parent content}))
                        collection))]
          (assoc content (keyword (row :slug)) updated))
        content)))

  (pre-destroy [this content]
    (if (or (row :dependent) (-> env :link :dependent))
      (let [parts (field-from this content {:include {(keyword (row :slug)) {}}})
            target (keyword ((target-for this) :slug))]
        (doall (map #(destroy target (% :id)) parts))))
    content)

  (field-from [this content opts]
    (let [include (if (opts :include) ((opts :include) (keyword (row :slug))))]
      (if include
        (let [down (assoc opts :include include)
              parts (db/fetch (-> (target-for this) :slug) (str (-> this :env :link :slug) "_id = %1") (content :id))]
          (map #(from (target-for this) % down) parts))
        [])))

  (render [this content opts]
    (map #(model-render (target-for this) % (assoc opts :include ((opts :include) (keyword (row :slug))))) (field-from this content opts))))

(defrecord PartField [row env]
  Field

  (table-additions [this field] [])
  (subfield-names [this field] [(str field "_id") (str field "_position")])

  (setup-field [this]
    (let [model_id (row :model_id)
          model (models model_id)
          target (models (row :target_id))]
      (if (or (nil? (row :link_id)) (zero? (row :link_id)))
        (let [collection (create :field
                           {:name (:name model)
                            :type "collection"
                            :model_id (row :target_id)
                            :target_id model_id
                            :link_id (row :id)
                            :_parent target})]
          (db/update :field {:link_id (-> collection :row :id)} "id = %1" (row :id))))

      (update :model model_id
        {:fields
         [{:name (str (row :slug) "_id")
           :type "integer"
           :editable false}
          {:name (str (row :slug) "_position")
           :type "integer"
           :editable false}]})))

  (cleanup-field [this]
    (let [fields ((models (row :model_id)) :fields)
          id (keyword (str (row :slug) "_id"))
          position (keyword (str (row :slug) "_position"))]
      (destroy :field (-> fields id :row :id))
      (destroy :field (-> fields position :row :id))
      (try
        (do (destroy :field (-> env :link :id)))
        (catch Exception e (str e)))))

  (target-for [this] (models (-> this :row :target_id)))

  (update-values [this content values] values)

  (post-update [this content] content)

  (pre-destroy [this content] content)

  (field-from [this content opts]
    (let [include (if (opts :include) ((opts :include) (keyword (row :slug))))]
      (if include
        (let [down (assoc opts :include include)
              collector (db/choose (-> (target-for this) :slug) (content (keyword (str (row :slug) "_id"))))]
          (from (target-for this) collector down)))))

  (render [this content opts]
    (let [field (field-from this content opts)]
      (if field
        (model-render (target-for this) field (assoc opts :include ((opts :include) (keyword (row :slug)))))))))

(defrecord LinkField [row env]
  Field
  (table-additions [this field] [])
  (subfield-names [this field] [])
  (setup-field [this] nil)
  (cleanup-field [this] nil)
  (target-for [this] nil)
  (update-values [this content values])
  (post-update [this content] content)
  (pre-destroy [this content] content)
  (field-from [this content opts])
  (render [this content opts] ""))

(def field-constructors
  {:id (fn [row] (IdField. row {}))
   :integer (fn [row] (IntegerField. row {}))
   :string (fn [row] (StringField. row {}))
   :slug (fn [row] 
           (let [link (db/choose :field (row :link_id))]
             (SlugField. (assoc row :link_id (link :id)) {:link link})))
   :text (fn [row] (TextField. row {}))
   :boolean (fn [row] (BooleanField. row {}))
   :timestamp (fn [row] (TimestampField. row {}))
   :image (fn [row] (ImageField. row {}))
   :collection (fn [row]
                 (let [link (if (row :link_id) (db/choose :field (row :link_id)))]
                   (CollectionField. row {:link link})))
   :part (fn [row]
           (let [link (db/choose :field (row :link_id))]
             (PartField. row {:link link})))
   :link (fn [row] (LinkField. row {}))
   })

;; (def base-fields [[:id "SERIAL" "PRIMARY KEY"]
;;                   [:position :integer "DEFAULT 1"]
;;                   [:status :integer "DEFAULT 1"]
;;                   [:locale_id :integer "DEFAULT 1"]
;;                   [:env_id :integer "DEFAULT 1"]
;;                   [:locked :boolean "DEFAULT false"]
;;                   [:created_at "timestamp with time zone" "NOT NULL" "DEFAULT current_timestamp"]
;;                   [:updated_at "timestamp with time zone" "NOT NULL" "DEFAULT current_timestamp"]])

(def base-fields [{:name "id" :type "id" :locked true :immutable true :editable false}
                  {:name "position" :type "integer" :locked true}
                  {:name "status" :type "integer" :locked true}
                  {:name "locale_id" :type "integer" :locked true :editable false}
                  {:name "env_id" :type "integer" :locked true :editable false}
                  {:name "locked" :type "boolean" :locked true :immutable true :editable false}
                  {:name "created_at" :type "timestamp" :locked true :immutable true :editable false}
                  {:name "updated_at" :type "timestamp" :locked true :editable false}])

(defn make-field [row]
  ((field-constructors (keyword (row :type))) row))

(defn fields-render [fields content opts]
  (reduce #(assoc %1 (keyword (-> %2 :row :name))
             (render %2 content opts))
          content fields))

(defn model-render [model content opts]
  (fields-render (vals (model :fields)) content opts))

(def lifecycle-hooks (ref {}))

(defn make-lifecycle-hooks [slug]
  (let [hooks {(keyword slug)
               {:before_create  (ref {})
                :after_create   (ref {})
                :before_update  (ref {})
                :after_update   (ref {})
                :before_save    (ref {})
                :after_save     (ref {})
                :before_destroy (ref {})
                :after_destroy  (ref {})}}]
    (dosync
     (alter lifecycle-hooks merge hooks))))

(defn run-hook [slug timing env]
  (let [kind (lifecycle-hooks (keyword slug))]
    (if kind
      (let [hook (kind (keyword timing))]
        (reduce #((hook %2) %1) env (keys @hook))))))

(defn add-hook [slug timing id hook]
  (dosync
   (alter ((lifecycle-hooks (keyword slug)) (keyword timing))
          merge {id hook})))

(defn invoke-model [model]
  (let [fields (db/query "select * from field where model_id = %1" (model :id))
        field-map (seq-to-map #(keyword (-> % :row :name)) (map make-field fields))]
    (make-lifecycle-hooks (model :slug))
    (assoc model :fields field-map)))

(defn alter-models [model]
  (dosync
   (alter models merge {(model :slug) model (model :id) model})))

(defn create-model-table [name]
  (db/create-table (keyword name) []))

(def invoke-models)

(defn add-model-hooks []
  (add-hook :model :before_create :build_table (fn [env]
    (create-model-table (-> env :spec :name))
    env))
  
  (add-hook :model :before_create :add_base_fields (fn [env]
    (assoc-in env [:spec :fields] (concat (-> env :spec :fields) base-fields))))

  (add-hook :model :after_create :invoke (fn [env]
    (if (-> env :content :nested)
      (create :field {:name "parent_id" :model_id (-> env :content :id) :type "integer" :_parent (-> env :content)}))
    (alter-models (-> env :content))
    env))
  
  (add-hook :model :after_update :rename (fn [env]
    (let [original (-> env :original :slug)
          slug (-> env :content :slug)]
      (if (not (= original slug))
        (db/rename-table original slug)))
    (alter-models (invoke-model (-> env :content)))
    env))

  (add-hook :model :after_save :invoke_all (fn [env]
    (invoke-models)
    env))

  (add-hook :model :after_destroy :cleanup (fn [env]
    (db/drop-table (-> env :content :slug))
    (invoke-models)
    env)))
  
(defn add-field-hooks []
  (add-hook :field :before_save :check_link_slug (fn [env]
    (assoc env :values 
      (if (-> env :spec :link_slug)
        (let [model_id (-> env :spec :model_id)
              link_slug (-> env :spec :link_slug)
              fetch (db/fetch :field "model_id = %1 and slug = '%2'" model_id link_slug)
              linked (first fetch)]
          (assoc (env :values) :link_id (linked :id)))
        (env :values)))))
  
  (add-hook :field :after_create :add_columns (fn [env]
    (let [field (make-field (env :content))
          slug (-> env :spec :_parent :slug)]
          ;; slug (if (-> env :spec :_parent)
          ;;        (-> env :spec :_parent :slug)
          ;;        ((models (-> env :spec :model_id)) :slug))]
      (doall (map #(db/add-column slug (name (first %)) (rest %)) (table-additions field (-> env :content :slug))))
      (setup-field field)
      (assoc env :content field))))
  
  (add-hook :field :after_update :reify_field (fn [env]
    (let [field (make-field (env :content))
          original (-> env :original :slug)
          slug (-> env :content :slug)
          model (models (-> field :row :model_id))
          spawn (apply zipmap (map #(subfield-names field %) [original slug]))
          transition (apply zipmap (map #(map first (table-additions field %)) [original slug]))]
      (if (not (= original slug))
        (do (doall (map #(update :field (-> ((model :fields) (keyword (first %))) :row :id) {:name (last %)}) spawn))
            (doall (map #(db/rename-column (model :slug) (first %) (last %)) transition)))))
    (assoc env :content (make-field (env :content)))))

  (add-hook :field :after_destroy :drop_columns (fn [env]
    (let [model (models (-> env :content :model_id))
          field ((model :fields) (keyword (-> env :content :slug)))]
      (do (cleanup-field field))
      (doall (map #(db/drop-column ((models (-> field :row :model_id)) :slug) (first %)) (table-additions field (-> env :content :slug))))
      env))))

(defn invoke-models []
  (let [rows (db/query "select * from model")
        invoked (doall (map invoke-model rows))]
     (add-model-hooks)
     (add-field-hooks)
     (dosync
      (alter models 
        (fn [in-ref new-models] new-models)
        (merge (seq-to-map #(keyword (% :slug)) invoked)
               (seq-to-map #(% :id) invoked))))))

(defn create [slug spec]
  (if (spec :id)
    (update slug (spec :id) spec)
    (let [model (models (keyword slug))
          values (reduce #(update-values %2 spec %1) {} (vals (dissoc (model :fields) :updated_at)))
          env {:model model :values values :spec spec}
          _save (run-hook slug :before_save env)
          _create (run-hook slug :before_create _save)
          content (db/insert slug (dissoc (_create :values) :updated_at))
          merged (merge (_create :spec) content)
          _after (run-hook slug :after_create (merge _create {:content merged}))
          post (reduce #(post-update %2 %1) (_after :content) (vals (model :fields)))
          _final (run-hook slug :after_save (merge _after {:content post}))]
      (_final :content))))

(defn update [slug id spec]
  (let [model (models (keyword slug))
        original (db/choose slug id)
        values (reduce #(update-values %2 spec %1) {} (vals (model :fields)))
        env {:model model :values values :spec spec :original original}
        _save (run-hook slug :before_save env)
        _update (run-hook slug :before_update _save)
        success (db/update slug (_update :values) "id = %1" id)
        content (db/choose slug id)
        merged (merge (_update :spec) content)
        _after (run-hook slug :after_update (merge _update {:content merged}))
        post (reduce #(post-update %2 %1) (_after :content) (vals (model :fields)))
        _final (run-hook slug :after_save (merge _after {:content post}))]
    (_final :content)))

(defn destroy [slug id]
  (let [model (models (keyword slug))
        content (db/choose slug id)
        env {:model model :content content :slug slug}
        _before (run-hook slug :before_destroy env)
        pre (reduce #(pre-destroy %2 %1) (_before :content) (vals (model :fields)))
        deleted (db/delete slug "id = %1" id)
        _after (run-hook slug :after_destroy (merge _before {:content pre}))]
    (_after :content)))

(defn rally
  "pull a set of content up through the model system with the given options"
  ([slug] (rally slug {}))
  ([slug opts]
     (let [model (models (keyword slug))
           order (or (opts :order) "asc")
           order-by (or (opts :order_by) "position")
           limit (str (or (opts :limit) 30))
           offset (str (or (opts :offset) 0))]
       (doall (map #(from model % opts) (db/query "select * from %1 order by %2 %3 limit %4 offset %5" slug order-by order limit offset))))))

(defn table-columns
  "return a list of all columns for the table corresponding to this model"
  [slug]
  (let [model (models (keyword slug))]
    (apply concat (map (fn [field] (map #(name (first %)) (table-additions field (-> field :row :slug)))) (vals (model :fields))))))

(defn progenitors
  "if the model is nested, return a list of it along with all of its ancestors"
  ([slug id] (progenitors slug id {}))
  ([slug id opts]
     (let [model (models (keyword slug))]
       (if (model :nested)
         (let [field-names (table-columns slug)
               base-where (db/clause "id = %1" [id])
               recur-where (db/clause "%1_tree.parent_id = %1.id" [slug])
               before (db/recursive-query slug field-names base-where recur-where)]
           (doall (map #(from model % opts) before)))
         [(from model (db/choose slug id) opts)]))))

(defn descendents
  "pull up all the descendents of the given nested model"
  ([slug id] (descendents slug id {}))
  ([slug id opts]
     (let [model (models (keyword slug))]
       (if (model :nested)
         (let [field-names (table-columns slug)
               base-where (db/clause "id = %1" [id])
               recur-where (db/clause "%1_tree.id = %1.parent_id" [slug])
               before (db/recursive-query slug field-names base-where recur-where)]
           (doall (map #(from model % opts) before)))
         [(from model (db/choose slug id) opts)]))))

(defn init []
  (invoke-models)
  (log :model "models-invoked"))


