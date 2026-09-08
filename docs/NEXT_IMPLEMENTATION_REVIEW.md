# v1.1.8 Implementation and Acceptance Ledger

Plan: `C:/Users/Murpho/.codex/plans/019feca7-5a95-70b0-b40b-0303d7072edd/01a08286-fe5f-7461-9af0-988442757b1f/PLAN.md`

Baseline: v1.1.7 `fa3ccdc`. Status: implementation in progress; not a release.

## Decisions and Boundaries

- Implement map removal and retained-feature fixes on Minecraft 1.21.8 first, then migrate to 1.21.11 / Loader 0.19.5 / Fabric API 0.141.6+1.21.11.
- Use a new local branch and candidate version 1.1.8. Preserve previous artifacts, tags and release branches.
- The old linked worktree could not be edited by the sandbox. Continue in the independent `toolset-next-worktree` clone; preserve the old worktree and its untracked screenshots.
- Only the isolated repository `run/` client is allowed. Build evidence is not target-server acceptance.
- Do not delete user map data or Xaero installations. Remove only this version's built-in map implementation.

## Ownership and Preflight

| Task | Owner | Shared boundary / decision | Status |
| --- | --- | --- | --- |
| A: remove map | remove_map | Map, main entrypoint, navigation, keys and dependencies. Reviewed independently by map_review. | Baseline complete |
| B: Arcane and diagnostic | arcane_diag | Mana/wand lifecycle and UTC+8. Reviewed by arcane_review; direct packet checks added to native client tests. | Baseline complete |
| C: scroll | scroll_accessory_brewing | Bounded batches, material totals, footer layout. Reviewed independently. Server capacity remains provisional. | Baseline complete |
| D: accessory and brewing | scroll_accessory_brewing | Session confirmations and conservative brewing. map_review added exact raw-tooltip guard. | Baseline complete |
| Integration and upgrade | main thread | Shared UI, world-change reset, baseline regression and target API migration. | Upgrade in progress |

The four implementation areas are independent after the navigation ownership is fixed. C and D share one worker due concurrency limits. The plan's historical "planning only" sentence does not override the owner's explicit instruction to implement and build; publication remains prohibited.

## Acceptance

| Gate | Evidence | Status |
| --- | --- | --- |
| Map code, dependencies, mixins, UI, keys removed | Commit 9f2e346; no production map/Xaero references, map tests removed. Historical docs retained. | Passed source gate |
| 1.21.8 unit tests and complete build without Xaero | baseline-reviewed-build.log: 69/69, complete build successful | Passed |
| Mana/wand lifecycle, timezone, scroll capacity, review persistence, conservative cooking tests | 69 unit tests plus baseline-reviewed-client.log native client test | Passed local gates |
| 1.21.11 unit tests and complete build | Pending | Pending |
| Client-only metadata, no map classes/dependencies, version and checksum | Pending | Pending |
| Isolated client UI/input/retained-module startup | Pending | Needs verification |
| Target server and external Simes runtime behavior | Requires actual runtime evidence | Needs verification |

## Baseline Evidence and Limits

Evidence directory: workspace `outputs/v1.1.8-verification/`. Native client tests use only repository `run/client-gametest`, with 1280x720 and 854x480 windows at GUI scale 2. All retained navigation panels, scroll/accessory screens, Simes settings, four-HUD layout and hotkeys were opened. Widget bounds, Esc/parent restoration, local world entry and native inventory were checked. Diagnostic scrolling was exercised and screenshots retained.

The actual Mana packet handler was called in a local world with a temporary synthetic target-server entry, restored immediately afterward: hidden Mana packets update state, same-slot wand replacement clears old Mana, no-wand packets pass through, real cooldown entries survive wand changes, and the Fabric world-change event clears session state. This is a synthetic client test, not a connection to play.simmc.cn.

- Scroll quantities 1/4/6/10/64 and input-cap boundaries are tested. The 320-input limit and main-material inclusion are conservative client assumptions, not newly verified server mechanics; element excess remains bounded by the existing search rule.
- Accessory confirmation reuse is session-scoped and requires unchanged parsed fingerprint, exact raw tooltip lines and matching source. Changed malformed lines require review even when normalized values match.
- Valid fermentation records are not expired merely because the player looks away. Empty stale associations expire; world change clears coordinate-scoped data.
- The old map disconnect/resource bug is removed from the new product with the module, not backported or declared fixed in v1.1.7.

## Dependency Evidence

- Fabric metadata read on 2026-09-09 lists `1.21.11+build.6` as stable Yarn mappings for Minecraft 1.21.11.
- Existing build uses Java 21 and Loom 1.17.17. Keep Loom if it supports the target; do not change plugins merely to change version numbers.
