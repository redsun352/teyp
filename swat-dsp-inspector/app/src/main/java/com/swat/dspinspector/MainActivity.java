package com.swat.dspinspector;

import android.Manifest;
import android.app.Activity;
import android.os.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final UUID AE00=UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb");
    static final UUID AE01=UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb");
    static final UUID AE02=UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    BluetoothAdapter adapter; BluetoothGatt gatt; BluetoothGattCharacteristic tx,rx;
    ArrayList<BluetoothDevice> found=new ArrayList<>(); ArrayList<String> labels=new ArrayList<>();
    ArrayAdapter<String> aa; TextView log,status; EditText hex; Handler h=new Handler(Looper.getMainLooper());
    BluetoothLeScanner scanner; boolean scanning=false;
    final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){
            BluetoothDevice d=r.getDevice();
            if(!found.contains(d)){
                found.add(d); String n=safeName(d); if(n.isEmpty())n="(no name)";
                labels.add(n+"  "+d.getAddress()); aa.notifyDataSetChanged();
                log("[ADV] "+n+" "+d.getAddress()+" RSSI="+r.getRssi()+" "+scanInfo(r));
            }
        }
        @Override public void onScanFailed(int errorCode){log("[SCAN ERROR] code="+errorCode); status.setText("Scan error "+errorCode); scanning=false;}
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); log=findViewById(R.id.log); hex=findViewById(R.id.hex);
        Spinner sp=findViewById(R.id.devices); aa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels); sp.setAdapter(aa);
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE); adapter=bm.getAdapter();
        findViewById(R.id.scan).setOnClickListener(v->scan());
        findViewById(R.id.clear).setOnClickListener(v->log.setText(""));
        findViewById(R.id.connect).setOnClickListener(v->{int i=sp.getSelectedItemPosition(); if(gatt!=null){gatt.disconnect();return;} if(i>=0&&i<found.size())connect(found.get(i));});
        findViewById(R.id.send).setOnClickListener(v->sendHex(hex.getText().toString())); requestPerms();
    }
    void requestPerms(){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},7);}
    String safeName(BluetoothDevice d){try{String n=d.getName();return n==null?"":n;}catch(SecurityException e){return "";}}
    String scanInfo(ScanResult r){ScanRecord sr=r.getScanRecord(); if(sr==null)return ""; byte[]m=sr.getManufacturerSpecificData(0xFFFF); return m==null?"":"MFG="+hx(m);}
    void scan(){
        if(adapter==null||!adapter.isEnabled()){status.setText("Bluetooth OFF");return;}
        if(scanning){stopScan();return;}
        found.clear(); labels.clear(); aa.notifyDataSetChanged(); status.setText("Scanning..."); log("[SCAN] BLE start");
        scanner=adapter.getBluetoothLeScanner(); if(scanner==null){status.setText("BLE unavailable");log("[SCAN ERROR] scanner unavailable");return;}
        try{scanner.startScan(scanCallback); scanning=true;}catch(Exception e){log("[SCAN ERROR] "+e);return;}
        h.postDelayed(()->{if(scanning)stopScan();},10000);
    }
    void stopScan(){if(scanner!=null){try{scanner.stopScan(scanCallback);}catch(Exception ignored){}} scanning=false; status.setText("Scan complete"); log("[SCAN] BLE stop");}
    void connect(BluetoothDevice d){stopScan(); tx=rx=null; status.setText("Connecting "+safeName(d)); log("[CONNECT BLE] "+safeName(d)+" "+d.getAddress()); try{gatt=d.connectGatt(this,false,cb,BluetoothDevice.TRANSPORT_LE);}catch(Exception e){log("[CONNECT ERROR] "+e);}}
    final BluetoothGattCallback cb=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int st,int ns){log("[GATT] state="+st+" newState="+ns); if(ns==BluetoothProfile.STATE_CONNECTED){status.setText("CONNECTED BLE"); log("[GATT] requesting MTU 247"); g.requestMtu(247); g.discoverServices();} else if(ns==BluetoothProfile.STATE_DISCONNECTED){status.setText("DISCONNECTED"); g.close(); gatt=null; tx=rx=null;}}
        public void onMtuChanged(BluetoothGatt g,int mtu,int st){log("[MTU] "+mtu+" status="+st);}
        public void onServicesDiscovered(BluetoothGatt g,int st){
            log("[SERVICES] status="+st+" count="+g.getServices().size());
            for(BluetoothGattService s:g.getServices()){
                log("SERVICE "+s.getUuid()); if(s.getUuid().equals(AE00))log("*** AE00 FOUND ***");
                for(BluetoothGattCharacteristic c:s.getCharacteristics()){
                    int p=c.getProperties(); log("  CHAR "+c.getUuid()+" props="+props(p));
                    if(s.getUuid().equals(AE00)){
                        if(c.getUuid().equals(AE01))tx=c;
                        if(c.getUuid().equals(AE02))rx=c;
                        if((p&(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE|BluetoothGattCharacteristic.PROPERTY_WRITE))!=0&&tx==null)tx=c;
                        if((p&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0&&rx==null)rx=c;
                    }
                }
            }
            if(rx!=null){g.setCharacteristicNotification(rx,true); BluetoothGattDescriptor d=rx.getDescriptor(CCCD); if(d!=null){d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);g.writeDescriptor(d);} log("[NOTIFY] enabled "+rx.getUuid());}
            status.setText(tx!=null&&rx!=null?"AE00 READY":"AE00 incomplete");
            log("[READY] TX="+(tx==null?"none":tx.getUuid())+" RX="+(rx==null?"none":rx.getUuid()));
        }
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){log("[RX "+c.getUuid()+"] len="+c.getValue().length+" HEX="+hx(c.getValue()));}
        public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int st){log("[TX ACK] "+c.getUuid()+" status="+st);}
        public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int st){log("[CCCD] "+d.getUuid()+" status="+st);}
    };
    String props(int p){ArrayList<String>a=new ArrayList<>();if((p&2)!=0)a.add("READ");if((p&8)!=0)a.add("WRITE");if((p&4)!=0)a.add("WRITE_NR");if((p&16)!=0)a.add("NOTIFY");if((p&32)!=0)a.add("INDICATE");return a.toString();}
    void sendHex(String s){if(tx==null||gatt==null){log("[TX] NOT READY");return;}try{byte[]b=parse(s);tx.setValue(b);tx.setWriteType((tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);boolean ok=gatt.writeCharacteristic(tx);log("[TX AE01] len="+b.length+" HEX="+hx(b)+" result="+ok);}catch(Exception e){log("[TX ERROR] "+e);}}
    byte[]parse(String s){s=s.replaceAll("[^0-9A-Fa-f]","");if((s.length()&1)!=0)throw new IllegalArgumentException("Odd HEX length");byte[]b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    String hx(byte[]b){StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.US,"%02X ",v&255));return x.toString().trim();}
    void log(String s){runOnUiThread(()->{log.append("["+new java.text.SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+"] "+s+"\n");});}
}
