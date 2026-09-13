# AUDIO DSP Control 1.0.8 / KK-1000 analysis

Verified from the supplied APK and live KK-1000 GATT inspection:

## KK-1000 live GATT
- Device name: KK-1000DSP
- Service: 0000AE00-0000-1000-8000-00805F9B34FB
- TX: 0000AE01-0000-1000-8000-00805F9B34FB (WRITE_NO_RESPONSE)
- RX: 0000AE02-0000-1000-8000-00805F9B34FB (NOTIFY)

## AUDIO DSP Control 1.0.8 APK strings
- 0000AE00-0000-1000-8000-00805F9B34FB
- 0000AE01-0000-1000-8000-00805F9B34FB
- 0000AE03-0000-1000-8000-00805F9B34FB
- BTService
- writeCharacteristic
- MSG_APP_VER
- MSG_APP_VOL_INIT
- MSG_APP_DSP
- MSG_APP_EQ_USER
- MSG_APP_EQ_USER_40
- MSG_APP_XOVER

## Current conclusion
The supplied application expects AE03 while KK-1000 exposes AE02 as the notify characteristic. A simple UUID substitution is not yet proven sufficient because the initial handshake payload and response validation are still unknown.

## Next required step
Recover the exact byte[] payload construction and write sequence in BTService, then adapt the application to AE01 TX / AE02 RX and validate the handshake before enabling DSP controls.
