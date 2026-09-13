# AUDIO DSP Control 1.0.8 → KK-1000

## Verified device GATT layout
- Service: 0000AE00-0000-1000-8000-00805F9B34FB
- TX: 0000AE01-0000-1000-8000-00805F9B34FB (WRITE_NO_RESPONSE)
- RX: 0000AE02-0000-1000-8000-00805F9B34FB (NOTIFY)

## Original AUDIO DSP Control 1.0.8
The APK contains AE00/AE01/AE03 and BTService/writeCharacteristic references, plus MSG_APP_VER, MSG_APP_VOL_INIT, MSG_APP_DSP, MSG_APP_EQ_USER and MSG_APP_XOVER.

## Important
No unverified HEX payload is being supplied as a working KK-1000 command. The actual handshake bytes must be extracted from the APK runtime or HCI capture before sending them to AE01. Random payloads can be ignored by the MCU and should not be treated as a valid protocol.

## Next extraction target
Recover the byte[] construction immediately upstream of writeCharacteristic in BTService, then map the response on KK-1000 AE02.
