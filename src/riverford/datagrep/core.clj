(ns riverford.datagrep.core
  "Like `grep` but on clojure data"
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str])
  (:import (java.util.regex Pattern Matcher)
           (clojure.lang IMeta Named)))

(def ^:dynamic *apply-to-meta*
  "Bind to true if you want grep to match on metadata as well as the values themselves."
  false)

(defn- build-pred
  [f]
  (if *apply-to-meta*
    (fn ! [x]
      (or (f x !)
          (do
            (and (instance? IMeta x)
                 (f (meta x) !)))))
    (fn ! [x]
      (f x !))))

(defn pred
  "Returns a predicate function from the given query, that will return true if the input is matched
  by the query.

  See `grep` docstring for query syntax."
  [query]
  (cond

    (fn? query)
    query

    (vector? query)
    (if (empty? query)
      (constantly true)
      (apply every-pred (map pred query)))

    (set? query)
    (if (empty? query)
      (constantly true)
      (apply some-fn (map pred query)))

    (map? query)
    (pred (vec (for [[k v] query
                     :let [kpred (pred k)
                           vpred (pred v)]]
                 (build-pred
                   (fn [x !]
                     (cond
                       (map? x) (some
                                  (fn [[k2 v2]]
                                    (or (and (kpred k2)
                                             (vpred v2))
                                        (! v2)))
                                  x)
                       (coll? x) (some ! x)
                       :else false))))))

    (number? query)
    (build-pred
      (fn [x !]
        (cond
          (map? x) (some (fn [[k v]] (or (! k) (! v))) x)
          (coll? x) (some ! x)
          (number? x) (= x query)
          :else (= (str query) (str/lower-case (str x))))))

    (string? query)
    (if (str/includes? query "*")
      (let [p (->> (str/split (str query) #"\*")
                   (remove str/blank?)
                   (map (fn [s] (Pattern/quote (str/lower-case s))))
                   (str/join ".*"))
            p' (if (str/starts-with? query "*")
                 (str ".*" p)
                 p)
            p' (if (str/ends-with? query "*")
                 (str p' ".*")
                 p')]
        (recur (Pattern/compile p')))
      (some-fn
        (build-pred
          (fn [x !]
            (cond
              (map? x) (some (fn [[k v]] (or (! k) (! v))) x)
              (coll? x) (some ! x)
              (instance? Named x) (or (! (str x))
                                      (! (name x)))
              (string? x) (= (str x) query)
              :else false)))
        (let [pattern (Pattern/compile (str "\\b" (Pattern/quote (str/lower-case query)) "\\b"))]
          (pred pattern))))

    (instance? Pattern query)
    (build-pred
      (fn [x !]
        (cond
          (map? x) (some (fn [[k v]] (or (! k) (! v))) x)
          (coll? x) (some ! x)
          (instance? Named x) (or (! (str x))
                                  (! (name x)))
          :else (let [s (str/lower-case (str x))]
                  (.find ^Matcher (re-matcher query s))))))

    (symbol? query)
    (recur (str query))

    (list? query)
    (condp = (first query)
      'not (complement (pred (set (rest query))))
      '= (let [y (second query)]
           (build-pred
             (fn [x !]
               (cond
                 (= y x) true
                 (map? x) (some (fn [[k v]] (or (! k) (! v))) x)
                 (coll? x) (some ! x)
                 :else false))))
      '== (let [y (second query)]
            (build-pred (fn [x !] (= x y))))

      '< (let [y (second query)
               ystr (when (string? y) (str/lower-case y))
               op (cond
                    (string? y) #(neg? (compare (str/lower-case %) ystr))
                    (number? y) (fn [x]
                                  (and (number? x) (< x y)))
                    :else (constantly false))]
           (build-pred
             (fn [x !]
               (op x))))

      '> (let [y (second query)
               ystr (when (string? y) (str/lower-case y))
               op (cond
                    (string? y) #(pos? (compare (str/lower-case %) ystr))
                    (number? y) (fn [x]
                                  (and (number? x) (> x y)))
                    :else (constantly false))]
           (build-pred
             (fn [x !]
               (op x))))

      '>= (let [y (second query)
                ystr (when (string? y) (str/lower-case y))
                op (cond
                     (string? y) #(<= 0 (compare (str/lower-case %) ystr))
                     (number? y) (fn [x]
                                   (and (number? x) (>= x y)))
                     :else (constantly false))]
            (build-pred
              (fn [x !]
                (op x))))

      '<= (let [y (second query)
                ystr (when (string? y) (str/lower-case y))
                op (cond
                     (string? y) #(>= 0 (compare (str/lower-case %) ystr))
                     (number? y) (fn [x]
                                   (and (number? x) (<= x y)))
                     :else (constantly false))]
            (build-pred
              (fn [x !]
                (op x))))

      (throw (IllegalArgumentException. "Not a valid call")))

    :else (recur (list '= query))))

(defn parse
  "Returns a query by reading the given string. Does not evaluate the query, rather returns it as a datastructure.
  See `grep` docstring syntax notes."
  [s]
  (binding [*read-eval* false]
    (read-string (str "[" s "]"))))

(defn grep
  "Filters the input according to a fuzzy query, each item is either considered a match or not, there is no scoring or ordering.
  The query term will be checked against every subcomponent of each item recursively, so if any part of an item matches - it will be a returned.

  It is intended to be used as an mechanism for humans to find subsets of data they are interested in,
  it is just about 'good enough' to find what you want without any indexing or thinking before hand.

  Because the search is fully exhaustive and recursive, it can be slow, and difficult to predict the matches, due to the fuzzy semantics of
  most of the terms.

  In the 1-ary form, returns a transducer that will filter the input according to the query.
  The 2-ary form, returns a seq containing only items in `coll` that are matched by the query.

  `query` is a datastructure composed of the following terms.
   - regular expressions (instances of Pattern or regex literals), like unix grep.
   - strings performs a fuzzy 'word' match, supports wildcards with *. If you want a more exact search, use
     The `=` term or a regex.
   - symbols, convenient shorthand for strings, only the name component is used.
   - numbers, compared with '='
   - vectors of terms expressing conjunction i.e 'and'
   - sets of terms expressing disjunction i.e 'or'
   - maps of key term to value term, where the input must contain a key matching the key term, and the value of that key or some subcomponent thereof
     must match the value term.
   - lists that transform the meaning of the term or do some more advanced comparison.
    For example (>= 5.3) would be valid list terms.
     - `not` inverts any clause, only returns the input if the term does cause a match.
     - `=` escapes any fuzzy rules and causes the clojure `=` to be used to determine a match, still recurses on input to find any match.
     - `==` root equality match, like `=` but will not recurse on the input to try and find a match.
     - `<` matches values that are less than the number. Or if a string is used, does alphanumeric comparison.
     - `<=` matches values that are less than or equal to the the number. Or if a string is used, does alphanumeric comparison.
     - `>` matches values that are greater than the number. Or if a string is used, does alphanumeric comparison.
     - `>=` matches values that are greater than or equal the number. Or if a string is used, does alphanumeric comparison.
   - arbitrary function predicates e.g even?

  example queries

  'foo matches items that contain the word foo, whether in a string, symbol, keyword collection or map
  'foo* matches items that begin with the word `foo` whether in a string, symbol keyword colleciton or map
  {'foo [even? '(< 20)]} matches items that contain a key containing foo, whether some component of the value is even and under 20.
   notice how in this example we are more specific with quoting to disambiguate symbols from functions."
  ([query]
   (filter (pred query)))
  ([query coll]
   (filter (pred query) coll)))

(defn pargrep
  "Like `grep` but runs the scan in parallel. Uses naive parallelism so expect only modest gains."
  [query coll]
  (let [pred (pred query)]
    (remove #(identical? ::not-found %) (pmap #(if (pred %) % ::not-found) coll))))

(defn vargrep
  "Runs the query against the vars in the namespaces (or all loaded vars), returns matching vars.
  Will include metadata in the search. e.g (vargrep '{doc transducer})"
  ([query]
   (vargrep query (cons
                    'clojure.core
                    (loaded-libs))))
  ([query namespaces]
   (binding [*apply-to-meta* true]
     (grep
       query
       (for [l namespaces
             [_ x] (ns-publics l)]
         x)))))
