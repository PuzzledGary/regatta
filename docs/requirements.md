# Requirements — S4 session tracker (v0.2, agreed 2026-08-15)

Decision from discussion: log + post-analysis is the core; a UI (e.g. TUI) is
an explicit nice-to-have. Heart rate is OUT OF SCOPE (not available for this
project; the comparison with watch HR happens elsewhere using exported data).

## Functional requirements

- F1. Connect to the S4 monitor over USB serial and establish the handshake
  (`USB` → `_WR_`).
- F2. Track a session with clear boundaries: start when rowing begins (or user-
  triggered), end on user stop, monitor reset, monitor reaching a configured
  workout target (beep/summary), or idle/pause detection.
- F3. Graceful shutdown (`EXIT`) — monitor always left clean, even on Ctrl-C or
  disconnect.
- F4. Capture time-series, host-timestamped: distance (m) — 0x057/0x058.
- F5. Elapsed clock — 0x1e0–0x1e3.
- F6. Stroke count — 0x140/0x141.
- F7. Stroke rate — 0x142 (and/or derived from `SS`/`SE`).
- F8. Intensity: m/s — 0x148–0x14b; pace (sec/500 m) and watts derived from it.
- F9. Calories (revised): capture monitor-estimated kcal — current `kcal_watts`
  (0x088/0x089) and accumulated `total_kcal` (0x08a–0x08c). Formula is NOT
  documented in the PDF; treat as "monitor kcal" for consistency/comparison, not
  accurate burn. Optionally recompute from distance/splits later.
- F10. Capture raw stroke events (`SS`, `SE`, `P XX`) alongside register polls
  for fine-grained stroke/pulse analysis later.
- F11. No heart-rate capture; but export format must allow merging watch HR data
  post-session (HR ↔ stroke-rate/intensity correlation done elsewhere).
- F12. (Optional, user-settable) Configure a workout on the monitor before a
  session — distance (`WSI`) or duration (`WSU`) — via `RESET` → `PING` →
  `WSI/WSU` → `OK`; gives a clean, monitor-driven session end at the target.
- F13. (DECIDED 2026-08-16) Live-data API during a session: REST snapshot
  (`GET /api/session`) + SSE stream (`GET /api/live`) pushing each `reading`
  to connected clients, so a TUI/browser can display current values
  while capture runs and logs. DB storage explicitly deferred (A4).
- A1. Auto-convert raw session log to an easy-to-use format (e.g. CSV) after a
  session.
- A2. Per-session summary: total distance, time, strokes, avg stroke rate,
  avg intensity/pace, avg watts, kcal, and time-aligned strokes per 500 m /
  1 km splits.
- A3. Retention: raw logs kept a few days/weeks by default, easy to delete.
- A4. (Nice-to-have) Optional import of raw logs into a database (SQLite or
  MariaDB) for long-term storage/queries.
- U1. Core is log + post-analysis; a simple TUI/dashboard is a nice-to-have.
  Keep architecture open for a UI (clean data layer/API). CLI is preferred
  interface but NOT mandatory — decouple core (serial/parsing/logging) from
  interface.

## Technical requirements

- T1. Serial access: auto-detect `/dev/ttyUSB*` / `/dev/ttyACM*`, 19200 baud,
  8N1; device path overridable.
- T2. Protocol per PDF: `IRS/IRD/IRT` polls, `IDS/IDD/IDT` reply parsing,
  `SS`/`SE`/`P` parsing, `USB`/`_WR_`/`EXIT`/`OK`/`ERROR`/`PING` handling.
- T3. Polling cadence: ≥25 ms between PC packets, mindful of monitor burst
  traffic; ~1–4 Hz register polling.
- T4. Host wall-clock ISO 8601 UTC timestamps so sessions align with watch data.
- T5. Robustness: recover from USB disconnect/reattach, prevent serial-port
  stalls, atomic writes. DECIDED 2026-08-16: writes are buffered — flushed
  every `regatta.session.flush-interval` (default 5 s) + on session end and
  shutdown; a crash may lose up to one flush-interval of data (accepted; the
  USB link and host are stable, see O1).
- T6. Non-invasive: default is strictly read-only toward the monitor (no display
  changes or EEPROM writes); OPTIONALLY (F12, user-settable) may configure a
  workout before a session. Never changes the display or writes EEPROM.
- T7. (Nice-to-have) Small CPU/memory footprint — not a design driver.
- T-OS. OS-agnostic core: native Linux + Windows; must also work when launched
  from WSL2 (Ubuntu). Windows is the current daily driver.

## Open points

- O1. Raw-log storage format (e.g. JSONL) vs. derived CSV.
  STATUS (2026-08-16): DECIDED — raw JSONL log is the primary/crash-safe
  store; CSV is a derived export (A1).
  Schema (fixed, `dev.regatta.store`): one file per session
  (`sessions/<startedAt>.jsonl`), one JSON object per line, host ISO-8601
  timestamps, nulls omitted. Records: `header` (format/version/startedAt,
  `firmware` only when `regatta.session.metadata=true`), `session_start`
  (workout config if set), `session_end` (reason: USER_STOP|TARGET_REACHED|
  RESET|IDLE|DISCONNECT, monitor totals), `reading` (poll snapshot), `packet`
  (verbatim auto-packet line, e.g. `SS`/`SE`/`P 12`). Buffered writes,
  flush every `regatta.session.flush-interval` (default 5s) + on session
  end/shutdown; no per-line flush.
  STATUS (2026-08-16): IMPLEMENTED — `dev.regatta.store` (`SessionLogger`,
  `LogRecords`, `SessionLoggerTest`), see AGENTS.md.
- O2. Data model if a database import is ever built.
  STATUS (2026-08-16): deferred — "JSONL only, DB later" decided. If built,
  Spring Data JDBC + SQLite; schema derived from the JSONL record types.
- O3. Language/runtime choice — decision: decide AFTER the WSL2 USB spike (O4).
  Candidates on the table: Kotlin/JVM (jSerialComm) vs Python (pyserial).
  Analysis/statistics is NOT a deciding factor (exports to Grafana later if
  needed). "JVM heavier" = JRE needed + per-OS native serial libs (JNI
  .dll/.so) + build tooling; not a blocker.
  STATUS (2026-08-16): DECIDED — Kotlin/JVM + Spring Boot 4.1.0 (Jackson 3),
  Kotlin 2.3.21, Gradle 9.7 wrapper, Java 25, jSerialComm 2.11.4. Single
  Spring Boot app: one process, log file as the capture↔analysis contract,
  serial module isolated internally (no separate connector process; not worth
  it at these baud/timing requirements).
- O4. Validate usbipd-win / USB serial passthrough for the S4 from WSL2 early.
  STATUS (2026-08-16): DONE — SUCCESS, see `docs/usb-connectivity.md`.
- O5. Clarify "Windows native": run as-is on Windows (COM port) vs. WSL-from-
  Windows only. WSL2 is the stated default.
  STATUS: jSerialComm supports COM* on native Windows as well as /dev/ttyACM*
  on WSL2 — works for both; O5 is a usage decision, not a blocker.

## Session usage context

- Laptop sits on a stand on the rowing machine during sessions while the user
  watches a show (e.g. Netflix) — the tool coexists with playback.
- Workflow: track session → post-session analysis → compare with sports-watch
  data (FIT/TCX/GPX import is a later concern, mentioned in Goal).
