# enduring_software

A small Clojure HTTP application for practising **REPL-driven development**, **acceptance test-driven development (ATDD)**, **test-driven development (TDD)** and incremental delivery.

**[Read the ATDD / TDD development workflow](WORKFLOW.md)** for the complete process from agreed examples and failing acceptance tests through Red → Green → Refactor, GitHub Issues, pull requests, CI and Done. Each use case must meet behavioural **and human-perceived performance** acceptance criteria.

## Prerequisites

- Java 21
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- `make` and `rlwrap` (for the terminal REPL)
- Git and the [GitHub CLI](https://cli.github.com/) (for contributing changes)
- [Vegeta](https://github.com/tsenart/vegeta) to run HTTP performance checks (`brew install vegeta` on macOS)

The project uses Clojure 1.12.5, Ring/Jetty, Reitit, Stuart Sierra's Component and `clojure.test`.

## Quick start

Run from the repository root:

| Command | Purpose |
| --- | --- |
| `make repl` | Start the persistent development REPL |
| `make run` | Run the HTTP application in a fresh JVM |
| `make test` | Run all tests, including pure performance-budget evaluation, in a fresh JVM; also used by CI |
| `make perf` | Run three warmed HTTP benchmarks and report provisional performance-budget results |
| `make perf-check` | Run the same benchmarks; return nonzero for broken provisional HTTP budgets |

In the REPL, the `dev/user.clj` helpers are available:

```clojure
(go)      ; Reload code and (re)start the Component system
(check)   ; Reload source/tests and run the configured tests
(stop)    ; Stop the running system
```

With the application running on its default address, try:

```sh
curl -i http://127.0.0.1:3000/hello
curl -i 'http://127.0.0.1:3000/hello?name=Ada'
curl -i http://127.0.0.1:3000/health
```

| Endpoint | HTTP response |
| --- | --- |
| `GET /hello` | `200`, plain text `Welcome to Clojure, World!` |
| `GET /hello?name=Ada` | `200`, plain text `Welcome to Clojure, Ada!` |
| `GET /health` | `200`, plain text `OK` |

`/health` reports **application liveness**, not the health of external dependencies.

## HTTP performance acceptance (interim)

`make perf` or `make perf-check` starts a separate Jetty instance, warms it, and runs three independent measurements of `GET /hello?name=Ada` through real HTTP using Vegeta. Results are saved under ignored `perf/results/`. Provisional HTTP limits are configured in `perf/budgets.edn`. The extra GitHub Actions performance job records **informational** results; the normal `Test` check remains required.

These backend measurements **do not establish** human-perceived performance. Our product goals remain meaningful visible feedback within 100 ms and ordinary task completion within 1 second (both p95 under agreed conditions), with no unexplained or indefinite waiting. We will measure actual action-to-visible-outcome when the Angular UI exists. See [WORKFLOW.md](WORKFLOW.md) for budgets, comparison rules and acceptance evidence.

## How the application fits together

```text
HTTP client
    ↓
Jetty (foo.server, managed by Component)
    ↓
Ring + Reitit (foo.http)
    ↓
Application behaviour (foo.greeting)
```

`foo.system/new-system` assembles the Component system; `foo.core/-main` starts the application for standalone operation. HTTP handlers are ordinary Ring functions that can be tested without starting a server. A separate integration test exercises real HTTP and the Jetty lifecycle.

## Project structure

```text
enduring_software/
├── .github/workflows/ci.yml    # GitHub Actions
├── dev/user.clj                # Development REPL helpers
├── src/foo/                    # HTTP application
│   └── perf/                   # Pure budget evaluator and CLI
├── test/foo/                   # Pure, HTTP and acceptance tests
│   └── perf_evaluate_test.clj  # Deterministic performance-budget tests
├── perf/
│   ├── run.sh                  # Isolated three-round HTTP benchmark
│   ├── start_server.clj        # Temporary Component-managed Jetty
│   └── budgets.edn             # Provisional API-only guardrails
├── deps.edn
├── Makefile
├── README.md
└── WORKFLOW.md                 # ATDD, TDD and performance acceptance workflow
```

## Development and delivery

We use protected `main`, short-lived feature branches, [GitHub Project #5](https://github.com/users/rorycawley/projects/5), Issues with acceptance criteria, and GitHub Actions to verify tests before merging.

**See [WORKFLOW.md](WORKFLOW.md)** for how to define outcomes, agree on executable acceptance examples, work through the TDD inner loop, use performance budgets, run `gh` and finish an Issue.
