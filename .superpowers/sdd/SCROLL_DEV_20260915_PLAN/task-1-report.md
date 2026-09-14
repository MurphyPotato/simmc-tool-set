# Task 1 report — nonlinear scroll domain

Date: 2026-09-14 (Asia/Hong_Kong; developer-authoritative project date)

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
existing beam/candidate heuristics. The fix round adds explicit exclusion
filtering, evolving-usage candidate expansion, actual material-map input
accounting, unrounded impurity and target-excess checks, and final-portfolio
ordering with sequential re-evaluation. A one-material fallback is evaluated
before legacy expansion so a timeout can retain a useful best-so-far result.
Controller integration should use the typed evaluator and preserve the ordered
batch list; it should not infer nonlinear values from rounded display strings.

## Fix round evidence

The reviewer-requested incomplete-target, explicit-exclusion, and evolving
usage paths are covered by the focused tests. The complete gate passes
`verifyUnitTests` 83/83. Parent-owned material-source coverage is included in
that run but remains outside this task's commit.

Fix-round regression evidence:

- `ScrollDomainPlannerTest.nonlinearImpurityIsCheckedAfterDecayAtCurrentUsage`
  covers the sole material `(金=2, 木=8)` at `U=20`; exact effective wood is
  below 8 although display HALF_UP may show 8, and the plan is feasible.
- `ScrollDomainPlannerTest.plannerRespectsExclusionsInputCapBudgetAndCancellation`
  passes an explicit excluded-material set and confirms it is absent.
- `ScrollDomainPlannerTest.incompleteManualTargetIsNotReportedFeasible`
  confirms insufficient effective target output is not complete.
- `ScrollDomainPlannerTest.highUsageExpandsOneVectorToTwoRawInputs` covers
  `M=100`, target 金=2, and a sole 金=2 material: one raw input is below
  target while two raw inputs are sufficient, so nonlinear expansion selects
  the two-input vector.

Command and result:

`.\gradlew.bat --no-daemon --offline verifyUnitTests` with the pinned JDK 21:
86 tests found, 86 started, 86 successful, 0 failed.
