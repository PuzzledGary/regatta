# regatta

Track WaterRower S4 sessions over USB serial, log them, and analyze them after
the session. See `docs/requirements.md` for the full requirement list (v0.2)
and `AGENTS.md` for orientation.

> Status (2026-08-16): Kotlin + Spring Boot scaffold is in place — REST
> snapshot + SSE live API verified, JSONL session logger implemented and
> tested. The capture core (serial + S4 protocol + session state machine) is
> the next step; a demo publisher feeds fake live data until then.

## Stack

Spring Boot 4.1 · Kotlin 2.3.21 · Gradle 9.7 (wrapper) · jSerialComm 2.11.4 ·
Java 25. Raw JSONL logs are the primary store (database import is a later
nice-to-have, A4).

## Running

```sh
./gradlew bootRun      # start the app (HTTP API on :8080)
./gradlew build        # compile + run tests
```

The system `gradle` is ancient (2012) — always use `./gradlew`. Configuration
lives in `src/main/resources/application.yml` (serial device, poll interval,
JSONL flush interval, demo publisher on/off).

## API

- `GET /api/session` — current session status + latest reading (REST snapshot)
- `GET /api/live` — SSE stream, one `reading` event per update

While `regatta.demo.enabled` is true, a demo publisher emits fake readings so
the live API is exercisable without the rower.

## Prerequisites

### USB serial device

The S4 monitor exposes a USB CDC interface (`Microchip Technology Inc.` /
`CDC RS-232: WR-S4.2`) that appears as a virtual serial port. Protocol: ASCII
packets, CR LF terminated, max 50 bytes, auto-baud up to 115200 (19200
recommended, 8N1).

### When running from within WSL2

**Requirement: `usbipd` must be installed on the Windows host.** WSL2 runs in a
VM, so USB devices are not automatically visible. Without this the S4 never
appears as `/dev/ttyACM*`.

One-time setup plus per-session attach — full verified procedure in
`docs/usb-connectivity.md`:

1. Install (admin PowerShell): `winget install Microsoft/usbipd`
2. Restart WSL: `wsl --shutdown`
3. Plug the rower in.
4. `usbipd bind --busid <BUSID>` (admin) and `usbipd attach --wsl --busid <BUSID>`
5. In WSL: verify with `ls -l /dev/ttyACM*`

A ready-to-run script is provided: `scripts/wsl2-usb-setup.ps1`.

> Native Linux works out of the box (device auto-detected as `/dev/ttyACM*` /
> `/dev/ttyUSB*`). No usbipd involved.

## Usage

TBD — capture tool not implemented yet (next step is the serial + protocol
core; the JSONL logger it feeds is already in place).
