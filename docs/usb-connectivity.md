# USB connectivity: WSL2 ↔ S4 attach / detach + serial session

Verified 2026-08-16. Full flow for getting the S4 into WSL2 as `/dev/ttyACM0`
and giving it back. Needed only when physically connecting the rower, or when
building/debugging the tracker's serial/reconnect logic (T1/T5).

## Setup (one-time, already done on this box)

- usbipd-win 5.3.0 installed on Windows (path:
  `C:\Program Files\usbipd-win\usbipd.exe`).
- WSL interop must work to run `usbipd.exe` from inside WSL. If Windows
  executables fail with "exec format error", the `WSLInterop` binfmt handler
  is missing; re-register it (root):
  `echo ':WSLInterop:M::MZ::/init:PF' > /proc/sys/fs/binfmt_misc/register`

## ATTACH (host = Windows, guest = WSL2/Ubuntu)

1. `usbipd list` — find the rower by VID:PID. The S4 is `04d8:000a` and shows
   as `USB Serial Device (COM4)` on busid `2-3`. (Busid may change if USB
   ports change.)
2. `usbipd bind --busid 2-3` — REQUIRES ADMIN. From a non-admin shell it fails
   with "Access denied; this operation requires administrator privileges."
   Two ways: (a) run it from an elevated PowerShell on Windows; or (b) trigger
   a UAC prompt from WSL:
   `powershell.exe -NoProfile -Command "Start-Process -FilePath 'C:\Program Files\usbipd-win\usbipd.exe' -ArgumentList 'bind --busid 2-3' -Verb RunAs -Wait"`
   After bind, `usbipd list` shows STATE `Shared`. Binding persists across
   reboots/replugs, so it usually only needs to be done once.
3. `usbipd attach --wsl --busid 2-3` — works from a non-admin WSL shell:
   `/mnt/c/Program Files/usbipd-win/usbipd.exe attach --wsl --busid 2-3`
   Output mentions "Using WSL distribution 'Ubuntu'", "Loading vhci_hcd
   module", and "Using IP address ... to reach the host".
4. In WSL the device appears shortly after as `/dev/ttyACM0` (user must be in
   the `dialout` group). Verify: `ls -l /dev/ttyACM*`.

## DETACH (release the device back to Windows)

- `/mnt/c/Program Files/usbipd-win/usbipd.exe detach --busid 2-3`
- `/dev/ttyACM0` disappears from WSL; the rower is back under Windows control
  and can be unplugged. `detach` does NOT unbind — the device stays `Shared`
  so the next attach is just steps 1+3 (no admin needed). Fully un-bind with
  `usbipd unbind --busid 2-3` if ever wanted.

## Serial session (pyserial, Python 3.12.3), after attach

- Open `/dev/ttyACM0` at 19200 8N1
  (`serial.Serial('/dev/ttyACM0', 19200, timeout=1)`).
- Always `reset_input_buffer()` first (reconnects may have buffered `P`
  packets).
- Handshake: send `USB\r\n` → expect `_WR_`.
- Read commands are NO-SPACE: `IRS057`, `IRD057`, `IRT1E0` → `IDS/IDD/IDT`
  replies (see `docs/protocol.md`).
- Model/firmware: `IV?` → `IV40210` (Series 4, fw 2.10).
- Optional workout: `RESET` → `OK`+`PING` → `WSI103E8` (1000 m) → `OK`.
- Leave clean: send `EXIT\r\n` (stops auto-packets), then `close()`.
