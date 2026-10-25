# enduring_software

A small Clojure project for **REPL-driven development** and **test-driven development (TDD)**.

The aim is to keep a JVM running while making small changes to source code, reloading affected namespaces, and checking behaviour through automated tests.

## Prerequisites

- A JDK (this project has been developed with Java 21).
- The [Clojure CLI](https://clojure.org/guides/install_clojure), providing the `clojure` command.
- `make`.
- `rlwrap`, used by the `repl` Makefile target for a nicer terminal REPL.

The project uses Clojure **1.12.5**, `clojure.tools.namespace` for development-time code reloading, and `clojure.test` with the Cognitect test runner for tests.

## Project structure

```text
enduring_software/
├── dev/
│   └── user.clj             # Development REPL helpers: go and check
├── src/
│   └── foo/
│       ├── core.clj         # Application entry point
│       └── greeting.clj     # Greeting behaviour
├── test/
│   └── foo/
│       └── greeting_test.clj # Behaviour tests
├── deps.edn                 # Dependencies and :dev / :test aliases
├── Makefile                 # Common commands
└── README.md
```

`src` is application code; `dev` is development-only code; `test` contains automated tests. The `:dev` alias adds both `dev` and `test` to the classpath. The `:test` alias makes the tests available to the test runner.

## Commands

Run these from the project root:

| Command | What it does |
| --- | --- |
| `make repl` | Starts a persistent development REPL (`clojure -M:dev`, via `rlwrap`). |
| `make run` | Starts a fresh JVM and runs `foo.core/-main`. |
| `make test` | Runs the test suite in a fresh process using `clojure -X:test`. |

### Start a REPL

```sh
make repl
```

At the `user=>` prompt:

```clojure
(go)     ; Reload changed code and run foo.core/-main
(check)  ; Reload changed source/tests and run the greeting tests
```

**Keep the REPL running** while editing files. Save your changes and call `(go)` or `(check)` again; you do not need to restart the JVM for ordinary source changes.

## REPL development loop

1. Start the REPL with `make repl`.
2. Edit and save a source file, for example `src/foo/greeting.clj`.
3. Run `(go)` to execute the application, or `(check)` to run tests.
4. Inspect the result and repeat.

For example:

```clojure
user=> (go)
:reloading (foo.greeting foo.core)
Welcome to Clojure, World!
nil
```

The exact `:reloading` list depends on which files have changed. `clojure.tools.namespace` detects changes **when you call** `(go)` or `(check)`; it is not continuously reloading files in the background.

### Why multiple namespaces reload

`foo.core` depends on `foo.greeting`:

```text
foo.core
    └── depends on foo.greeting
```

If `foo.greeting` changes, `clojure.tools.namespace` can reload `foo.greeting` **and** its dependent `foo.core`, in dependency order. The `dev/user.clj` helpers keep this convenient:

```clojure
(ns user
  (:require [clojure.tools.namespace.repl :as tn]
            [clojure.test :as t]))

(tn/set-refresh-dirs "src" "test")

(defn run-tests []
  (t/run-tests 'foo.greeting-test))

(defn check []
  (tn/refresh :after 'user/run-tests))

(defn go []
  (tn/refresh :after 'foo.core/-main))
```

The `user` namespace lives in `dev`, outside the directories refreshed by `tools.namespace`, so these REPL helper functions remain available. The `:after` option runs the designated function after a successful refresh.

## TDD: Red → Green → Refactor

Use automated tests to drive one small behaviour change at a time.

**1. Red — describe a behaviour with a failing test.**

For example, in `test/foo/greeting_test.clj`:

```clojure
(deftest greet-whitespace-name-test
  (is (= "Welcome to Clojure, friend!"
         (greeting/greet "   "))))
```

Run `(check)`. Confirm the test fails **for the expected reason**, rather than because of a syntax or setup error.

**2. Green — make the smallest useful change that passes the test.**

In `src/foo/greeting.clj`:

```clojure
(ns foo.greeting
  (:require [clojure.string :as str]))

(defn greet [name]
  (str "Welcome to Clojure, "
       (if (str/blank? name) "friend" name)
       "!"))
```

Run `(check)` again. A passing run reports zero failures and errors, for example:

```text
Ran 3 tests containing 3 assertions.
0 failures, 0 errors.
{:test 3, :pass 3, :fail 0, :error 0, :type :summary}
```

**3. Refactor — improve the structure, keeping behaviour unchanged.**

Simplify code only when there is a concrete improvement to make. Keep the tests green. If the implementation is already clear, no refactoring is needed.

Then repeat the cycle for the next behaviour.

### Behaviour covered by the example tests

| Input to `greet` | Expected result |
| --- | --- |
| `"World"` | `"Welcome to Clojure, World!"` |
| `""` | `"Welcome to Clojure, friend!"` |
| `"   "` | `"Welcome to Clojure, friend!"` |

`clojure.string/blank?` also treats `nil` as blank. That is a consequence of the chosen implementation, **not yet a separately agreed requirement**.

## Git workflow: trunk-based development

This project uses **trunk-based development**: `main` is the trunk and the single integration branch. Keep it green, work in small increments, and integrate changes frequently. Avoid long-lived `develop`, feature, or release branches.

The everyday loop connects our TDD and REPL workflow to Git:

```text
Choose one small behaviour
        ↓
Red → Green → Refactor in the REPL: (check)
        ↓
Review the diff and run: make test
        ↓
Commit the working increment
        ↓
Integrate into main promptly
```

### Option A — Short-lived branch and pull request (recommended when using a remote)

A short-lived branch is compatible with trunk-based development **when it is merged back quickly** (ideally the same day, usually within a day or two). A pull request is a review and integration mechanism, not a place to accumulate days of work.

**1. Start from the latest trunk** (the pull command assumes a configured remote/tracking branch):

```sh
git switch main
git pull --ff-only
git switch -c feat/whitespace-greeting
```

**2. Develop one small behaviour.** Keep `make repl` running in a separate terminal. Work in Red → Green → Refactor cycles, calling `(check)` frequently. When the increment is green:

```sh
make test
git status
git diff
```

**3. Commit only the intended files.** Prefer focused commits with meaningful messages:

```sh
git add src/foo/greeting.clj test/foo/greeting_test.clj
git commit -m "Handle whitespace-only greeting names"
```

For several unrelated edits, use `git add -p` to stage selectively. Aim for small, self-contained, passing commits rather than large batches of changes.

**4. Integrate promptly.** With a remote such as GitHub:

```sh
git push -u origin feat/whitespace-greeting
```

Open a small pull request into `main`. Have automated checks run `make test` before merging. Review it and merge as soon as it's ready. Once merged, update your local trunk:

```sh
git switch main
git pull --ff-only
```

Delete the completed branch after confirming it has been merged. Note that squash-merging creates a new commit, so `git branch -d` may not recognise the old branch as merged; check the PR's status before forcing deletion.

### Option B — Working directly on `main` (simple solo-project workflow)

For a solo project, you can skip feature branches and commit directly on `main`, provided **every commit leaves the trunk working**:

```sh
# After a Red → Green → Refactor cycle
make test
git diff
git add src/foo/greeting.clj test/foo/greeting_test.clj
git commit -m "Handle whitespace-only greeting names"
# If you have a remote:
git push origin main
```

If collaborators join, favour short-lived branches, pull requests, and required automated checks to protect the shared trunk.

### Working agreements

- **`main` stays green and releasable.** Run `(check)` as you work and `make test` before integration. A successful local run is useful; CI should independently verify each proposed change.
- **Integrate small and often.** Split substantial work into independently useful increments. Don't keep a feature branch open while building a large feature.
- **Hide unfinished functionality.** Use a feature flag or another safe boundary if incomplete code must be integrated; do not leave broken behaviour exposed on `main`.
- **Commit code and tests together.** A red test is a deliberate *local* TDD step, not a reason to leave the shared trunk failing.
- **Review diffs before committing.** Don't commit REPL caches, compiled artifacts, credentials, or machine-local files. Maintain a suitable `.gitignore`.
- **Prefer a linear, understandable history.** Keep branches up to date with `main`; avoid force-pushing the shared trunk. Rebase only your own unpublished or safely coordinated branch work.

### Continuous Integration (CI)

GitHub Actions automatically runs the Clojure test suite whenever:

- A pull request targets `main`.
- A commit is pushed to `main`.

The workflow is defined in `.github/workflows/ci.yml`. It runs on Ubuntu with Java 21 and the Clojure CLI, then executes the same verification command used locally:

```sh
make test
```

#### Keeping the trunk green

Our working agreement is that `main` must always be working. Before integrating changes:

1. Complete a Red → Green → Refactor cycle.
2. Run `(check)` from the REPL.
3. Run `make test` in a fresh JVM.
4. Commit and push the change on a short-lived branch.
5. Verify the GitHub Actions **Test** job passes.
6. Merge the pull request into `main` promptly.

After merging the CI workflow, configure a GitHub branch ruleset that requires the **Test** status check before merging. The workflow itself does not enforce branch protection.

A passing CI run demonstrates that the automated tests pass; it does not guarantee the application is free of defects.

## When to use each command

- **During development:** keep `make repl` open and use `(check)` frequently, with `(go)` when you want to exercise the application.
- **Before committing:** run `make test` to verify the suite in a clean process.
- **To run the application independently:** use `make run`.
- **After changing `deps.edn`:** restart the REPL so its classpath reflects the new dependencies.

## A few conventions

- Keep application behaviour in `src`, not in `dev/user.clj`.
- Use `dev/user.clj` for short development conveniences, such as `(go)` and `(check)`.
- Place tests under `test` with a matching namespace: `foo.greeting` → `foo.greeting-test` in `test/foo/greeting_test.clj`.
- Test **observable behaviour**, not implementation details, so internal refactoring doesn't invalidate otherwise-correct tests.
- Remember that a long-running REPL retains state. Reloading definitions is not the same as restarting the JVM; use a fresh process when you need a clean-slate check.
