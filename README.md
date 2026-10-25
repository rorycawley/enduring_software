# enduring_software

A small Clojure HTTP application for practising **REPL-driven development**, **acceptance test-driven development (ATDD)**, **test-driven development (TDD)** and incremental delivery.

**[Read the ATDD / TDD development workflow](WORKFLOW.md)** for the complete process from agreed examples and failing acceptance tests through Red → Green → Refactor, GitHub Issues, pull requests, CI and Done.

## Prerequisites

- Java 21
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- `make` and `rlwrap` (for the terminal REPL)
- Git and the [GitHub CLI](https://cli.github.com/) (for contributing changes)

The project uses Clojure 1.12.5, Ring/Jetty, Reitit, Stuart Sierra's Component and `clojure.test`.

## Quick start

Run from the repository root:

| Command | Purpose |
| --- | --- |
| `make repl` | Start the persistent development REPL |
| `make run` | Run the HTTP application in a fresh JVM |
| `make test` | Run all tests in a fresh JVM; also used by CI |

In the REPL, the `dev/user.clj` helpers are available:

```clojure
(go)      ; Reload code and (re)start the Component system
(check)   ; Reload source/tests and run the configured tests
(stop)    ; Stop the running system
```

With the application running on its default address, try:

```sh
curl -i http://127.0.0.1:3000/hello
curl -i http://127.0.0.1:3000/health
```

| Endpoint | HTTP response |
| --- | --- |
| `GET /hello` | `200`, plain text `Welcome to Clojure, World!` |
| `GET /health` | `200`, plain text `OK` |

`/health` reports **application liveness**, not the health of external dependencies.

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
├── src/foo/
│   ├── core.clj                # Standalone entry point
│   ├── greeting.clj            # Greeting behaviour
│   ├── http.clj                # Ring handlers and routes
│   ├── server.clj              # Component-managed Jetty
│   └── system.clj              # Component system
├── test/foo/
│   ├── greeting_test.clj       # Pure behaviour tests
│   ├── http_test.clj           # Fast HTTP handler tests
│   └── system_test.clj         # Component/real HTTP tests
├── deps.edn
├── Makefile
├── README.md
└── WORKFLOW.md                 # ATDD, TDD and GitHub delivery workflow
```

## Development and delivery

We use protected `main`, short-lived feature branches, [GitHub Project #5](https://github.com/users/rorycawley/projects/5), Issues with acceptance criteria, and GitHub Actions to verify changes before merging.

**See [WORKFLOW.md](WORKFLOW.md)** for how to define outcomes, agree on executable acceptance examples, work through the TDD inner loop, run tests in the REPL, use `gh` and finish an Issue.

Completed reference: [Issue #4 — GET /health](https://github.com/rorycawley/enduring_software/issues/4) and [PR #5](https://github.com/rorycawley/enduring_software/pull/5).
