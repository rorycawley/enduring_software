.PHONY: repl run test perf

repl:
	rlwrap clojure -M:dev

run:
	clojure -M -m foo.core

test:
	clojure -X:test

perf:
	bash perf/run.sh
