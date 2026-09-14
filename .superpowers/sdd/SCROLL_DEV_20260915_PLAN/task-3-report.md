# Task 3 report — controller and native screens

Date: 2026-09-15

## Delivered

- `ArcaneController` now evaluates `ScrollPlanningRequest` through
  `DecayPlanner`, carries the immutable `PlanningResult` to the UI, cancels
  superseded generations, and refuses to publish stale callbacks.
- Planning reads the connected player's UUID-scoped Beijing-date usage snapshot.
  No generated UUID is used for identity.
- Confirmation builds a `UsageCommitRequest` with a stable transaction ID,
  expected revision/date, ordered batches, before/after M, and actual material
  consumption. `ScrollUsageStore.commit` supplies idempotency and stale-write
  protection.
- The calculator uses the new evaluated multi-batch result, displays planned
  versus requested crafts, M transition, impurity/excess, budget status, and
  unrounded-validation disclaimer. The main-material toggle remains display
  only, while the planner's input cap still includes the core item.
- Confirmation requires a second click, preserving a retryable transaction on
  storage failure. A successful commit clears the preview.
- Usage history now shows UUID/date-separated history and provides an explicitly
  acknowledged, audited M editor. Esc/parent restoration and scrolling are
  retained.
- Config paths use `config/simmc-tool-set/scroll-settings.json`,
  `scroll-usage.json`, and `scroll-presets.json`.
- Preset rename now atomically replaces the old name instead of leaving a
  duplicate; controller exposes rename/delete/save operations.
- Help text no longer claims that decay reset timing is unknown or that decay
  starts at M=64; it explains shared-M planning, idempotent records, and the
  local-model limitation.

## Verification

- `git diff --check`: passed for the task changes.
- Focused Gradle verification could not run in this environment: the wrapper
  attempted network download, and the cached Gradle 9.5 executable detected
  the host's configured JVM 8. A JDK 17+ run is still required.

## Limitations / follow-up

- Full preset-use preview controls and isolated-client interaction tests remain
  for the parent integration pass.
- The UI intentionally keeps the existing recipe/exclusion controls and
  parent/Esc navigation; runtime screen evidence is still needed.
