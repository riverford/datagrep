# datagrep

`grep` for data, for quick and dirty filtering at the repl.

`grep` is a filtering function, that returns elements of a sequence that match the provided query terms.

Terms are datastructures and are typically matched recursively, see [Query Syntax](#query-syntax) for available terms.

e.g

```clojure
(require '[riverford.datagrep.core :refer [grep]])
(grep '{:orders {:quantity (< 3)}} coll)
```

```clojure
(require '[riverford.datagrep.core :refer [vargrep]])
(vargrep '{:doc transducer, :ns #"clojure.core$"})
```

## Usage

Available via clojars:

```clojure
[riverford/datagrep "0.1.0"]
```

## Query Syntax

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

## TODO

- Tree grep?
- Additional query terms
- Scoring?!

Pull requests welcome!

## License

Copyright © 2017 Riverford Organic Farmers Ltd

Distributed under the [3-clause license ("New BSD License" or "Modified BSD License").](http://github.com/riverford/datagrep/blob/master/LICENSE)