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
  - [x] S2 protocol layer — `dev.regatta.protocol`: `Packet` sealed interface
        (StrokeStart/StrokeEnd/Pulse, Ok/Error/Ping, Handshake, Firmware,
        RegisterRead[kind,address,bytes,value], Unknown), `PacketParser`
        (strict uppercase hex; PULSE accepts both `P XX` and `PXX` — spike saw
        `P03`; firmware version bytes are BCD: `IV40210` → 2.10),
        `S4Client(connection, onAutoPacket, replyTimeout)` — connect
        (USB→_WR_ + IV?→Firmware), readByte/Word/Triple (NO-SPACE IRS/IRD/IRT,
        loops frames till matching reply, interleaved auto packets → callback,
        ERROR → ProtocolException), sendWorkoutDistance/Duration (WSI/WSU→OK),
        exit(), close(). Plain classes (not wired). Tests: PacketParserTest +
        S4ClientTest (fake connection, 17 tests) green.
  - [x] S3 session state machine — `dev.regatta.session`: `SessionManager`
        (F2/F3): IDLE→CONNECTING→ACTIVE→ENDED. ACTIVE on rowing evidence
        (register increase vs previous poll, or SS/SE packet — stale idle
        data stays static, so no false start; attach mid-workout starts on
        the 2nd rising poll). ENDED with reason: USER_STOP (stop()) |
        TARGET_REACHED (distance/elapsed ≥ configured target, checked
        before idle) | RESET (values drop vs previous) | IDLE (no evidence
        for `regatta.session.idle-timeout`, default 10s) | DISCONNECT.
        stop()/disconnect() while CONNECTING → back to IDLE, no session.
        Emits `onTransition(status, reason)` for S4; timing driven by
        Reading.timestamp. `SessionEndReason` enum (matches JSONL reasons).
        New config: `regatta.session.idle-timeout`. Plain class, not wired.
        Tests: SessionManagerTest (17 tests) green.
  - [x] S4a capture device layer — `dev.regatta.capture`:
        `ReadingBuilder` (object; maps registers → `Reading`: elapsed from
        clock 0x1e0 triple (min<<16|sec<<8|decs) + 0x1e2 word (hr<<8|min);
        stroke rate = 6000/stroke_avg, pace = 500/speed_mps, watts =
        2.8/speed_mps³ — guards zero/absent), `CaptureDevice` interface
        (`var onPacket: (String) -> Unit`, `connect(): Firmware`, `poll()`,
        `disconnect()`), `S4CaptureDevice` (connectionSupplier seam for
        tests; poll = 8 paced reads IRD057/IRT1E0/IRD1E2/IRD140/IRD142/
        IRD14A/IRD088/IRT08A, ≥25 ms spacing (T3, `minPacketSpacingMs`),
        exception → null; onPacket routes raw frames), `CaptureConfig`
        (Spring @Bean). NOTE: `S4Client.onAutoPacket` now takes the raw line
        `(String)` for verbatim JSONL packet records (F10); `S4Client.await()`
        forwards `frame`. Shared test helper `FakeSerialConnection`. Tests:
        ReadingBuilderTest (4) + S4CaptureDeviceTest (6) green (57 total).
  - [x] LIVE-TEST (2026-08-16): `CaptureService` (minimal stdout driver,
        `@ConditionalOnProperty regatta.demo.enabled=false`) — connects,
        polls on `regatta.session.poll-interval` (1s), prints status
        transitions + reading values, `@PreDestroy` disconnect. Run:
        `./gradlew bootRun --args='--regatta.demo.enabled=false'`.
        FIX found during smoke test: Boot 4.1 binds YAML `device: null` as
        empty string → `getCommPort("")` yields a phantom port; now treated
        as unset in `SerialPortProvider.detect()` (blank device → autodetect).
  - [ ] S4b wiring — poller (1–4 Hz, honors ≥25 ms),
        SessionManager feeding LiveReadingService (SSE) + SessionLogger
        (JSONL) via onTransition, replace DemoLivePublisher
  - [ ] S5 lifecycle — session start/stop entry points, `EXIT` on shutdown
        (F3), reconnect on USB drop (T5)
  - [ ] S6 (optional) F12 workout config — RESET→PING→WSI/WSU→OK
  NEXT ACTION: **S4b wiring** — `CaptureService` driver: connect on
  `regatta.demo.enabled=false`, `@Scheduled` poll → SessionManager.observeReading
  + live.publish + SessionLogger reading/packet records; onTransition opens
  logger (header + session_start) and closes (session_end + flush); update
  SessionController to report real status.
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
