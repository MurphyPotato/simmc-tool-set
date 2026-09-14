# Task 2 storage API

Implemented the bounded storage layer in the scroll worktree.

## Public API

`ScrollUsageStore`

- `new ScrollUsageStore(Path)` and `new ScrollUsageStore(Path, Clock)` (the
  latter is used by deterministic tests).
- `snapshot(UUID)` returns the current Asia/Shanghai-date ledger, revision,
  current M, totals, read-only state, and warning.
- `history(UUID)` returns immutable historical `UsageRecord` snapshots across
  all dates; `audits(UUID)` returns manual-edit audit entries.
- `commit(UsageCommitRequest)` validates player UUID, expected Beijing date,
  expected revision, nonnegative values, and transaction identity. It stores
  one complete immutable record and returns `CommitResult`; replaying a
  transaction ID is idempotent.
- `edit(UUID, LocalDate, long, Map<String,Integer>, int, boolean, String)`
  requires explicit acknowledgement, checks date/revision, and atomically
  stores old/new totals in an audit entry.
- `isReadOnly()` and `warning()` expose corrupt/unreadable-file error mode.
- Existing `count`, no-argument `snapshot`, `add`, and `set` adapters remain
  for old call sites; unscoped writes throw and never create a fake UUID.

`PresetStore`

- `list`, `save`, `rename`, and `delete` operate on immutable `PresetPlan`
  snapshots.
- `isReadOnly()` and `warning()` expose corrupt-file error mode.
- Preset names are limited to 20 Unicode code points; presets contain only
  recipe, ordered vectors, and quantities, never historical M/usage metrics.

All writes use a temporary sibling followed by atomic replacement when
supported. In-memory state changes only after replacement succeeds. Corrupt
files are preserved and put the store into read-only mode.

## Verification

`git diff --check` passes. `verifyUnitTests` could not start because the local
Gradle invocation could not download the Minecraft/Loom dependency metadata
(`Failed download after 3 attempts`); no source or artifact was published.
