# ATDD / TDD development workflow

This is the repository's **single guide for how we develop and deliver a change**. The [README](README.md) covers installation, running the application and its structure; this document owns the detailed engineering workflow.

Our goal is to make a small, verifiable change from an agreed user outcome to working software on protected `main`, retaining a traceable link between the requirement and its tests.

## One workflow, two feedback loops

```text
GitHub Issue: outcome + business-facing criteria
                     │
                     ▼
      Agree examples → acceptance RED
                     │
                     ▼
        ┌─────────────────────────┐
        │ TDD: RED → GREEN        │
        │      → REFACTOR         │
        │ Repeat for small rules  │
        └─────────────────────────┘
                     │
                     ▼
         ATDD: acceptance test GREEN
                     │
                     ▼
      make test → PR → CI → merge
                     │
                     ▼
        Issue CLOSED → Project Done
```

| ATDD (outer loop) | TDD (inner loop) |
| --- | --- |
| Agree on **what behaviour is valuable** and what examples demonstrate it | Design and implement **how that behaviour works**, one small step at a time |
| Driven by stakeholder outcomes and public, observable behaviour | Driven by tests of small functions, domain rules, handlers and collaborators |
| Acceptance checks should remain meaningful if the internals change | Implementation-level tests can evolve as internals are refactored |
| Ends when agreed examples work through the relevant public boundary | Repeats Red → Green → Refactor until the acceptance example can pass |

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

## ATDD: discover and agree on the behaviour first

### 1. Record the desired outcome and business-facing rules

Write an Issue describing a meaningful change in observable behaviour, not a prescribed internal design. State who benefits, what they want to accomplish and what would count as success.

Example for [Issue #8 — personalised greetings](https://github.com/rorycawley/enduring_software/issues/8) (**under discussion; not yet implemented**):

> Someone can ask for a greeting addressed to a particular person.

Possible agreed acceptance criteria, expressed without HTTP terminology:

- When a name is supplied, the greeting addresses that name.
- When **no name is supplied**, the greeting addresses World.
- When an **explicitly empty name is supplied**, the greeting also addresses World.

Do not write *"When `GET /hello?name=` is called"* as the business rule. That's one particular way the client expresses an empty name, not the behaviour we are trying to specify.

Importantly, **no name supplied** and **an explicitly empty name supplied** are different situations, even though we have agreed that their outcomes match. Keeping both examples means a future implementation cannot accidentally support only one of them.

### 2. Discuss concrete examples (Three Amigos)

Approach each example from three perspectives:

- **User / stakeholder:** What should the person experience? Does the outcome solve the problem?
- **Developer:** What is unclear, difficult, or dependent on another behaviour?
- **Tester:** Which contrasting or boundary cases might disprove our shared understanding?

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

Translate an **agreed example** into an acceptance test at the **public boundary that expresses the requirement**. The test code may mention URLs, status codes and response headers; the business-facing rule above should not have to. For the current HTTP application, that boundary is often real HTTP through Component-managed Jetty. For a future bounded context, it might be its public contract, supported by separate real-adapter integration tests.

We can begin by adding a new acceptance `deftest` to the *existing* `test/foo/system_test.clj`, which is already included in `(check)`:

```clojure
(deftest personalised-greeting-acceptance-test
  (let [running (component/start
                 (system/new-system {:port 0}))]
    (try
      (let [jetty (get-in running [:jetty :server])
            port (.getLocalPort
                   (first (.getConnectors jetty)))
            url (str "http://127.0.0.1:"
                     port "/hello?name=Ada")
            connection (.openConnection
                         (.toURL (java.net.URI/create url)))]

        (is (= 200 (.getResponseCode connection)))
        (is (= "Welcome to Clojure, Ada!"
               (slurp (.getInputStream connection)))))

      (finally
        (component/stop running)))))
```

This is an **illustration**, not a test already present in the repository. It checks the **"Ada" business example** through its concrete HTTP mapping. It should initially fail because the current `/hello` endpoint always returns `Welcome to Clojure, World!`. Add equivalent acceptance checks for the other agreed cases as the increment progresses.

Run `(check)` and confirm the *expected* failure. A typo, missing namespace or test setup exception is **not** the desired Red stage.

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

- Confirm every **agreed acceptance example** is covered and passing, and remains traceable to its business-facing criterion.
- Run the entire suite in a fresh JVM using `make test`.
- Review what the tests *do not* establish: public contracts, failures and assumptions should be explicit.
- Verify with stakeholders where possible; a green automated test does not replace agreement on the outcome.
- Deliver the change through the GitHub PR/CI workflow below.

### Where acceptance tests live

For now, keeping a few acceptance cases in `foo.system-test` is simplest and requires **no additional framework**.

Later, if the suite grows, we can introduce `test/foo/acceptance/` namespaces. **Important:** `dev/user.clj` currently enumerates test namespaces explicitly in `run-tests`, so `(check)` will *not* automatically discover a new acceptance namespace. When moving/adding namespaces, update the helper (or a test-discovery strategy) and confirm `make test` includes them.

Acceptance is a **role a test plays**, not a folder name or the fact that it crosses a network. Pure domain tests, handler tests, integration tests and acceptance tests are complementary, not competing categories.

## TDD inner loop and test levels

Use **Red → Green → Refactor** for one small behaviour at a time:

1. **Red:** Add a test that describes the required observable behaviour. Run `(check)` and verify it fails for the expected reason.
2. **Green:** Make the smallest useful code change that satisfies the test. Run `(check)` again.
3. **Refactor:** Improve structure only where there is a real benefit, preserving green tests.

For example, [Issue #4](https://github.com/rorycawley/enduring_software/issues/4) specified that `GET /health` returns `200`, the expected content type, and body `OK`. The corresponding Ring handler test exercises the request and response maps without Jetty.

The current test suite covers:

| Test namespace | What it verifies |
| --- | --- |
| `foo.greeting-test` | Pure greeting behaviour, including empty/blank names |
| `foo.http-test` | Ring/Reitit HTTP responses and unknown-route handling |
| `foo.system-test` | Component-managed Jetty starts, serves real HTTP requests and stops |

The real HTTP integration test uses port `0` so the operating system selects an available port. This avoids conflicting with the development server on port 3000.

As of completing Issue #4, the suite has **7 tests and 14 assertions**. Run it in a fresh JVM before committing:

```sh
make test
```

These tests verify behaviour at different boundaries. A passing suite is useful evidence, not a guarantee that every possible defect has been prevented.

## REPL development loop

The helpers in [`dev/user.clj`](dev/user.clj) give you:

```clojure
(go)       ; stop → refresh changed namespaces → start
(start)    ; start the Component system
(stop)     ; stop the Component system
(check)    ; refresh changed namespaces → run tests
(keys @system)
(-> @system :jetty :server .isStarted)
```

`clojure.tools.namespace` reloads changed source and dependent namespaces when `(go)` or `(check)` is invoked; it is **not** continuously watching files.

Component owns resource lifecycle. A `defonce` atom in `dev/user.clj` holds the current running system across namespace refreshes. The REPL stays alive while `(go)` stops and starts Jetty.

**Important:** `(check)` can reload HTTP namespaces while the existing Jetty instance continues holding its earlier Ring handler. After changing a handler, call `(go)` before checking the live endpoint over HTTP.

After changing `deps.edn`, restart the REPL so the new dependencies appear on its classpath.

Example from another terminal:

```sh
curl -i http://127.0.0.1:3000/health
```

Expected response includes `200`, `Content-Type: text/plain; charset=utf-8`, and body `OK`.

An SLF4J warning about a missing provider is currently nonfatal; the application has not yet configured a logging backend.

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

Start with a GitHub Issue whose description focuses on *what must be true*, not on internal implementation choices. Specify the **business rules first**, clarify them with examples, and record the technical public contract separately where needed:

```markdown
## Outcome

What should someone be able to do, or what problem should be solved?

## Acceptance criteria (business language)

- When [business situation], [required observable outcome].
- ...

## Agreed examples

- Given ..., when ..., then ...
- Include contrasting and boundary examples where relevant.

## Public interface contract (where relevant)

- HTTP method/path, status, headers or message formats that clients rely on.
- Link each technical example to its business scenario.

## Open questions

- Record unresolved cases rather than guessing.

## Verification

- Executable acceptance tests for agreed examples
- Fast domain/handler tests and real-adapter integration tests as needed
- All tests pass locally and in CI
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

Agree on business-facing criteria and concrete examples, map them to the published interface, and make the first acceptance test fail for the expected reason. Then work through the TDD inner loop (Red → Green → Refactor), calling `(check)` frequently. Use `(go)` to restart the Component-managed HTTP server after source changes.

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

If tests fail, correct the problem on the same branch, rerun local tests, commit and push; do not bypass branch protection.

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

# Work in the REPL: (check), (go)
make test
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
- Describe acceptance criteria in business language before implementation; keep concrete examples and interface-specific checks traceable to them. Link every meaningful change to an Issue when practical.
- Run fast REPL tests as you work and `make test` before creating the PR.
- Commit behaviour and its tests together.
- Let CI confirm the result before merging.
- Use the Project board for visibility, not as a substitute for the Issue, tests or PR.
- Keep the status workflow small: Todo, In Progress, Done.

## Reference: first completed Issue-driven change

- [Issue #4 — Add GET /health liveness endpoint](https://github.com/rorycawley/enduring_software/issues/4)
- [PR #5 — HTTP health endpoint](https://github.com/rorycawley/enduring_software/pull/5)
- [Enduring Software Project #5](https://github.com/users/rorycawley/projects/5)

This work followed the complete cycle and verified the Project's automatic transition to `Done`.
