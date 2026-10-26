# ATDD / TDD development workflow

This is the repository's **single guide for how we develop and deliver a change**. The [README](README.md) covers installation, running the application and its structure; this document owns the detailed engineering workflow.

Our goal is to make a small, verifiable change from an agreed user outcome to working software on protected `main`, retaining a traceable link between the requirement and its tests.

**Permanent acceptance rule: a use case is not accepted until it is both behaviourally correct and sufficiently responsive from the user's perspective.** Every use case needs explicit behavioural *and* performance acceptance criteria, with evidence for each. Performance is part of ATDD, not a separate optional exercise.

## One workflow, two feedback loops

```text
GitHub Issue: outcome + behaviour + perceived-performance criteria
                     │
                     ▼
     Agree examples → (accept) RED
                     │
                     ▼
        ┌─────────────────────────┐
        │ REPL: explore and test  │
        │ TDD: RED → GREEN        │
        │      → REFACTOR         │
        │ (test-one 'ns/test)      │
        └─────────────────────────┘
                     │
                     ▼
         Behavioural ATDD: (accept) GREEN
                     │
                     ▼
      Performance acceptance: measured
        user-visible response + outcome
                     │
                     ▼
      (check) → make test → PR → CI → merge
                     │
                     ▼
        Issue CLOSED → Project Done
```

| ATDD (outer loop) | TDD (inner loop) |
| --- | --- |
| Agree on **what behaviour is valuable** and what examples demonstrate it | Design and implement **how that behaviour works**, one small step at a time |
| Driven by stakeholder outcomes and public, observable behaviour | Driven by tests of small functions, domain rules, handlers and collaborators |
| Acceptance checks should remain meaningful if the internals change | Implementation-level tests can evolve as internals are refactored |
| Ends only when agreed behavioural **and perceived-performance** examples pass through relevant public boundaries | Repeats Red → Green → Refactor until the behavioural acceptance example can pass |

**ATDD is more than running an end-to-end test.** Its distinctive step is agreeing on specific examples *before* implementation, considering user, development and testing perspectives together. We can play these roles ourselves in this learning repository, but must still surface ambiguity before coding.

**Acceptance criteria are not automatically acceptance tests.** Express the outcome and rules in language meaningful to the people using the system. Agree on examples that clarify those rules, then implement executable tests against the appropriate public interface. Not every sentence needs its own test, and we should not force HTTP or implementation details into a business requirement.

### Separate the requirement from its delivery mechanism

Keep these four things connected, but distinguish their roles:

| Layer | What it answers | Example |
| --- | --- | --- |
| **Outcome** | Why does this behaviour matter? | Someone can request a greeting addressed to them. |
| **Business-facing acceptance criterion** | What must remain true regardless of the implementation? | When no name is supplied, the greeting addresses World. |
| **Concrete example** | Which particular situation establishes what the rule means? | No name supplied → `Welcome to Clojure, World!` |
| **Executable acceptance test** | How do we verify that example through the system we actually deliver? | Send `GET /hello` and check the response. |

**Write the first three in the language of the domain, not in the language of HTTP, Clojure or the database.** A rule should not cease to make sense because we replace Ring with another framework or replace the application entirely.

A published HTTP API may *also* have requirements of its own: route names, status codes, content types and payload formats are important to existing API clients. Record these as **public interface contract requirements**, linked to the business examples, rather than disguising a query-string detail as a business concept.

The test code is where the agreed example is mapped onto that interface. Keep a traceable connection from the Issue to its examples, the tests and the change that satisfies them.

## Human-perceived performance is part of acceptance

> **A use case is not accepted until it is both behaviourally correct and sufficiently responsive from the user's perspective.**

Our quality goal is not merely a fast server. A person using the product should never think *"I am waiting; is this stuck; will it ever finish?"* Specify performance in **human experience terms**, alongside the business outcome, before selecting an implementation. Both requirements must survive a complete replacement of the application.

### Default user-experience budgets

| Observable experience | Initial acceptance target |
| --- | --- |
| **Acknowledgement / feedback** | Meaningful, truthful feedback is visible within **100 ms at p95** after the user's action. |
| **Ordinary interactive task completion** | The intended outcome is visible within **1 second at p95**, measured end-to-end. |
| **Long-running tasks** | Meaningful feedback still appears promptly; the person sees an honest, understandable pending state, can revisit its status, and receives a visible outcome or actionable failure. |
| **Uncertain or indefinite waiting** | Not acceptable: no endless spinner, silent wait, or false success indication. Explicitly handle delay, timeout, loss of connectivity, and unknown outcomes. |

These are **initial product targets**, not proven existing performance. p95 means 95% of measured interactions meet the budget **under agreed, representative conditions**; it is not a promise about every possible device/network. Record p99 and exceptional long waits as diagnostic evidence so a good p95 cannot conceal an unacceptable experience. Tighten or revise targets based on actual users and evidence, never merely to make a failing build green.

A task should be classified **before implementation** as *ordinary interactive* or *legitimately long-running*. Do not classify a slow implementation as long-running after the fact to avoid the 1-second goal. Some tasks (payments, background processing, external approvals) are genuinely asynchronous; a fast acknowledgement is **not** proof the work has finished. Distinguish *Sending*, *Accepted/Pending*, *Completed*, *Failed*, and *Status unknown* honestly, and provide a way to recover or check. Set task-appropriate timeout/status-update expectations rather than allowing endless ambiguity.

### Specify it for every use case

For each use case or task-based UI interaction, record **alongside its behavioural acceptance**:

1. **Human action and visible result** — where the timer starts (e.g. pressing *Draw names*) and exactly what counts as first meaningful feedback and completed user-visible outcome. An on-screen confirmation is different from an HTTP `200` or a queued message.
2. **Performance acceptance** — the 100 ms feedback budget and 1-second ordinary-completion budget, or a justified long-running exception with explicit visible states, recovery and completion/failure handling. Specify any more suitable use-case-specific thresholds here.
3. **Representative measurement conditions** — reference device/browser, network, data size, expected concurrent usage, warm-up and environment. Avoid passing only on a developer's unusually fast computer or failing on noisy infrastructure.
4. **Executable evidence** — the scenario/test identifier, measured feedback and completion latency distributions (at least p95; observe p99), errors, environment and baseline. Keep the evidence traceable to the use case and PR.

Examples showing why **ordinary** and **long-running** use cases should be distinct decisions:

```gherkin
Scenario: Organiser starts the Secret Santa draw (ordinary task)
  Given an exchange is ready for drawing
  When the organiser starts the draw
  Then meaningful feedback is visible within 100 ms
  And the confirmed draw result is visible within 1 second
  And the assignments respect the agreed drawing rules

Scenario: Payment is awaiting an external provider (long-running task)
  Given an organiser is ready to pay for the exchange
  When they submit the payment
  Then the interface acknowledges the action within 100 ms
  And it shows whether payment is sending, pending, confirmed or failed
  And a pending payment has a way to check its status later
  And a delay or unknown outcome never appears as confirmed payment
```

The examples define the experience; percentile thresholds are evaluated across repeated runs in the agreed environment, **not** inferred from a single Gherkin execution. Behavioural checks also verify that pending/success/failure states are honest. Do not substitute "show any spinner within 100 ms" for meaningful feedback or permit all ordinary tasks to become indefinitely pending.

### Measure the whole user journey, not an internal function

- **Once a UI exists:** measure from the actual human action to the **visible change** in a real browser, then to the **visible task outcome**, using representative conditions. An API acknowledgement is an intermediate milestone, not proof of task completion.
- **While this repository is HTTP-only:** measure real client-to-Jetty HTTP round trips as a **temporary proxy**, clearly labelled *API performance*, not as proof of 100 ms human-visible feedback. Ring mocks and pure-function microbenchmarks can help diagnose issues but cannot establish perceived performance.
- **For non-UI or automated use cases:** still specify performance acceptance at the meaningful consumer-visible boundary and choose a relevant outcome deadline. Do not leave the performance section blank; explain why the general UI budgets do not apply.

Keep the implementation of performance measurement **small and reusable**: one shared way to exercise a use case, time feedback/outcome, record the result, and compare against its acceptance budget. Measurements should use a warmed system and sufficient repeated samples to make percentiles meaningful. Do not time a single REPL expression and call it a user-experience acceptance test.

### How performance fits into the two ATDD/TDD loops

1. **Agree before coding:** user, developer and tester agree on behavioural examples **and** human-perceived performance examples/budgets for the use case. Both live in the Issue.
2. **Behavioural ATDD RED → TDD RED/GREEN/REFACTOR:** use our existing fast `(accept)`, `(test-one ...)` and `(check)` feedback loop. Do not force statistical timing assertions into quick unit tests.
3. **Performance acceptance:** run the real user journey under repeatable conditions, measure acknowledgement and final visible outcome, and compare with the specified budgets. If the result fails, the use case remains unaccepted even if every behavioural test is green. Measure again after relevant refactoring.
4. **Evidence before Done:** keep the performance results and conditions with the Issue/PR, alongside behavioural test results. Watch regression trends from a baseline, not only absolute pass/fail numbers.

A small performance-evidence record is enough:

```text
Use case: <name / ID>              Behaviour: PASS / FAIL
Feedback: target p95 <=100 ms     Observed p95: <value>
Completion: target p95 <=1 s     Observed p95: <value>
Slow tail: observed p99 <value>   Errors: <count>
Conditions: <device, network, data, concurrency, environment>
Evidence: <test/run link>         Result: PASS / FAIL / NOT YET MEASURED
```

For an agreed long-running use case, replace the 1-second completion budget with its documented pending/status/outcome criteria; don't record an immediate acknowledgement as a completed task.

**Automation and gating:** `make perf` provides an initial, reproducible **real-HTTP API latency baseline** for the greeting use case. It does not measure visible feedback or completion in a browser, and it has no p95 acceptance threshold yet. Keep the actual human-perceived acceptance criterion recorded and distinguish **API BASELINE RECORDED** from **HUMAN PERFORMANCE ACCEPTED**. A future UI use case cannot be accepted solely on API numbers. Shared GitHub-hosted runners have variable timing: do **not** enforce fragile 100 ms thresholds on an uncontrolled runner. Until a trustworthy timed gate is available, PR review requires explicit performance evidence; a green `make test` does **not** certify responsiveness.

### Executing the first HTTP performance baseline

This repository has a **separate, report-only** benchmark harness that does not change `make test` or the required GitHub Actions check. Install [Vegeta](https://github.com/tsenart/vegeta) (on macOS: `brew install vegeta`), then from the repository root:

```sh
make perf
```

The command starts its **own** Component-managed Jetty instance bound to loopback on an OS-selected port (so it will not conflict with a REPL server on port 3000). It waits for `/health`, confirms the `/hello?name=Ada` response, warms up the running JVM, and issues a fixed-rate sample of HTTP requests. It reports p50, p95, p99, throughput, status codes and errors. It always attempts to stop the temporary application process afterwards.

The default workload is **20 requests/second**, **5 seconds warmup**, and **30 seconds measurement**, with a **5-second request timeout**. For example, to specify a different representative load:

```sh
PERF_RATE=50 PERF_DURATION=60s PERF_WARMUP=10s make perf
```

Outputs are saved under ignored `perf/results/<UTC-date>-<git-revision>-<run-id>/` as `report.txt`, `report.json`, `samples.bin`, `metadata.txt`, and `server.log`. Include the commit, environment, load, latency percentiles, success/error details, and link/path to the report in the Issue or PR. Prefer comparisons from the same controlled machine and matching workload; a laptop measurement is not directly comparable with a shared GitHub Actions runner.

**Interpretation:** These results are **HTTP round-trip timings**, not the user's 100 ms visual acknowledgement or 1-second visible task completion. Do not call a use case performance-accepted merely because `make perf` ran or produced a low p95. The first run establishes evidence, not a pass/fail SLA; define stable, use-case-specific performance budgets after collecting representative baseline measurements. Do not add timing-sensitive checks to the ordinary `make test` suite. A future browser harness will measure actual action-to-visible feedback/completion and should supply the human-perceived performance acceptance result.

### Incremental rollout for this repository

1. **Now — specification:** make behavioural and perceived-performance criteria mandatory in every new or revised use-case Issue. Add a performance-evidence line to the PR and Definition of Done.
2. **First infrastructure increment — API baseline:** `make perf` starts a separate Jetty instance on an OS-selected loopback port, warms it, and measures real HTTP requests for `/hello?name=Ada` using Vegeta. Save the baseline results and repeat under matching conditions. This is an **API proxy**, not a human-perceived UI acceptance measurement. Existing Issue #8 was completed before this requirement; do not misrepresent it as already performance-accepted.
3. **When Angular task-based UI arrives — end-to-end measurement:** use a shared browser-driven journey measurement for first feedback and completed outcome, including truthful pending and failure states. Reuse this harness for subsequent use cases instead of bespoke timing code in every test.
4. **When results are stable — permanent safeguards:** run use-case performance checks in a controlled environment as part of delivery, publish baseline comparisons in PRs, and observe real-user responsiveness and slow-tail regressions after deployment.

The rule **takes effect immediately**; the automation grows incrementally. We should never confuse *"we ran a test"* with *"we proved the person did not have to wait uncertainly"*.

## ATDD: discover and agree on the behaviour first

### 1. Record the desired outcome and business-facing rules

Write an Issue describing a meaningful change in observable behaviour, not a prescribed internal design. State who benefits, what they want to accomplish and what would count as success. **For every use case, also write its perceived-performance acceptance budget and the visible feedback/completion states.** See [Human-perceived performance is part of acceptance](#human-perceived-performance-is-part-of-acceptance).

Example for [Issue #8 — personalised greetings](https://github.com/rorycawley/enduring_software/issues/8) (used here as a worked example):

> Someone can ask for a greeting addressed to a particular person.

Possible agreed acceptance criteria, expressed without HTTP terminology:

- When a name is supplied, the greeting addresses that name.
- When **no name is supplied**, the greeting addresses World.
- When an **explicitly empty name is supplied**, the greeting also addresses World.

Do not write *"When `GET /hello?name=` is called"* as the business rule. That's one particular way the client expresses an empty name, not the behaviour we are trying to specify.

Importantly, **no name supplied** and **an explicitly empty name supplied** are different situations, even though we have agreed that their outcomes match. Keeping both examples means a future implementation cannot accidentally support only one of them.

### 2. Discuss concrete examples (Three Amigos)

Approach each example from three perspectives, considering **responsiveness and behaviour together**:

- **User / stakeholder:** What should the person experience? Does the outcome solve the problem? When would they begin to feel that they are waiting or have lost control?
- **Developer:** What is unclear, difficult, or dependent on another behaviour?
- **Tester:** Which contrasting or boundary cases might disprove our shared understanding? How will we verify both the visible result and its timing under representative load?

Express acceptance scenarios in domain language first. For example, these are the three currently agreed cases for Issue #8:

```gherkin
Scenario: A name is provided
  Given a greeting can be requested
  When someone requests a greeting for "Ada"
  Then the greeting is "Welcome to Clojure, Ada!"

Scenario: No name is provided
  Given a greeting can be requested
  When someone requests a greeting without providing a name
  Then the greeting is "Welcome to Clojure, World!"

Scenario: An explicitly empty name is provided
  Given a greeting can be requested
  When someone requests a greeting with an empty name
  Then the greeting is "Welcome to Clojure, World!"
```

These Given/When/Then statements are a **shared specification**, not a requirement to use a Gherkin parser or Cucumber. They do not mention handlers, routes or implementation details.

#### Map agreed examples onto the public HTTP contract

Because our current product exposes greetings over HTTP, the executable acceptance tests will use these requests:

| Business situation | HTTP example used in the test | Expected response body |
| --- | --- | --- |
| Name Ada supplied | `GET /hello?name=Ada` | `Welcome to Clojure, Ada!` |
| No name supplied | `GET /hello` | `Welcome to Clojure, World!` |
| Explicitly empty name supplied | `GET /hello?name=` | `Welcome to Clojure, World!` |

Separately, our HTTP interface contract specifies the success response (`200` and `Content-Type: text/plain; charset=utf-8`). These details matter for an HTTP client, but they're **not the business definition of a greeting**.

The mapping makes the requirement testable while keeping the business specification useful if the delivery mechanism changes. If we replace HTTP with another interface, we would retain the agreed outcomes and write a new adapter-level test mapping.

**Do not silently decide unresolved cases.** For example, a name containing only whitespace might count as no name, might be rejected, or might have another agreed meaning. Record it as a question until the expected behaviour is decided. Do the same for other relevant missing, invalid or unusually long inputs rather than inventing policies to increase test counts.

### 3. Make the examples executable — ATDD RED

Keep the performance criterion and its measurement conditions next to the behavioural examples from the outset. The first RED step usually concerns behaviour; a later end-to-end measurement supplies separate quantitative performance evidence. Do not claim the whole use case is accepted when only behaviour turns GREEN.

Translate an **agreed example** into an acceptance test at the **public boundary that expresses the requirement**. The test code may mention URLs, status codes and response headers; the business-facing rule above should not have to. For the current HTTP application, that boundary is often real HTTP through Component-managed Jetty. For a future bounded context, it might be its public contract, supported by separate real-adapter integration tests.

For the personalised-greeting increment, use a dedicated file such as `test/foo/acceptance/greeting_test.clj` and register `foo.acceptance.greeting-test` in both `(accept)` and `(run-tests)` in `dev/user.clj`. The acceptance test starts a real Component-managed Jetty server, issues an HTTP request, and checks its response. For example:

```clojure
(ns foo.acceptance.greeting-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [foo.system :as system]))

(defn- http-get [path]
  (let [running (component/start
                 (system/new-system {:port 0}))]
    (try
      (let [jetty (get-in running [:jetty :server])
            port (.getLocalPort (first (.getConnectors jetty)))
            url (str "http://127.0.0.1:" port path)
            connection (.openConnection
                        (.toURL (java.net.URI/create url)))]
        {:status (.getResponseCode connection)
         :content-type (.getContentType connection)
         :body (slurp (.getInputStream connection))})
      (finally
        (component/stop running)))))

(deftest greet-person-by-name
  (let [{:keys [status content-type body]}
        (http-get "/hello?name=Ada")]
    (is (= 200 status))
    (is (re-matches #"(?i)text/plain\s*;\s*charset=utf-8"
                    content-type))
    (is (= "Welcome to Clojure, Ada!" body))))
```

The helper is **test infrastructure**, local to the acceptance namespace; it must not be copied into the pure `foo.greeting-test` unit-test namespace. It starts and stops a fresh system for each request, with `finally` ensuring cleanup. The executable example checks the **"Ada" business criterion** and the HTTP response contract. Add separate tests for omitted and explicitly empty names; both expect `World`.

At the beginning of an ATDD cycle, run `(test-one 'foo.acceptance.greeting-test/greet-person-by-name)` or `(accept)` and confirm **RED due to the missing behaviour**. After implementation it should be GREEN. A missing file, absent test Var, compilation error or HTTP setup exception is **not** a valid RED. Running the acceptance suite after the behaviour is already implemented demonstrates coverage, but does not retroactively establish an ATDD red-first sequence.

Using port `0` asks the operating system to allocate a free port, avoiding conflicts with the development server.

### 4. Enter the TDD inner loop

With the acceptance test red, write smaller tests that help us introduce the capability in controlled steps:

1. A rule or handler can produce a greeting for the supplied name.
2. An HTTP request's query string is interpreted appropriately.
3. The handler connects the request input to the greeting behaviour.

Follow **Red → Green → Refactor** for each small step. Keep the acceptance example unchanged unless the agreed requirement changes.

Once the acceptance example passes, check the other agreed examples and any existing behaviour that might have regressed. Add tests only for real requirements and risks, not speculative functionality.

### 5. Complete the acceptance loop

Before declaring the Issue ready:

- Confirm every **agreed behavioural acceptance example** is covered and passing, and remains traceable to its business-facing criterion.
- Verify **perceived-performance acceptance** for each affected use case: human-visible feedback/completion against its agreed budget (or a clearly identified interim API proxy before the UI exists), along with honest delay, failure and unknown-state behaviour. Record measurement conditions and results; do not declare unmeasured targets satisfied.
- Run the entire suite in a fresh JVM using `make test`.
- Review what the tests *do not* establish: public contracts, failures and assumptions should be explicit.
- Verify with stakeholders where possible; a green automated test does not replace agreement on the outcome.
- Deliver the change through the GitHub PR/CI workflow below.

### Where acceptance tests live

Behavioural acceptance tests live under `test/foo/acceptance/`; **timed performance acceptance** can use a separate reproducible harness while sharing the same use-case specification and evidence. We should not mix noisy time thresholds into the fast Clojure test runner. The existing dedicated acceptance tests live under `test/foo/acceptance/`. Our first example, `foo.acceptance.health-test` in `test/foo/acceptance/health_test.clj`, exercises `/health` over real HTTP using a temporary Component-managed Jetty server. It verifies *already agreed* behaviour; it is not an example of the RED stage for a new feature.

`dev/user.clj` currently names test namespaces **directly in two functions**, not in central `regular-test-namespaces` / `acceptance-test-namespaces` Vars. `(accept)` runs the acceptance namespaces; `(check)` refreshes code and invokes `(run-tests)` for the entire REPL suite. **When adding a new acceptance namespace, add it to both `(accept)` and `(run-tests)`**. Read the actual source before following an example in documentation.

`make test` uses the Cognitect test runner and discovers test namespaces from the `test` directory independently of these REPL lists. Verify both `(check)` and `make test` whenever adding or moving a namespace; don't assume a passing suite implies a new test was executed.

Acceptance is a **role a test plays**, not just a folder name or the fact that it crosses a network. Pure domain tests, handler tests, integration tests and acceptance tests complement each other. We do not need Cucumber or another framework for these tests.

## TDD inner loop and test levels

Use **Red → Green → Refactor** for one small behaviour at a time:

1. **Red:** Add a focused test that describes the required behaviour. Run `(test-one 'some.namespace/some-test)` and verify it fails for the expected reason.
2. **Green:** Make the smallest useful change that passes that test. Run `(test-one ...)` again.
3. **Refactor:** Improve structure only where there is a real benefit, preserving green tests. Run `(check)` as the change grows and before final verification.

For example, [Issue #4](https://github.com/rorycawley/enduring_software/issues/4) specified that `GET /health` returns `200`, the expected content type, and body `OK`. The corresponding Ring handler test exercises the request and response maps without Jetty.

The current test suite covers:

| Test namespace | What it verifies |
| --- | --- |
| `foo.greeting-test` | Pure greeting behaviour, including empty/blank names |
| `foo.http-test` | Ring/Reitit HTTP responses and unknown-route handling |
| `foo.system-test` | Component-managed Jetty starts, serves real HTTP requests and stops |
| `foo.acceptance.health-test` | Acceptance example for application liveness through real HTTP |
| `foo.acceptance.greeting-test` | Personalised, missing-name and empty-name examples through real HTTP |

The real HTTP integration test uses port `0` so the operating system selects an available port. This avoids conflicting with the development server on port 3000.

After Issue #8, the three greeting acceptance examples brought the fresh-JVM suite to **12 tests and 24 assertions**. Counts are a snapshot, not a permanent pass criterion: make sure the *intended test names and scenarios* are actually present before trusting the total. Run it in a fresh JVM before committing:

```sh
make test
```

These tests verify **behaviour** at different boundaries. A passing suite is useful evidence, not a guarantee that every possible defect has been prevented — and not proof that a task satisfies its **human-perceived performance acceptance**. That requires separate, representative measurements.

## REPL development loop

### REPL-first ATDD/TDD

Keep a persistent Clojure REPL open for exploration and rapid feedback. Prefer the smallest operation that answers the current question; use progressively broader checks as confidence increases.

| Command | Purpose | Typical use |
| --- | --- | --- |
| Evaluate a Clojure expression | Explore data and behaviour directly | Continuously |
| `(test-one 'foo.http-test/health-endpoint-test)` | Refresh changed namespaces and run one named test | Fast TDD feedback |
| `(accept)` | Refresh and run the acceptance-test suite | ATDD outer loop |
| `(check)` | Refresh and run the entire configured REPL suite | After a small increment |
| `(go)` | Stop, refresh and restart the development system | Exploring changed HTTP behaviour |
| `(start)` / `(stop)` | Start or stop the development Component system | Explicit lifecycle management |
| `make test` | Run all tests in a fresh JVM | Before committing and in CI |

The REPL commands use `clojure.test`, `clojure.tools.namespace` and Component; they introduce no extra test framework.

#### ATDD outer loop

1. Agree on the **business-facing acceptance criterion**, distinct from the delivery mechanism.
2. Agree on concrete examples and map each to the current public interface.
3. Save the first executable acceptance example in `test/foo/acceptance/` and register its namespace in both `(accept)` and `(run-tests)` in `dev/user.clj`. Verify the file contains the test before running it.
4. Run `(accept)` (or `(test-one '.../specific-example)`) and verify that the test fails **for the intended behavioural reason**.
5. Enter the TDD inner loop, keeping the acceptance example stable unless the agreed requirement changes.
6. Run `(accept)` again to establish that the agreed behaviour is satisfied. Complete any other agreed examples.

#### TDD inner loop

1. Evaluate the relevant pure functions and data directly in the REPL to understand the problem.
2. Write **one focused test** describing the next small behaviour.
3. Run `(test-one 'namespace/test-name)` and see RED for the expected reason.
4. Make the smallest change to reach GREEN; evaluate intermediate expressions as needed.
5. Refactor with the focused test passing. Repeat until the acceptance example becomes green.
6. Run `(check)` to catch regressions outside the focused test.

A focused test is not necessarily an acceptance test; it could exercise a pure rule or an HTTP handler. Test **observable behaviour**, not implementation details, whenever possible.

#### Test isolation and Component lifecycle

Acceptance tests must not depend on the running development server. Each real HTTP acceptance test should start an isolated Component system using an ephemeral port (`{:port 0}`), issue requests through HTTP, and stop its system in a `finally` block, even on failure. Shared test fixtures may centralise this lifecycle when the suite grows.

The development server started by `(go)` is for interactive exploration. A test that starts its own server should work even when `(go)` has never been called. Avoid reusing development-server state across acceptance examples.

#### Reloading and retained state

The helpers live in [`dev/user.clj`](dev/user.clj). In the development REPL:

```clojure
(go)       ; stop → refresh changed namespaces → start
(start)    ; start the Component system if needed
(stop)     ; stop the Component system
(accept)   ; acceptance tests only
(check)    ; regular and acceptance tests
(test-one 'foo.http-test/health-endpoint-test)
(keys @system)
(-> @system :jetty :server .isStarted)
```

`clojure.tools.namespace` reloads changed source and test namespaces when these helpers invoke it; this is not an automatic file watcher. **Changes to `dev/user.clj` require an explicit reload** because `dev` is deliberately excluded from `tn/set-refresh-dirs`:

```clojure
(require 'user :reload)
```

The development helpers keep a `defonce` atom holding the current running Component system. Reloading code is not the same as restarting stateful resources. In particular, Jetty may retain an earlier Ring handler after `(check)` or `(accept)` reloads `foo.http`. Call `(go)` to restart Jetty before manually inspecting the changed HTTP endpoint.

After changing `deps.edn`, restart the REPL to pick up the changed classpath. The SLF4J warning about no logging provider is presently nonfatal.

#### Lessons from Issue #8: trustworthy ATDD feedback

The personalised-greeting increment exposed several ways that a development loop can appear green without proving the intended behaviour. Apply these checks as **part of the workflow**, not just when a test fails mysteriously:

1. **Save the acceptance test, then verify the file.** Check the path, namespace, and `deftest` declarations. A mistaken shell edit left `test/foo/acceptance/greeting_test.clj` at **0 bytes** while the REPL still executed a previously defined test. A successful `(accept)` is not proof that the test exists on disk. For example:

   ```sh
   wc -c test/foo/acceptance/greeting_test.clj
   grep -n '^(deftest' test/foo/acceptance/greeting_test.clj
   ```

   The three agreed examples should have separate `deftest` definitions: `greet-person-by-name`, `greet-person-without-name`, and `greet-person-with-empty-name`. A test run can be green because an intended test was **not run**; count the intended scenarios, not just assertions or passing suites.

2. **Distinguish saved code from live REPL state.** `clojure.tools.namespace` refreshes changed namespaces, but ordinary `require :reload` does not necessarily remove old Vars whose definitions have vanished from the source file. If a file was emptied, deleted, moved or renamed, old definitions can mislead you. Inspect the file and use a fresh JVM (`make test`) as the delivery gate. If `(test-one ...)` throws a `NullPointerException` inside `clojure.test/run-test-var`, check that `(requiring-resolve 'namespace/test-name)` returns a Var; a missing test is not a behavioural test failure.

3. **Exercise each boundary with the right request shape.** A Ring mock may provide parsed `:query-params` even when a real Jetty request initially provides only `:query-string`. A passing handler test can therefore miss missing query-parameter middleware. When diagnosing differences, compare (a) the Ring app with a raw query string, (b) direct Jetty + Ring, (c) Component-managed Jetty, and (d) the executable acceptance test. Real HTTP acceptance checks are necessary for the public HTTP contract; mock-based handler tests remain useful for fast TDD.

4. **Keep infrastructure code in infrastructure-facing tests.** `foo.greeting-test` should only exercise pure greeting logic. The helper that creates Component, binds port `0`, sends real HTTP and stops Jetty belongs in `foo.acceptance.greeting-test` (or a dedicated acceptance support namespace), **not** `foo.greeting-test`. A compile error such as `No such namespace: component` can be evidence that a helper was pasted into the wrong file. Read the *file and line number* in the error before changing application code.

5. **Refactor one test at a time and stay green.** Introduce a shared `http-get` helper, verify, then convert each acceptance test to assert response data (`:status`, `:content-type`, `:body`). Keep the `try/finally` lifecycle in the helper. After every small change, run the tests and compare the intended assertions. Test code needs refactoring discipline too.

6. **Review the Git worktree before staging.** `git status --short` shows modified files and untracked acceptance tests. Run `git diff -- test/foo/greeting_test.clj` to confirm a pure unit-test file was not accidentally modified by an HTTP-test refactor. Run `git diff --check` and `make test`; explicitly stage any untracked acceptance test. The new documentation should be committed alongside the behaviour it explains.

These lessons reinforce the distinction between **ATDD as a shared specification and executable acceptance feedback**, **TDD as fast design feedback**, and **fresh-JVM CI as a reproducibility check**. None substitutes for the others.

#### Namespace lists and complete verification

The actual implementation in `dev/user.clj` enumerates namespaces directly. Once Issue #8's acceptance tests are added, these functions should contain the following namespaces (other helper functions are omitted here):

```clojure
(defn run-tests []
  (t/run-tests 'foo.greeting-test
               'foo.http-test
               'foo.system-test
               'foo.acceptance.health-test
               'foo.acceptance.greeting-test))

(defn accept []
  (when (= :ok (tn/refresh))
    (t/run-tests 'foo.acceptance.health-test
                 'foo.acceptance.greeting-test)))
```

`(accept)` runs the acceptance tests; `(check)` refreshes changed namespaces and runs all namespaces listed by `(run-tests)`. **A fresh JVM using `make test` discovers tests independently** via the Cognitect runner. Confirm that the actual file is populated, the expected test names exist, and both runner paths include the new tests. Do not infer coverage merely from a green summary.

Before creating a PR:

```clojure
(check)
```

In a separate terminal, verify the tests in a new JVM and check the diff:

```sh
make test
git diff --check
```

This ensures results do not depend on stale REPL definitions or state. CI runs the same `make test` command.

For manual HTTP exploration after `(go)`:

```sh
curl -i http://127.0.0.1:3000/health
```

Expect status `200`, `Content-Type: text/plain; charset=utf-8`, and body `OK`.


## GitHub Issue → PR → CI → Done

We use:

- **[Enduring Software — Project #5](https://github.com/users/rorycawley/projects/5)** for visibility and status: `Todo → In Progress → Done`.
- **GitHub Issues** to capture the desired outcome, acceptance criteria and verification needed.
- **Short-lived branches and pull requests** to deliver one small, independently testable increment.
- **GitHub Actions CI** to verify tests before merging.
- **Protected `main`** as the single trunk.

The Project is linked to `rorycawley/enduring_software`. Its configured automation moves an item to **Done** when the corresponding Issue closes; this was verified with Issue #4.

The one-time repository link was established with:

```sh
gh project link 5 --owner rorycawley --repo rorycawley/enduring_software
```

You do not need to repeat this command for every Issue.

### 1. Capture one piece of work

Start with a GitHub Issue whose description focuses on *what must be true*, not on internal implementation choices. Specify the **business rules first**, clarify them with examples, **specify human-perceived performance for every use case**, and record the technical public contract separately where needed:

```markdown
## Outcome

What should someone be able to do, or what problem should be solved?

## Acceptance criteria (business language)

- When [business situation], [required observable outcome].
- ...

## Agreed behavioural examples

- Given ..., when ..., then ...
- Include contrasting and boundary examples where relevant.

## Performance acceptance (required for each use case)

- Human action / event that starts timing: ...
- First meaningful visible feedback: ... within 100 ms at p95 (or justified alternative)
- Visible completion: ... within 1 second at p95 for ordinary tasks (or justified long-running classification)
- Long-running/failed/unknown state, status/recovery/timeout expectations: ...
- Measurement conditions: representative device, network, test data, concurrency, environment
- Measurement evidence / acceptance-test link: ... (record p95, observe p99, errors)

## Public interface contract (where relevant)

- HTTP method/path, status, headers or message formats that clients rely on.
- Link each technical example to its business scenario.

## Open questions

- Record unresolved cases rather than guessing.

## Verification

- Executable behavioural acceptance tests for agreed examples
- Measured performance acceptance for the affected use cases, with conditions, baseline and evidence
- Fast domain/handler tests and real-adapter integration tests as needed
- Behavioural tests pass locally and in CI; the agreed performance gate/evidence also passes before Done
```

Create an Issue and add it to the Project:

```sh
gh issue create \
  --title "Describe the desired behaviour" \
  --project "Enduring Software"
```

GitHub CLI will prompt for the body. A linked Project item should initially be `Todo`.

View the backlog:

```sh
gh project item-list 5 --owner rorycawley
gh issue list
```

### 2. Move the Issue to In Progress

For a selected issue, first inspect its item ID and the Project's Status field:

```sh
gh project item-list 5 --owner rorycawley --format json
gh project field-list 5 --owner rorycawley --format json
```

The IDs belong to the Project item and field, **not** the repository Issue number; they are different kinds of identifiers.

You can retrieve the IDs and change the status from the CLI. Replace `4` with your actual Issue number:

```sh
ISSUE=4
OWNER=rorycawley
PROJECT=5

PROJECT_ID=$(gh project view "$PROJECT" --owner "$OWNER" \
  --format json --jq '.id')

ITEM_ID=$(gh project item-list "$PROJECT" --owner "$OWNER" \
  --format json \
  --jq ".items[] | select(.content.number == $ISSUE and .content.repository == \"rorycawley/enduring_software\") | .id")

FIELD_ID=$(gh project field-list "$PROJECT" --owner "$OWNER" \
  --format json --jq '.fields[] | select(.name == "Status") | .id')

OPTION_ID=$(gh project field-list "$PROJECT" --owner "$OWNER" \
  --format json \
  --jq '.fields[] | select(.name == "Status") | .options[] | select(.name == "In Progress") | .id')

gh project item-edit \
  --project-id "$PROJECT_ID" \
  --id "$ITEM_ID" \
  --field-id "$FIELD_ID" \
  --single-select-option-id "$OPTION_ID"
```

Confirm the Issue is now in progress:

```sh
gh project item-list 5 --owner rorycawley --format json \
  --jq '.items[] | select(.content.number == 4) | {title, status}'
```

If a command returns a blank ID, inspect the Project JSON and correct the selection **before** calling `item-edit`.

### 3. Create a short-lived branch from `main`

With a clean working tree:

```sh
git switch main
git pull --ff-only
git switch -c feat/ISSUE-NUMBER-short-description
```

For our first Issue-driven increment, we used `feat/4-health-endpoint`.

Try to work on one Issue at a time. Split large work into safe, independently testable increments rather than keeping a branch open for days.

### 4. Develop and verify with ATDD and TDD

Start the REPL in one terminal:

```sh
make repl
```

Agree on **both behavioural and perceived-performance acceptance** for the use case, with a human-visible start/end point and representative measurement conditions. Map behaviour to the published interface and make the first behavioural acceptance test fail for the expected reason using `(accept)`. Then use `(test-one 'namespace/test-name)` in the TDD inner loop (Red → Green → Refactor). Run `(accept)` again when the feature is ready, and `(check)` for the whole configured suite. Use `(go)` only when restarting the interactive Component-managed development server after source changes.

**Before calling the use case accepted**, perform its repeatable end-to-end performance measurement, record p95 (and observe p99), verify any pending/error states, and attach the result to the Issue/PR. With today's HTTP-only app, a real-HTTP performance probe is explicitly interim evidence; browser-visible measurements replace that proxy when the UI is delivered. Run `make perf` for the available HTTP API proxy and record its report path; measure browser-visible feedback and outcomes separately once the UI exists.

In a separate terminal, verify the final increment:

```sh
make test
git diff --check
git status --short
git diff
```

Stage only intended files. For example:

```sh
git add src/foo/http.clj test/foo/http_test.clj test/foo/system_test.clj
git commit -m "feat: add HTTP health endpoint"
```

Use `git add -p` when you need more selective staging. Never include generated caches or credentials.

### 5. Push and create a PR linked to the Issue

In the PR description, report **behavioural acceptance** and **perceived-performance acceptance** separately. Include the use-case budget, measurement conditions, results and baseline, or state plainly that an interim proxy is being used and which human-visible measurement is not yet available. Do not mark an unmeasured budget as passed.

Push your short-lived branch (substitute your own branch name):

```sh
git push -u origin feat/ISSUE-NUMBER-short-description
```

Create the PR with a closing keyword in the description:

```sh
gh pr create \
  --base main \
  --title "feat: describe delivered behaviour" \
  --body "Closes #ISSUE_NUMBER"
```

Replace `ISSUE_NUMBER` with the actual number, for example `Closes #4`. This links the PR to the Issue so merging into `main` automatically closes it.

You can inspect the PR using:

```sh
gh pr view --web
```

### 6. Let GitHub Actions verify the change

```sh
gh pr checks --watch
```

The workflow in [`.github/workflows/ci.yml`](.github/workflows/ci.yml) executes `make test` using Java 21 and the Clojure CLI on pull requests targeting `main` and on pushes to `main`.

The required check is named **`Test`** (shown in the CLI as `CI/Test (pull_request)`). If `gh pr checks --watch` initially reports no checks, the workflow may not yet have started; check the PR's Actions tab or run the command again.

If tests fail, correct the problem on the same branch, rerun local tests, commit and push; do not bypass branch protection. **This existing check verifies behavioural tests, not timing.** Performance acceptance must also be demonstrated before accepting a use case. Once the timing environment is controlled and the harness reliable, add a separate required performance check rather than treating GitHub-hosted runner wall-clock timing as reliable.

### 7. Merge, close the Issue and finish the Project item

Once the required check passes:

```sh
gh pr merge --squash --delete-branch
git switch main
git pull --ff-only
git status
```

Because the PR body contains `Closes #ISSUE_NUMBER`, GitHub closes the Issue on merge. Project automation then changes the item's status to `Done`.

Verify both:

```sh
gh issue view 4 --json state,title

gh project item-list 5 --owner rorycawley --format json \
  --jq '.items[] | select(.content.number == 4) | {title, status}'
```

For completed Issue #4, the results were `CLOSED` and `Done`.

The outcome is a traceable history:

```text
Outcome + business criteria + examples (Issue)
              ↓
   ATDD (red) + TDD + ATDD (green)
              ↓
   Tests + reviewed change (PR)
              ↓
    Verified checks (GitHub CI)
              ↓
     Protected main (merge)
              ↓
      Closed Issue + Done
```

### GitHub workflow — quick reference

```sh
# Select work in Project #5; move its Issue to In Progress
git switch main
git pull --ff-only
git switch -c feat/ISSUE-NUMBER-short-description

# Agree behavioural + human-perceived performance acceptance for every use case
# Work in the REPL: (test-one 'namespace/test-name), (accept), (check)
# Use (go) to restart the development server when needed
make test
# Record HTTP API latency where applicable (proxy only, not visible UI performance)
make perf
# For UI use cases, separately measure user-visible feedback and task completion
git diff --check

git add -p  # Stage the intended changes; add new files explicitly
git commit -m "feat: describe the change"
git push -u origin feat/ISSUE-NUMBER-short-description

gh pr create --base main \
  --title "feat: describe the change" \
  --body "Closes #ISSUE_NUMBER"

gh pr checks --watch
gh pr merge --squash --delete-branch

git switch main
git pull --ff-only
git status
# Verify the Issue closed and the Project item moved to Done
```

## Protecting the trunk

The repository's active [Protect main ruleset](https://github.com/rorycawley/enduring_software/rules/25802970) requires a PR and a successful **Test** check. The branch must be current with `main`; force-pushes and deletion are blocked, and there are no bypass exceptions.

**Do not push directly to `main`.** This applies equally to source changes, tests, documentation and CI configuration.

The working agreements are:

- Keep `main` working and releasable.
- Use small changes and integrate frequently.
- Describe **behavioural and human-perceived performance acceptance** in business language before implementation; keep concrete examples, timing evidence and interface-specific checks traceable to the use case. Link every meaningful change to an Issue when practical.
- **Definition of Done for every use case:** required behaviour passes and the user-visible responsiveness budget is met with recorded, representative evidence (or the documented, genuinely long-running experience is verified). No silent/indefinite waiting, and no claiming that fast API responses prove a fast UI.
- Evaluate expressions and run focused tests from the REPL during TDD; run `(accept)` for ATDD, `(check)` for the full REPL suite and `make test` before creating the PR.
- Commit behaviour and its tests together.
- Let CI confirm the result before merging.
- Use the Project board for visibility, not as a substitute for the Issue, tests or PR.
- Keep the status workflow small: Todo, In Progress, Done.

## Reference: first completed Issue-driven change

- [Issue #4 — Add GET /health liveness endpoint](https://github.com/rorycawley/enduring_software/issues/4)
- [PR #5 — HTTP health endpoint](https://github.com/rorycawley/enduring_software/pull/5)
- [Issue #10 — Optimise REPL-driven ATDD and TDD workflow](https://github.com/rorycawley/enduring_software/issues/10)
- [Enduring Software Project #5](https://github.com/users/rorycawley/projects/5)

This work followed the complete cycle and verified the Project's automatic transition to `Done`.
