# Scroll iteration execution plan

Approved target: v1.1.9-dev.20260915.1. This execution breakdown implements the
user-approved full plan in the conversation; it does not replace its requirements.
Keep MC 1.21.11, Loader 0.19.5, API 0.141.6+1.21.11. Preserve historical artifacts,
other modules, and the actual user client. No publishing.

## Task 1: Nonlinear calculation and planning domain

Own internal/scroll/domain and focused domain tests only. Read existing solver
and retain legacy API compatibility until integration. Add real nonlinear
multi-batch planning, not an unused helper.

F(M)=64+0.714296*(M-64)-0.00271535*(M-64)^2.
Contribution a*(F(U+n)-F(U)); F(0) must retain the polynomial intercept.
No input means zero contribution. Use high precision throughout validation and
search, round HALF_UP only for final integer display prefixed with 约.
Reject invalid input and expose negative/extreme contributions; never clamp.
Material theoretical values remain immutable. Shared usage across ordered batches.
Expose immutable evaluated plans with theoretical/effective elements, before/after
usage, feasibility, impurity/excess, extra-material/efficiency status.

Inputs: recipe, excluded materials, desired crafts, current usage, budget.
Retain recipe constraints and conservative 320 total inputs INCLUDING main item.
Seek fixed one-craft vectors and their largest feasible repeat capacity without
extra ingredients. This defines no-extra-material decay, NOT M<=64.
Use bounded heuristic search; favor fewer efficient batches, otherwise best
feasible low-loss fallback. Order closest-to-decay first (descending maximum final
M); re-evaluate shared usage after ordering, never return invalid sorted schedules.
FAST <=1s, BALANCED <=3s, EXTREME <=9s: monotonic deadline checked within expansion,
sorting/planning, return best-so-far; cancellation distinct from timeout.
No artificial delay and no false proof of unsatisfiability after beam pruning.
Manual evaluation changes counts/material totals without searching again.

TDD: independent numeric constants, telescoping, zero input, multiple elements,
negative domain, target 1/4/6/10/64, exclusions, shared usage, order, input cap,
budget and cancellation, no early rounding. Run focused tests before full suite.
Report public interfaces for later controller/UI integration. Do not dispatch
subagents, modify controller/UI/storage/data resources, or publish artifacts.

## Task 2: Durable usage and preset transactions

Implement UUID/Asia-Shanghai-date separated atomic JSON storage in
config/simmc-tool-set/scroll-usage.json. Preserve history across days; immutable
snapshots, idempotent confirmation, audit edits, transactional failure handling.
Separate temporary simulation from confirmed usage. Presets store vectors/order/
quantities, no historical M; names <=20 Unicode characters; rename/delete.
Tests: restart, player/date separation, duplicate clicks, stale commits, failed
writes, corrupt files (never silently erase), edits and history.

Persist usage records with transaction ID, player UUID, Beijing date, timestamp,
recipe, ordered plan inputs, requested crafts, actual material usage, before/after
M, auto/manual mode and modified flag. Save whole auto combination as one atomic
record; manual per-plan saves atomic individually. Revalidate expected date and
usage revision so a result made before another save/edit/midnight cannot be
committed silently. Duplicate transaction IDs must not double-count after restart.
Inject Clock in tests. Validate nonnegative integer counts and overflow; do not
silently repair corrupted data. Write temporary sibling then atomic replace;
publish in-memory state only after durable success. Existing unscoped prototype
data cannot be attributed to a UUID: preserve and expose a warning, do not adopt.
Read-only/error mode must leave file intact rather than silently resetting it.
History should remain queryable across dates. No random UUID placeholder for a
real player. Controller integration follows in Task 3, retain temporary adapters
only if needed to compile old call sites, but do not permit unsafe unscoped writes.
Manual edits require explicit acknowledgement and record old/new totals; store
must support their atomic audit and UI provides actual warning/checkbox.
Presets may remain in a sibling file if transactions that update M/history remain
atomic; preserve legacy presets without trusting fabricated metrics, evaluate
again with current data before use.

## Task 3: Controller and native screens

Integrate domain planner and stores; new request cancels old, stale result cannot
publish. Auto default freezes plan. Manual controls disabled in auto. Per-plan
quantity editing without automatic solver invocation. Reducing plan count needs
confirmation, remove tail and redistribute equally; more than default retains
default with warning. Reset unsaved state only. Per-plan and whole-plan idempotent
save. History scrolling, current/temporary M, audited editing warning plus checkbox
我确保明白自己在做什么. Preset save from history, rename/delete, evaluate at current M,
specific failure warning plus second confirmation before recording actual use.
Show all fields required by approved UI specification, budget status, local-model
disclaimer. Fix toolbar responsive layout and retain existing filters/main toggle.

Confirmed rows stay locked across reset/edit and must not be counted again in
temporary usage. Recalculate an unsaved edit using confirmed M plus preceding
unsaved rows, never adding saved rows a second time. Validate total assigned
quantity against the requested target; show both totals even when invalid.
Handle player changes, midnight and M edits by invalidating stale result/commit
contexts. Store failed writes must leave confirm retryable with same transaction
ID and unchanged M; successful saves disable their button. Creating a fresh
calculation or a fresh preset-use preview creates a new explicit transaction.
Use session/connected player UUID, never a generated UUID for identity.
Move the new files into the approved config directory; existing legacy settings
may continue being read for preference compatibility. Remove obsolete advice
claiming reset timing is unknown or that decay starts at M=64.
Tests must exercise UI controls in the isolated client at multiple window sizes,
including automatic disabled manual controls, manual counts/reset, confirmation,
history entry, M acknowledgement checkbox, presets and parent/Esc restoration.

## Task 4: Materials, verification and delivery

Read original Excel Sheet1, import deterministic data and provenance, verify
mapping against project IDs. Review all changes. Full tests, isolated client
interaction tests, build, diff check and JAR audit. Preserve existing incomplete
same-version candidate under a unique archive path before final delivery.
Write final JAR/checksum to workspace release, no publication or user-client edits.
Synchronize reviewed durable memory with explicit standalone memory commit.
