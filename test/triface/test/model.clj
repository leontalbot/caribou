(ns triface.test.model
  (:require [triface.db :as db]
            [triface.util :as util])
  (:use [triface.debug])
  (:use [triface.model])
  (:use [clojure.test]))

(deftest invoke-model-test
  (let [model (db/query "select * from model where id = 1")
        invoked (invoke-model (first model))]
    (is (= "name" (:name (:row (:name (invoked :fields))))))))

(deftest model-lifecycle-test
  (invoke-models)
  (let [model (create :model
         {:name "yellow"
          :description "yellowness yellow yellow"
          :position 3
          :fields [{:name "gogon" :type "string"}
                   {:name "wibib" :type "boolean"}]})
        yellow (create :yellow {:gogon "obobo" :wibib true})]

    (is (<= 8 (count (model :fields))))
    (is (= (model :name) "yellow"))
    (is ((models :yellow) :name "yellow"))
    (is (db/table? :yellow))
    (is (yellow :wibib))
    (is (= 1 (count (db/query "select * from yellow"))))
    
    (destroy :model (model :id))

    (is (not (db/table? :yellow)))
    (is (not (models :yellow)))))

(deftest model-interaction-test
  (invoke-models)
  (try
    (let [yellow-row (create :model
           {:name "yellow"
            :description "yellowness yellow yellow"
            :position 3
            :fields [{:name "gogon" :type "string"}
                     {:name "wibib" :type "boolean"}]})

          zap-row (create :model
            {:name "zap"
             :description "zap zappity zapzap"
             :position 3
             :fields [{:name "ibibib" :type "string"}
                      {:name "yobob" :type "slug" :link_slug "ibibib"}
                      {:name "yellows" :type "collection" :dependent true :target_id (yellow-row :id)}]})

          yellow (models :yellow)
          zap (models :zap)

          zzzap (create :zap {:ibibib "kkkkkkk"})
          yyy (create :yellow {:gogon "obobo" :wibib true :zap_id (zzzap :id)})
          yyyz (create :yellow {:gogon "igigi" :wibib false :zap_id (zzzap :id)})
          yy (create :yellow {:gogon "lalal" :wibib true :zap_id (zzzap :id)})]
      (update :yellow (yyy :id) {:gogon "binbin"})
      (update :zap (zzzap :id)
                      {:ibibib "OOOOOO mmmmm   ZZZZZZZZZZ"
                       :yellows [{:id (yyyz :id) :gogon "IIbbiiIIIbbibib"}
                                 {:gogon "nonononononon"}]})
      
      (let [zap-reload (db/choose :zap (zzzap :id))]
        (is (= ((db/choose :yellow (yyyz :id)) :gogon) "IIbbiiIIIbbibib"))
        (is (= ((db/choose :yellow (yyy :id)) :gogon) "binbin"))
        (is (= (zap-reload :yobob) "oooooo_mmmmm_zzzzzzzzzz"))
        (is (= "OOOOOO mmmmm   ZZZZZZZZZZ" ((from zap zap-reload {:include {}}) :ibibib)))
        (is (= 4 (count ((from zap zap-reload {:include {:yellows {}}}) :yellows))))

        (destroy :zap (zap-reload :id))

        (let [yellows (db/query "SELECT * FROM yellow")]
          (is (empty? yellows))))

      (destroy :model (zap :id))

      (is (empty? (-> models :yellow :fields :zap_id)))

      (destroy :model (yellow :id))

      (is (and (not (db/table? :yellow)) (not (db/table? :zap)))))

    (catch Exception e (util/render-exception e))
    (finally      
     (if (db/table? :yellow) (db/drop-table :yellow))
     (if (db/table? :zap) (db/drop-table :zap))
     )))



