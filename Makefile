.PHONY: repl run test perf perf-check

repl:
	rlwrap clojure -M:dev

run:
	clojure -M -m foo.core

test:
	clojure -X:test

# Three warmed HTTP rounds, compare with provisional API budgets.
perf:
	bash perf/run.sh

# Non-zero for failures; usable locally and as informational CI until calibrated.
perf-check:
	PERF_MODE=check bash perf/run.sh
