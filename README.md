# regatta

Track WaterRower S4 sessions over USB serial, log them, and analyze them after
the session. See `docs/requirements.md` for the full requirement list (v0.2)
and `AGENTS.md` for orientation.

> Status: technical-solutions phase. Architecture is log-based: a capture
> process writes an append-only raw log (the source of truth); parsing,
> summaries, and CSV export are derived from it. The WSL2 USB spike is DONE
> (verified working); core language decision (Kotlin/JVM vs Python) pending.

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

TBD — capture tool not implemented yet.
