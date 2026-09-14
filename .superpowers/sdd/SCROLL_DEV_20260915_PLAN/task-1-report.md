# Task 1 report — nonlinear scroll domain

Date: 2026-09-15 (Asia/Hong_Kong)

## Scope

Implemented only the nonlinear scroll-domain surface and focused tests. Existing
controller, screens, storage, preset DTO, imported resource, and unrelated
work-in-progress files were left unstaged.

## Public interfaces

- `ScrollPlanningRequest`: immutable recipe/materials/desired-craft/current-usage,
  material-budget, `SearchBudget`, and cancellation input.
- `DecayPlanner.plan(request)`: bounded multi-batch search returning
  `PlanningResult` with `PlanningStatus` and best plan.
- `DecayPlanner.evaluate(...)`: deterministic manual schedule evaluation; it
  never invokes the solver.
- `EvaluatedPlan`: immutable ordered batch results, theoretical/effective
  elements, before/after usage, feasibility, impurity/excess, extra-material
  map, and efficiency.
- `EvaluatedBatch`: immutable per-batch transition and metrics.
- Legacy `DecayPlanner.simulate(...)` remains available for current integration.
- `MaterialDecay.cumulativeFactor(...)` and `incremental(...)` now have
  BigDecimal overloads; double overloads remain source-compatible.

Main-material semantics remain the established baseline: it counts as one input
per craft for the 320-input cap, while its absent data row contributes no
elements or usage transition.

## RED/GREEN evidence

The focused test initially reached RED due to a missing convenience constructor
for a request with a material budget. After adding that constructor, the focused
domain tests passed. The mandated complete gate then passed:

`verifyUnitTests`: 82/82 successful, 0 failures, JDK 21 from
`AAAsimmc aoshu jisuanqi/aoshu-scroll-calculator-v1.1.1/tools/android-env/jdk-21`.

Focused coverage includes independent polynomial constants, telescoping
increments, zero input, invalid count rejection, un-clamped polynomial domain,
shared usage across ordered batches, exclusions, input cap, budget, and distinct
cancellation behavior.

## Limitations / concerns

The legacy `ArcaneSolver` remains the fixed-vector generator and retains its
existing beam/candidate heuristics. `DecayPlanner` checks its own deadline at
candidate expansion, ordering, and capacity evaluation, and returns a
best-so-far plan once evaluation has begun; a timeout raised inside the legacy
solver before a candidate is materialized necessarily has an empty candidate
list. Controller integration should use the typed evaluator and preserve the
ordered batch list; it should not infer nonlinear values from rounded display
strings.
