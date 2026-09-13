package com.kk1000.bleinspector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView log, status;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder out = new StringBuilder();

    private void p(String s) {
        runOnUiThread(() -> {
            out.append(s).append('\n');
            log.setText(out.toString());
        });
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        log = findViewById(R.id.log);
        status = findViewById(R.id.status);
        ((Button)findViewById(R.id.scan)).setOnClickListener(v -> startScan());

        BluetoothManager bm = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = bm.getAdapter();
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10);
        }
    }

    private void startScan() {
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Önce Bluetooth'u aç.");
            return;
        }
        out.setLength(0);
        p("=== KK-1000 BLE SCAN ===");
        p("15 saniye taranıyor...");
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { p("BLE scanner kullanılamıyor."); return; }
        scanner.startScan(scanCallback);
        handler.postDelayed(() -> {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
            p("=== SCAN BİTTİ ===");
        }, 15000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            ScanRecord s = result.getScanRecord();
            String name = s != null ? s.getDeviceName() : null;
            if (name == null && Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                try { name = d.getName(); } catch (SecurityException ignored) {}
            }
            p("------------------------------");
            p("NAME: " + name);
            p("ADDRESS: " + d.getAddress());
            p("RSSI: " + result.getRssi());
            if (s != null) {
                p("UUIDS: " + s.getServiceUuids());
                p("MFG: " + s.getManufacturerSpecificData());
                p("SERVICE_DATA: " + s.getServiceData());
            }
            if (name != null) {
                String n = name.toLowerCase();
                if (n.contains("kk") || n.contains("king") || n.contains("dsp")) {
                    connect(d);
                }
            }
        }

        @Override public void onScanFailed(int errorCode) { p("SCAN FAILED: " + errorCode); }
    };

    private void connect(BluetoothDevice d) {
        p(">>> GATT CONNECT: " + d.getAddress());
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        try { d.connectGatt(this, false, gattCallback); }
        catch (Exception e) { p("CONNECT ERROR: " + e.getMessage()); }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            p("GATT STATE: " + newState + " status=" + statusCode);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                p(">>> discoverServices()");
                g.discoverServices();
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            p("=== GATT SERVICES ===");
            for (BluetoothGattService s : g.getServices()) {
                p("SERVICE: " + s.getUuid());
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    p("  CHAR: " + c.getUuid() + " props=" + props(c.getProperties()));
                }
            }
        }
    };

    private String props(int p) {
        StringBuilder s = new StringBuilder();
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) s.append(" READ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) s.append(" WRITE");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) s.append(" WRITE_NR");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) s.append(" NOTIFY");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) s.append(" INDICATE");
        return s.toString();
    }
}
