# AGENTS.md

## Status / resume here (2026-08-16)

- WSL2 USB spike (O4) SUCCESS — usbipd attach, handshake, auto-packets,
  register reads, `EXIT`, and workout config (`WSI103E8` = 1000 m, auto-stop
  at target) all verified live. Device currently DETACHED (unplugged).
- Key protocol findings: read commands must be NO-SPACE (`IRD057` works,
  `IRD 057` → `ERROR`); unit firmware is 2.10 (`IV?` → `IV40210`).
- NEXT ACTION: decide O3 (Kotlin vs Python), then build the capture + logging
  core (F1–F12). See `docs/requirements.md` for the spec and open points.
- Deep docs (read only when relevant): `docs/requirements.md`,
  `docs/protocol.md`, `docs/usb-connectivity.md`. Only this file is loaded
  every session.

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
