# AGENTS.md

## Status / resume here (2026-08-16)

- **O3 DECIDED (2026-08-16): Kotlin/JVM + Spring Boot 4.1.0, Kotlin 2.3.21,
  Gradle 9.7 (wrapper), Java 25, jSerialComm 2.11.4.** Scaffold builds and
  boots; REST snapshot (`GET /api/session`) + SSE live stream (`GET /api/live`,
  `SseEmitter`, one `reading` event) verified. Storage DECIDED: raw JSONL log
  primary (DB later, A4).
- **O1 DONE (2026-08-16): JSONL store layer implemented + tested** —
  `dev.regatta.store`: `LogRecords` (header/session_start/session_end/reading/
  packet, nulls omitted, schema fixed in docs/requirements.md O1), `SessionLogger`
  (one file per session, buffered, flush every `regatta.session.flush-interval`).
  `SessionLoggerTest` (4 tests) green.
- Live-data API wired to `DemoLivePublisher` (`regatta.demo.enabled`) — fake
  readings until the capture core lands.
- WSL2 USB spike (O4) SUCCESS — usbipd attach, handshake, auto-packets,
  register reads, `EXIT`, and workout config (`WSI103E8` = 1000 m, auto-stop
  at target) all verified live. Device currently DETACHED (unplugged).
- Key protocol findings: read commands must be NO-SPACE (`IRD057` works,
  `IRD 057` → `ERROR`); unit firmware is 2.10 (`IV?` → `IV40210`).
- CAPTURE CORE PLAN (F1–F12) — small steps, marked as done:
  - [x] S0 store layer (O1) — LogRecords + SessionLogger, tested
  - [x] S1 serial adapter — `dev.regatta.serial`: `SerialConnection` interface,
        `JSerialCommConnection` (jSerialComm, 19200 8N1 via
        `setComPortTimeouts(TIMEOUT_READ_BLOCKING, 200ms)`, blocking read →
        0 on timeout), `CrlfFrameReader` (CRLF frames, 50-byte cap, drops
        oversize/garbage with resync), `SerialPortProvider` (config
        `regatta.serial.device` override, else first `/dev/ttyUSB*`|
        `/dev/ttyACM*`|`COM\d+`). Plain classes (not wired yet). Tests:
        `CrlfFrameReaderTest` + `SerialPortProviderTest` (8 tests) green.
        NOTE: jSerialComm has NO `setReadTimeout` — use
        `setComPortTimeouts(TIMEOUT_READ_BLOCKING, ms, 0)`.
  - [ ] S2 protocol layer — S4Client (handshake `USB`→`_WR_`, `EXIT`, NO-SPACE
        `IRS/IRD/IRT` polls, `WSI/WSU` config), PacketParser (`IDS/IDD/IDT`
        replies, `SS`/`SE`/`P XX`, `OK`/`ERROR`/`PING`) (T2)
  - [ ] S3 session state machine — SessionManager (F2/F3):
        idle→connecting→active→ended; end reasons USER_STOP|TARGET_REACHED|
        RESET|IDLE|DISCONNECT
  - [ ] S4 wiring — SessionManager feeds LiveReadingService (SSE) +
        SessionLogger (JSONL), 1–4 Hz poller honoring ≥25 ms spacing (T3);
        replace DemoLivePublisher
  - [ ] S5 lifecycle — session start/stop entry points, `EXIT` on shutdown
        (F3), reconnect on USB drop (T5)
  - [ ] S6 (optional) F12 workout config — RESET→PING→WSI/WSU→OK
  NEXT ACTION: **S2 protocol layer**. Spec: `docs/requirements.md`,
  protocol details: `docs/protocol.md`.
- Deep docs (read only when relevant): `docs/requirements.md`,
  `docs/protocol.md`, `docs/usb-connectivity.md`. Only this file is loaded
  every session.

## Working style

- Work in SMALL steps, one topic at a time. Discuss before acting; no big batch
  builds without checking in. The user explicitly asked for this.
- Discuss structural/naming decisions (packages, class names, API shape) BEFORE
  implementing them.

## Project

We're building a session tracker for the WaterRower S4 rowing computer: the
laptop connects to the monitor over USB (CDC serial) during workouts, logs
distance / strokes / pace / calories as a host-timestamped time series, and
after the session summarizes and exports the workout for comparison with
sports-watch data. Core = log + post-analysis; a UI (TUI) is a nice-to-have.
Heart rate is out of scope.

Source docs: `water-rower-s4-usb-protocol.pdf` (authoritative) and the
generated `water-rower-s4-usb-protocol.md`. If they disagree, trust the PDF.

## Protocol cheat-sheet

- ASCII lines, CR LF, max 50 bytes, 19200 8N1. `USB` → `_WR_`; `EXIT` stops
  auto-packets (always leave the monitor clean).
- Auto while rowing: `SS` (stroke start), `SE` (stroke end), `P XX`
  (pulses/25 ms). `P` packets also fire from idle wheel movement — filter.
- Memory reads are NO-SPACE: `IRSXXX`/`IRDXXX`/`IRTXXX` → `IDS/IDD/IDT`
  replies (highest address byte first). Key registers: distance 0x057/058 m,
  clock 0x1e0–0x1e3, strokes 0x140/141, stroke avg 0x142, m/s 0x148–14b,
  kcal 0x088–08c, workout totals 0x1e8–1ef. `IV?` → firmware (this unit 2.10).
- Workout config (a WRITE, optional): `RESET` → `OK`+`PING` → `WSI<unit><hex>`
  or `WSU<hex sec>` → `OK`; monitor auto-stops at the target.
- Details, quirks, spike observations: `docs/protocol.md`.

## Repo files

- `AGENTS.md` — this orientation (always loaded).
- `docs/requirements.md` — F/A/U/T spec, open points (O1–O5), usage context.
- `docs/protocol.md` — packet/register details, no-space syntax, workout
  config, spike observations (stale data, kcal units, connection loss).
- `docs/usb-connectivity.md` — WSL2↔S4 attach/detach (usbipd) + serial recipe.
- `water-rower-s4-usb-protocol.{pdf,md}` — source docs (PDF authoritative).
- `scripts/wsl2-usb-setup.ps1` — usbipd setup helper.
- `README.md` — user-facing intro.
- App source — Spring Boot 4.1 / Kotlin / Gradle. Entrypoint:
  `src/main/kotlin/dev/regatta/RegattaApplication.kt` (base package
  `dev.regatta`). Build/run: `./gradlew bootRun` (system gradle is 2012-era —
  always use the wrapper). Config: `src/main/resources/application.yml`. Live
  API: `/api/session` (REST snapshot) + `/api/live` (SSE stream, one `reading`
  event per snapshot).
- Boot 4 = Jackson 3 (`tools.jackson.*`): `ObjectMapper` is immutable, inject
  the auto-configured bean (null-omission for the JSONL log via `@JsonInclude`
  on `LogRecord`). For READING JSONL back (analysis) later: add
  `tools.jackson.module:jackson-module-kotlin` (3.x, NOT managed by Boot BOM).
