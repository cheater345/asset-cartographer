# Journal schema

Every operation appends one JSON object (JSONL format) to
`files/journal/journal.jsonl` inside the app sandbox.

| field       | meaning                                                                    |
|-------------|----------------------------------------------------------------------------|
| `id`        | uuid of the entry                                                          |
| `wallClock` | ms since epoch, start of the operation                                     |
| `op`        | operation id: `apk.import`, `device.listDir`, `saf.walk`, `report.export`, `journal.rollback` |
| `target`    | what was operated on (uri or path)                                         |
| `outcome`   | `ok` / `error` / `partial`                                                 |
| `detail`    | human-readable result summary                                              |
| `artifacts` | absolute paths of files this tool created                                  |
| `reversible`| true when rollback can delete the artifacts                                |

Rules:

- Reads are journaled with `reversible = false` — nothing to undo, but the
  receipt exists, so the observation is reproducible.
- `journal.rollback` only ever deletes artifacts that live under this app's
  sandbox. It cannot touch anything else, by construction.
- The same schema is used by the companion Unity lab, so both pipelines feed
  one write-up.
