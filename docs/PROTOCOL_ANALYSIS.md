# KK-1000 DSP protocol analysis

## Confirmed from device
- BLE device name: `KK-1000DSP`
- GATT service: `0000ae00-0000-1000-8000-00805f9b34fb`
- TX characteristic: `0000ae01-0000-1000-8000-00805f9b34fb` (`WRITE_NO_RESPONSE`)
- RX characteristic: `0000ae02-0000-1000-8000-00805f9b34fb` (`NOTIFY`)
- CCCD write succeeds (`status=0`).
- No unsolicited AE02 notifications were observed while changing volume on the head unit.

## AUDIO DSP Control 1.0.8 static findings
- Package: `com.nxo.nosio_application`
- BLE UUID strings present: AE00, AE01, AE03.
- Relevant symbols/messages present: `MSG_APP_DSP`, `MSG_APP_EQ_USER`, `MSG_APP_EQ_USER_40`, `MSG_APP_SETTING`, `MSG_APP_VER`, `MSG_APP_VOL_INIT`, `MSG_APP_XOVER`.
- `BTService`, `writeCharacteristic`, `initCharacteristic Fail!`, `Gatt Write Fail!` are present.

## Next step
Trace the `BTService` implementation and call sites that create/write the payload associated with `MSG_APP_VER` / `MSG_APP_VOL_INIT`. Do not send arbitrary packets to the KK-1000. Extract the exact byte-array construction and the sequence of writes, then adapt only the verified handshake to AE01 and observe AE02.
