(ns riverford.datagrep.core-test
  (:require [clojure.test :refer :all]
            [riverford.datagrep.core :refer :all]))

(deftest sanity-test
  (are
    [q ret]
    (= (grep q [1 2 3]) ret)

    some? [1 2 3]
    1 [1]
    2 [2]
    3 [3]
    even? [2]
    '(< 2) [1]
    "2" [2]
    #{1 "3"} [1 3])

  (are
    [q ret]
    (= (grep q ["fred" "bob" "ethel"]) ret)

    'fred ["fred"]
     'bo* ["bob"]
     #"(fr|)e.*" ["fred" "ethel"]
     '*thel ["ethel"])

  (let [fred {:name "fred"
              :friends #{"bob"}
              :age 22}

        bob {:name "bob"
             :friends #{"fred" "mary"}
             :age 60}

        mary {:name "mary"
              :friends #{"fred"}
              :age 82}]
    (are
      [q ret]
      (= (grep q [fred bob mary]) ret)

      "fred" [fred bob mary]

      {:name "fred"} [fred]
      {:name '*ob} [bob]
      {:age even?} [fred bob mary]
      {:age odd?} []

      {:friends "bob"} [fred]
      {:friends ["fred" "mary"]} [bob]
      {:friends #{"fred" "mary"}} [bob mary]
      {:friends '(= #{"fred" "mary"})} [bob])))
