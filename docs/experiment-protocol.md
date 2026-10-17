# Experiment protocol (companion Unity lab)

Where the override → document → rollback experiment actually belongs: a Unity
project you own, with an instrumented resolver. Reproduce the catalog-driven
resolution pattern, then document it.

## Setup

- Unity 2022 LTS, any render pipeline.
- `CatalogStore` — JSON mapping logical ids (e.g. `skin.knight.tier3`) to
  paths + variants.
- `ResolutionProbe` — wraps every resolve call and logs:
  `{id, requestedPath, orderedCandidateList, chosenPath, fallbackStep, elapsedMs, contentHash}`.
- `OverrideRule` — in-memory `id → alternate path` rules with TTL, applied by
  *your* resolver. This is the reversible override, in a place where it is
  legitimate.
- Log entries use the `JournalEntry` schema from `docs/journal-schema.md`.

## Protocol

| step | action                              | observation                                  |
|------|-------------------------------------|----------------------------------------------|
| 1    | resolve full catalog, capture hashes | canonical resolution order per id           |
| 2    | inject override rule for one id     | which fallback position wins, what is skipped |
| 3    | capture journal, export table       | before/after decision tree for the same id   |
| 4    | revert rule, re-resolve             | baseline hashes must match exactly           |

## Reporting

- The step-3 table generalizes into a description of catalog-driven asset
  resolution — publishable material.
- Baseline hash diffs prove the resolver returns to a canonical state after
  every experiment. Keep them in the appendix.
