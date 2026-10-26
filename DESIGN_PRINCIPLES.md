# Design principles

> **Software is built from today's understanding of reality, but it must survive tomorrow's reality.**

A car, a house, and a road embody design decisions that are often expensive to reverse once built. Software is different in an important respect: we can change its behaviour and structure after delivery. Yet software too can become costly to change when we encode a temporary understanding of the world as if it were permanent.

Every design reflects what we currently know. Business needs evolve, laws and policies change, and new evidence reveals that some of our assumptions were incomplete or wrong. We cannot design for every future circumstance. We can, however, avoid making change unnecessarily difficult.

## Two foundational design principles

### 1. Design for Ease of Change

**Make software straightforward to understand, modify, test and adapt as our understanding of reality evolves.**

The goal is not to make *every conceivable* change trivial. It is to keep a change as local and understandable as its actual consequences allow, without avoidable coupling, hidden assumptions, or speculative abstractions.

### 2. Design for a High-Performance User Experience

**Ensure that software remains fast, responsive and predictable for the people who depend on it.**

The measure of performance is the experience of accomplishing a task, not merely the execution time of an internal function or a fast HTTP response.

## Our guiding question

> **How do we design software that can adapt to a world we do not yet fully understand, while continuing to perform exceptionally well for its users?**

These principles guide our trade-offs; they do not replace correctness, security, privacy, reliability, accessibility, or other requirements of a particular system.

## Deliberately limited objectives

**We deliberately constrain our architectural objectives to two: ease of change and a high-performance user experience.**

This focus is intentional. A longer list of equally weighted goals makes trade-offs less clear. Every additional abstraction, pattern, or technology must justify the complexity it introduces.

We do not pursue architectural sophistication for its own sake. We choose mechanisms when evidence and sound reasoning show they help achieve one or both objectives **while satisfying the system's essential requirements**. Neither a familiar pattern nor a hypothetical future need is sufficient justification on its own.

**We constrain what we optimise for, not what we are willing to learn.**

We remain open to new evidence about the domain, users, and technology, and we revisit choices when the assumptions supporting them change. The constraint focuses our decisions; it must never freeze our understanding of reality.

## Designing for ease of change

We favour design choices that make it possible to reason about a change and understand its consequences:

- **Understand the domain before freezing a model.** Distinguish observed facts from assumptions; distinguish domain laws and invariants from changeable business policies. Record the conditions under which our understanding holds, and look for counterexamples. Change the model when the evidence changes.
- **Seek high cohesion and low coupling.** Put related rules together and keep unrelated concerns independent. Boundaries should follow meaningful responsibilities and reasons to change, not patterns chosen in advance.
- **Make information and data flow explicit.** Prefer plain, inspectable data that preserves important distinctions. Make inputs, outputs, dependencies, and state transitions visible rather than relying on hidden mutable state or implicit context.
- **Prefer pure functions for decisions and transformations.** A functional core makes rules easy to explore in the REPL, compose, test, and revise. Keep effects such as network calls, persistence, messaging, and time at explicit boundaries where practical.
- **Build behaviour through simple composition.** Prefer small coherent functions and clear interfaces. Add abstractions when they remove real duplication or isolate genuine variation, not to anticipate every possible future requirement.
- **Test promises, not incidental structure.** Behavioural acceptance tests should remain useful when the implementation is replaced. Fast rule-level tests and real-boundary integration tests provide complementary evidence.

**Evidence of success:** A change to a greeting rule should primarily affect the rule and its relevant tests, not require editing Jetty startup or persistence configuration. Replacing Jetty should change the server adapter and wiring, not the greeting logic. These are examples of *change locality*, not guarantees that every change will stay within one file.

## Designing for a high-performance user experience

Start from what a person does and experiences:

- **Define the observable start and finish.** Measure from the user's action to meaningful, truthful feedback and to the visible completion of the task. A successful API response is not necessarily a completed user task.
- **Specify budgets before optimising.** The initial targets in [WORKFLOW.md](WORKFLOW.md) are meaningful feedback within **100 ms at p95** and ordinary visible task completion within **1 second at p95**, under agreed representative conditions. These are product goals, not claims that they are already met.
- **Keep long-running work honest.** Some operations cannot complete immediately. Show a useful pending or unknown state, provide progress or status where appropriate, and make failures and recovery understandable; never use fast acknowledgement as a substitute for completed work.
- **Measure real boundaries and observe variation.** Record workload, environment, p95, p99, and errors. Investigate slow tails and regressions rather than relying on averages or unrepeatable microbenchmarks.
- **Optimise with evidence.** A faster implementation is not necessarily better if its additional complexity makes change substantially harder. Prefer simple designs until measurement demonstrates that a more complex one is justified.

Our current `make perf` and `make perf-check` commands provide **interim HTTP-only evidence**. They do not prove the browser-visible response goals. Once a UI exists, we will measure action-to-visible-feedback and action-to-completion directly.

## Making design decisions

For any meaningful change, ask:

1. **What changed in our understanding?** Is it a new fact, a domain law, a business policy, or an implementation constraint?
2. **Where should the change belong?** Which existing responsibilities are affected, and which should remain untouched?
3. **Can we follow the data?** Are inputs, decisions, state and effects explicit enough to understand and test?
4. **What will users experience?** Does the change preserve correct behaviour and agreed responsiveness, including errors and waiting states?
5. **What evidence supports the choice?** What tests or measurements will demonstrate success, and what trade-off deserves an Architecture Decision Record (ADR)?

Use these questions in design discussion and pull-request review. They are prompts for judgement, **not a mandatory collection of layers, patterns, or documents**. Revisit a decision when its assumptions no longer hold.

## Relationship to our workflow

[WORKFLOW.md](WORKFLOW.md) explains how we turn these goals into executable behavioural and performance acceptance, work through ATDD and TDD, and deliver changes through GitHub Issues, pull requests, and CI. This document explains **why and how we judge the design itself**.
