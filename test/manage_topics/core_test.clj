(ns manage-topics.core-test
  (:require [clojure.test :refer :all]
            [manage-topics.core :refer :all]))

(defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
  `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
     ~@tests))

(with-private-fns [manage-topics.core [keywordize-keys]]

  (deftest test-keywordize
    (testing "can keywordize a string-keyed hashmap"
      (is (= {:wow 1 :ok.cool. 2}
             (keywordize-keys {"wow" 1 "ok.cool." 2})))))

  )
