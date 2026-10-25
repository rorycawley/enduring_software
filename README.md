# enduring_software

A small Clojure project for learning **REPL-driven development**, **test-driven development (TDD)**, and **incremental delivery with GitHub Issues and Projects**.

The working principle is simple: specify one observable behaviour, prove it with tests, change the running application through the REPL, and deliver a small verified increment to protected `main`.

## Quick start

### Prerequisites

- Java 21 (the project's development and CI JDK)
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- `make` and `rlwrap` (for the terminal REPL)
- Git and [GitHub CLI (`gh`)](https://cli.github.com/)

Authenticate the GitHub CLI once:

```sh
gh auth login
gh auth status
```

To use the GitHub Projects commands, your GitHub CLI authentication also needs the `project` scope:

```sh
gh auth refresh -s project
```

### Everyday commands

| Command | Purpose |
| --- | --- |
| `make repl` | Start a persistent REPL using the `:dev` alias |
| `(go)` | Stop the running system, reload changed namespaces, and restart it |
| `(start)` | Start the Component system if it is not running |
| `(stop)` | Stop the Component system |
| `(check)` | Reload changed namespaces and run the three test namespaces |
| `make run` | Run the Jetty HTTP server in a fresh JVM |
| `make test` | Run all tests in a fresh JVM (also used by CI) |

Start the development REPL:

```sh
make repl
```

Then, at the `user=>` prompt:

```clojure
(go)
(slurp "http://127.0.0.1:3000/hello")
(slurp "http://127.0.0.1:3000/health")
(check)
```

The responses from the two requests are `"Welcome to Clojure, World!"` and `"OK"` respectively. To end a REPL session cleanly, run `(stop)` and then exit the REPL.

## Project structure

```text
enduring_software/
├── .github/
│   └── workflows/
│       └── ci.yml             # GitHub Actions CI
├── dev/
│   └── user.clj               # REPL system lifecycle and test helpers
├── src/
│   └── foo/
│       ├── core.clj           # Standalone entry point
│       ├── greeting.clj       # Pure greeting behaviour
│       ├── http.clj           # Ring handlers and Reitit routes
│       ├── server.clj         # Component-managed Jetty server
│       └── system.clj         # Component system definition
├── test/
│   └── foo/
│       ├── greeting_test.clj  # Pure behaviour tests
│       ├── http_test.clj      # Fast Ring/Reitit handler tests
│       └── system_test.clj    # Real HTTP + Component lifecycle test
├── .gitignore
├── deps.edn
├── Makefile
└── README.md
```

Application behaviour belongs in `src`, REPL helpers in `dev`, and automated tests in `test`. The `:dev` alias includes `dev` and `test` on the classpath; `:test` supplies the test runner and its test dependencies. The application uses Clojure 1.12.5, Ring/Jetty, Reitit and Stuart Sierra's Component.

The local `.clj-kondo/.cache/` directory contains generated analysis data and is ignored by Git.

## How the HTTP application is assembled

The request path is:

```text
Client
  → Jetty HTTP server (foo.server)
  → Ring / Reitit router (foo.http)
  → Application behaviour (foo.greeting)
```

Available endpoints:

| Method | Path | Response |
| --- | --- | --- |
| `GET` | `/hello` | `200`, plain-text `Welcome to Clojure, World!` |
| `GET` | `/health` | `200`, plain-text `OK` |

`/health` is a **liveness** endpoint: it establishes that the HTTP application responds. It does not assert that external dependencies are healthy.

`foo.http/app` is a Ring handler—a function of a request map returning a response map. It can be tested without opening a network port. Jetty is a separate, stateful adapter managed by Component.

`foo.system/new-system` builds a Component system map. The `:jetty` component implements `component/Lifecycle` to start and stop the Jetty server. The standalone `foo.core/-main` starts that system and waits on Jetty.

### REPL-driven development

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

## Testing and TDD

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

## Managing development with GitHub Projects and Issues

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

Start with a GitHub Issue whose description focuses on *what must be true*, not on internal implementation choices. A useful template is:

```markdown
## Outcome

What should someone be able to do, or what problem should be solved?

## Acceptance criteria

- Given ..., when ..., then ...
- ...

## Verification

- Fast behaviour/handler test
- Integration test where relevant
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

### 4. Develop and verify with the REPL

Start the REPL in one terminal:

```sh
make repl
```

Work through Red → Green → Refactor, calling `(check)` frequently. Use `(go)` to restart the Component-managed HTTP server after source changes.

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
Outcome + acceptance criteria (Issue)
              ↓
     TDD + implementation
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
- Describe acceptance criteria before implementation; link every meaningful change to an Issue when practical.
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
