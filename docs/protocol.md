# S4 protocol notes (distilled from spikes, 2026-08-16)

Reference: `water-rower-s4-usb-protocol.pdf` (authoritative) and
`water-rower-s4-usb-protocol.md`. This file captures the essentials plus the
quirks verified empirically on the user's unit (firmware 2.10).

## Packets

- ASCII lines, terminated by CR LF (`0x0D0A`), max 50 bytes.
- Handshake: PC sends `USB`, monitor replies `_WR_`; then auto-packets flow
  until the PC sends `EXIT`.
- Every PC packet produces a reply (or `OK` where nothing else is due).
  Monitor packets are not acknowledged by the PC.

## Auto-transmitted packets (while rowing)

- `SS` — stroke start, `SE` — stroke end, `P XX` — pulse count per 25 ms.
- NOTE: the user's flywheel emits `P03`/`P04` even when idle (minor
  paddle/wheel movement); per the PDF the PC app must filter these.
- `PING` — sent ~once per second while NO rowing is occurring; signals the
  monitor is operational but stopped. After a `RESET` a `PING` means the
  monitor is ready again for data.

## Memory reads — NO-SPACE syntax

- `IRSXXX`, `IRDXXX`, `IRTXXX`. A space between command and address
  (e.g. `IRD 057`) returns `ERROR` even though the PDF renders the syntax as
  "I RD + XXX" — the "+" is concatenation, not a literal space.
- Replies: `IDSXXXY1`, `IDDXXXY2Y1`, `IDTXXXY3Y2Y1` — highest address byte
  first (Y1 = address XXX).
- Model/firmware: `IV?` → `IV4<major><minor>`; user's unit reports
  `IV40210` (Series 4, fw 2.10 — the PDF map is for fw 2.00, registers below
  verified working on 2.10).

## Register map (verified on fw 2.10)

- distance `0x057`/`0x058` = low/hi bytes of meters (max 65535 m).
- display clock `0x1e0` dec-seconds 0.0–0.9, `0x1e1` sec, `0x1e2` min, `0x1e3` hr.
- strokes `0x140`/`0x141` (low/hi).
- `stroke_average` `0x142` = 0.01 s per whole stroke; `stroke_pull` `0x143`.
- m/s `0x148`/`0x149` total cm/s, `0x14a`/`0x14b` instant average cm/s.
- kcal `0x088`/`0x089` current kcal+watts, `0x08a`–`0x08c` total kcal.
- workout totals `0x1e8`–`0x1ef`, heart-rate peak `0x1f9`.
- Full map: see the protocol PDF/markdown (Series 4, v2.00).

## Workout config (a WRITE — optional, per F12/T6)

- Sequence: `RESET` → `OK` + `PING` (ready) → config packet → `OK`.
- Distance: `WSI<X><YYYY>`, X = 1 meters, 2 miles, 3 km, 4 strokes;
  YYYY = 16-bit hex target (e.g. `WSI103E8` = 1000 m). Max 64000 m / 5000 strokes.
- Duration: `WSU<YYYY>`, YYYY = hex seconds (max 18000 = 5 h).
- Interval workouts: `WII`/`WIU`/`WIN` (see PDF, multiple packets, must
  complete within the PING window; no rowing during programming).
- Verified live: `RESET`→`OK`+`PING`→`WSI103E8`→`OK`; the monitor retained the
  workout and auto-stopped the session at 1000 m (beep + summary).

## Spike observations (2026-08-16)

- While idle the monitor reports STALE workout data: registers showed a previous
  session (61 m, 8 strokes, 56.3 s clock) with no rowing in progress. Session
  start/end detection must distinguish idle-showing-last-workout vs. an actual
  new workout (e.g. distance/strokes starting to change, or `SS` activity).
- `total_kcal` (`0x08a`–`0x08c`) read 0x0848 = 2120 while distance was only
  61 m — either it accumulates across workouts or the units differ. Treat as
  opaque "monitor kcal" for now; open question for the post-analysis phase.
- Connection loss: the monitor keeps running on its own power; the USB link can
  be dropped mid-session without losing the workout on the monitor. On replug
  the tracker should drain buffered packets, re-handshake (`USB`→`_WR_`), and
  resume register polls; the gap only loses fine-grained `P`/`SS`/`SE` events.
