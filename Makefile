.PHONY: repl run test

repl:
	rlwrap clojure -M:dev

run:
	clojure -M -m foo.core

test:
	clojure -X:test