# enduring_software

A small Clojure project for **REPL-driven development** and **test-driven development (TDD)**.

The aim is to keep a JVM running while making small changes to source code, reloading affected namespaces, and checking behaviour through automated tests.

## Prerequisites

- A JDK (this project has been developed with Java 21).
- The [Clojure CLI](https://clojure.org/guides/install_clojure), providing the `clojure` command.
- `make`.
- `rlwrap`, used by the `repl` Makefile target for a nicer terminal REPL.
- Git and the [GitHub CLI (`gh`)](https://cli.github.com/) for contributing changes. Authenticate once with `gh auth login`; verify with `gh auth status`.

The project uses Clojure **1.12.5**, `clojure.tools.namespace` for development-time code reloading, and `clojure.test` with the Cognitect test runner for tests.

## Project structure

```text
enduring_software/
├── .github/
│   └── workflows/
│       └── ci.yml           # GitHub Actions: tests on PRs and pushes to main
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

## Git workflow: trunk-based development with `gh`

We use **trunk-based development**. `main` is the protected trunk: integrate **small, tested changes frequently** via short-lived branches and pull requests. Avoid long-lived `develop` or feature branches. Each merged increment should leave `main` working.

The GitHub ruleset [Protect main](https://github.com/rorycawley/enduring_software/rules/25802970) is active and requires:

- A pull request before changes enter `main` (zero approving reviews required for this solo project).
- The GitHub Actions **Test** status check to pass.
- The branch to be up to date with `main` before merging.
- No force-pushes, no deletion of `main`, and no bypass exceptions.

**Direct pushes to `main` are not our workflow.** Use a short-lived branch and a pull request, even for documentation changes.

### Everyday workflow (copy/paste reference)

```text
Update main → create short-lived branch
                       ↓
      Red → Green → Refactor in the REPL
                       ↓
             (check) → make test
                       ↓
              Review and commit
                       ↓
               Push the branch
                       ↓
                gh pr create
                       ↓
             gh pr checks --watch
                       ↓
          gh pr merge --squash --delete-branch
                       ↓
              Update local main
```

#### 1. Start with the latest `main`

From the repository root, with your local changes committed or safely set aside:

```sh
git switch main
git pull --ff-only
git switch -c feat/describe-small-change
```

Use a short, descriptive branch name: `feat/…`, `fix/…`, `docs/…`, or `ci/…`. Aim to merge within a day when practical. A pull request is an integration checkpoint, not a place to accumulate a large feature.

#### 2. Develop one behaviour at a time

In one terminal, keep the REPL open:

```sh
make repl
```

In the REPL, follow **Red → Green → Refactor**. Run `(check)` after edits and `(go)` when you want to exercise the application:

```clojure
(check)  ; Reload changed namespaces and run tests
(go)     ; Reload changed namespaces and run the application
```

A failing test during the Red step is expected **locally**. Before integration, make all tests pass and keep code and tests together in the change.

#### 3. Verify and commit the small increment

In another terminal:

```sh
make test
git diff --check
git status
git diff
```

Inspect what changed, stage only relevant files, then commit:

```sh
git add src/foo/greeting.clj test/foo/greeting_test.clj
git commit -m "Handle whitespace-only greeting names"
```

Use the actual files and message for your change. For selective staging, use `git add -p`. Avoid committing local caches, generated files or credentials.

#### 4. Push and open a pull request with `gh`

```sh
git push -u origin feat/describe-small-change

gh pr create \
  --base main \
  --title "Describe the small change" \
  --body "Explain the behaviour changed and how it was verified."
```

`gh pr create` uses the checked-out branch as the PR's head. To inspect it in the browser, use `gh pr view --web`.

#### 5. Wait for CI and investigate failures

```sh
gh pr checks --watch
```

GitHub Actions runs `make test` in a fresh Ubuntu environment. Only merge when the **CI / Test** check succeeds. If it fails, fix the issue on the same branch, rerun local tests, commit and push again, then recheck. If GitHub says the branch is out of date, update it from `main` and rerun the checks.

#### 6. Squash merge and delete the completed branch

```sh
gh pr merge --squash --delete-branch
```

The ruleset prevents merging without the required passing check. Squash merge leaves one clear commit for the increment on `main`; `--delete-branch` removes the completed branch from GitHub and, when possible, locally.

#### 7. Return to an up-to-date trunk

```sh
git switch main
git pull --ff-only
git status
```

`gh pr merge --delete-branch` may already switch you to `main`, but explicitly running these commands is safe. Start the next change from this updated trunk. To see recent runs on `main`:

```sh
gh run list --branch main --limit 5
```

### Continuous Integration (CI)

The workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs for:

- Pull requests targeting `main`.
- Pushes to `main` (including merges).

The **CI / Test** job checks out the code, installs Java 21 and the Clojure CLI, then runs the same command as local verification:

```sh
make test
```

The branch ruleset requires its **`Test`** status check before merging. The job name is `Test`; `CI` is the workflow name. You can review runs in the [Actions tab](https://github.com/rorycawley/enduring_software/actions) or with `gh run list`.

A green CI check means the automated tests passed for that commit; it does **not** prove all possible behaviour is correct. If a push-triggered CI run on `main` fails, treat restoring a green trunk as a priority.

### Working agreements

- **Keep `main` releasable.** No deliberately failing tests or broken features on trunk.
- **Integrate frequently.** Prefer small, independently verifiable increments; split large features into safe slices.
- **Hide incomplete functionality** behind feature flags or other safe boundaries if necessary.
- **Commit implementation and tests together.** Red is a local TDD phase, not a state to merge.
- **Use PRs and CI for every change.** The same rules apply to code, tests, documentation and configuration.
- **Review changes before committing.** Keep secrets and machine-local files out of Git; maintain `.gitignore`.

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
