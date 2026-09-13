package com.kk1000.bleinspector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private TextView log,status;
    private EditText hexInput;
    private Button txButton;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic txChar,rxChar;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final StringBuilder out=new StringBuilder();
    private boolean scanRunning;
    private static final UUID AE00=UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb");
    private static final UUID AE01=UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb");
    private static final UUID AE02=UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private void p(String s){runOnUiThread(()->{out.append(s).append('\n');log.setText(out.toString());});}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        log=findViewById(R.id.log);
        status=findViewById(R.id.status);
        hexInput=findViewById(R.id.hex_input);
        txButton=findViewById(R.id.tx_button);
        Button btn=findViewById(R.id.scan);
        btn.setText("SCAN + CONNECT + AE02 NOTIFY");
        btn.setOnClickListener(v->startScan());
        txButton.setOnClickListener(v->sendManual());
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=bm.getAdapter();
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},10);
    }
    private void startScan(){
        if(adapter==null||!adapter.isEnabled()){status.setText("Bluetooth'u aç.");return;}
        if(gatt!=null){try{gatt.close();}catch(Exception ignored){}gatt=null;}
        txChar=null;rxChar=null;txButton.setEnabled(false);
        out.setLength(0);p("=== KK-1000 BLE INSPECTOR ===");p("15 saniye taranıyor...");
        scanner=adapter.getBluetoothLeScanner();if(scanner==null){p("BLE scanner kullanılamıyor.");return;}
        scanRunning=true;scanner.startScan(scanCallback);
        handler.postDelayed(()->{if(scanRunning){scanRunning=false;try{scanner.stopScan(scanCallback);}catch(Exception ignored){}p("=== SCAN BİTTİ ===");}},15000);
    }
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){
            BluetoothDevice d=r.getDevice();ScanRecord s=r.getScanRecord();String name=s!=null?s.getDeviceName():null;
            if(name==null&&Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){try{name=d.getName();}catch(Exception ignored){}}
            p("------------------------------");p("NAME: "+name);p("ADDRESS: "+d.getAddress());p("RSSI: "+r.getRssi());
            if(s!=null){p("UUIDS: "+s.getServiceUuids());p("MFG: "+s.getManufacturerSpecificData());p("SERVICE_DATA: "+s.getServiceData());}
            if(name!=null&&name.toUpperCase(Locale.ROOT).contains("KK-1000"))connect(d);
        }
        @Override public void onScanFailed(int e){p("SCAN FAILED: "+e);}
    };
    private void connect(BluetoothDevice d){
        if(scanRunning){scanRunning=false;try{scanner.stopScan(scanCallback);}catch(Exception ignored){}}
        p(">>> GATT CONNECT: "+d.getAddress());status.setText("KK-1000 bağlanıyor...");
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;
        try{gatt=d.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);}catch(Exception e){p("CONNECT ERROR: "+e.getMessage());}
    }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int st,int ns){
            p("GATT STATE: "+ns+" status="+st);if(ns==BluetoothProfile.STATE_CONNECTED){gatt=g;status.setText("Bağlandı; servisler aranıyor");p(">>> discoverServices()");g.discoverServices();}else if(ns==BluetoothProfile.STATE_DISCONNECTED){status.setText("Bağlantı kesildi");txButton.setEnabled(false);}}
        @Override public void onServicesDiscovered(BluetoothGatt g,int st){
            p("=== GATT SERVICES ===");
            for(BluetoothGattService svc:g.getServices()){p("SERVICE: "+svc.getUuid());for(BluetoothGattCharacteristic c:svc.getCharacteristics())p("  CHAR: "+c.getUuid()+" props="+props(c.getProperties()));}
            BluetoothGattService svc=g.getService(AE00);if(svc!=null){rxChar=svc.getCharacteristic(AE02);txChar=svc.getCharacteristic(AE01);if(rxChar!=null){p("AE02 NOTIFY: enabling...");enableNotify(g,rxChar);}if(txChar!=null){p("AE01 TX: WRITE_NO_RESPONSE ready");txButton.setEnabled(true);}}
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int st){p("CCCD WRITE status="+st);if(st==BluetoothGatt.GATT_SUCCESS)p("AE02 NOTIFY ACTIVE - waiting for RX.");}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] v=c.getValue();p("RX "+c.getUuid()+" = "+hex(v)+" | ASCII="+ascii(v));}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int st){p("TX DONE "+c.getUuid()+" status="+st);}
    };
    private void enableNotify(BluetoothGatt g,BluetoothGattCharacteristic c){
        if(!g.setCharacteristicNotification(c,true)){p("AE02 notification enable failed");return;}
        BluetoothGattDescriptor d=c.getDescriptor(CCCD);if(d==null){p("AE02 CCCD bulunamadı");return;}
        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);g.writeDescriptor(d);
    }
    private void sendManual(){
        if(gatt==null||txChar==null){p("TX: AE01 bağlı değil");return;}
        String text=hexInput.getText().toString().trim();if(text.isEmpty()){p("TX: HEX girin");return;}
        try{byte[] data=parseHex(text);if(data.length==0){p("TX: boş paket");return;}txChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);txChar.setValue(data);p("TX AE01 = "+hex(data));p("TX REQUEST="+gatt.writeCharacteristic(txChar));}
        catch(Exception e){p("HEX ERROR: "+e.getMessage());}
    }
    private byte[] parseHex(String s){
        s=s.replace(","," ").replace("0x","").replace("0X","").trim();if(s.isEmpty())return new byte[0];
        String[] parts=s.split("\\s+");byte[] data=new byte[parts.length];
        for(int i=0;i<parts.length;i++){if(parts[i].length()>2)throw new IllegalArgumentException("Geçersiz byte: "+parts[i]);data[i]=(byte)Integer.parseInt(parts[i],16);}return data;
    }
    private String props(int p){String z="";if((p&BluetoothGattCharacteristic.PROPERTY_READ)!=0)z+=" READ";if((p&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)z+=" WRITE";if((p&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)z+=" WRITE_NR";if((p&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)z+=" NOTIFY";if((p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)z+=" INDICATE";return z;}
    private String hex(byte[] b){if(b==null)return "";StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02X ",x));return s.toString().trim();}
    private String ascii(byte[] b){return new String(b,StandardCharsets.UTF_8).replaceAll("[^\\x20-\\x7E]",".");}
    @Override protected void onDestroy(){super.onDestroy();try{if(gatt!=null)gatt.close();}catch(Exception ignored){}}
}
