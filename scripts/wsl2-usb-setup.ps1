# WSL2 USB passthrough for the WaterRower S4 monitor
#
# Run this in an ADMIN Windows PowerShell window.
# Steps 1-3 are one-time setup; step 4 is per-session (or check "bind" flags).

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 1. Install usbipd (one-time). Then RESTART WSL before continuing:
#      wsl --shutdown   (from the same admin PowerShell)
#    and reopen your WSL terminal.
# ---------------------------------------------------------------------------
if (-not (Get-Command usbipd -ErrorAction SilentlyContinue)) {
    Write-Host "usbipd not found - installing via winget..."
    winget install Microsoft/usbipd
    Write-Host "DONE installing. Now run: wsl --shutdown, then re-open WSL and re-run this script from step 2."
    exit 1
} else {
    Write-Host "usbipd already installed."
}

# ---------------------------------------------------------------------------
# 2. List USB devices on the Windows host.
#    Identify the rowing machine: the S4 shows up as a USB CDC device
#    ("Microchip Technology Inc." / "CDC RS-232: WR-S4.2").
# ---------------------------------------------------------------------------
Write-Host "`nUSB devices:"
usbipd list

# ---------------------------------------------------------------------------
# 3. Attach the rower to WSL2 (per session, unless you add --bind which
#    persists the binding across reboots / device reconnects).
#    Replace <BUSID> with the one from `usbipd list`, e.g. 3-2.
# ---------------------------------------------------------------------------
Write-Host "`nAttaching (edit BUSID below as needed)..."
usbipd attach --wsl --busid <BUSID>

# ---------------------------------------------------------------------------
# 4. Verify from inside WSL2 (run in your WSL terminal, not here):
#      ls -l /dev/ttyACM*
#      stty -F /dev/ttyACM0 19200 raw; head -c 64 /dev/ttyACM0
# ---------------------------------------------------------------------------
Write-Host "`nAttached. In WSL2 verify with:  ls -l /dev/ttyACM*"
