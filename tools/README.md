# KK-1000 DSP protocol analysis

The KK-1000 exposes BLE service `0000ae00-0000-1000-8000-00805f9b34fb` with:
- AE01: WRITE_NO_RESPONSE
- AE02: NOTIFY

AUDIO DSP Control 1.0.8 contains BLE UUID strings for AE00, AE01 and AE03 and application message identifiers including `MSG_APP_VER`, `MSG_APP_VOL_INIT`, `MSG_APP_DSP`, `MSG_APP_EQ_USER`, and `MSG_APP_XOVER`.

Next step: recover the actual byte construction around the app's GATT write calls and compare against KK-1000 AE01/AE02 traffic. Do not send guessed DSP commands to the head unit.
