package com.swat.dspinspector;

import android.Manifest;
import android.app.Activity;
import android.os.*;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final UUID AE00=UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    BluetoothAdapter adapter; BluetoothGatt gatt; BluetoothGattCharacteristic tx,rx;
    ArrayList<BluetoothDevice> found=new ArrayList<>(); ArrayList<String> labels=new ArrayList<>();
    ArrayAdapter<String> aa; TextView log,status; EditText hex; Handler h=new Handler(Looper.getMainLooper());

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
    void scan(){
        if(adapter==null||!adapter.isEnabled()){status.setText("Bluetooth OFF");return;}
        found.clear(); labels.clear(); aa.notifyDataSetChanged(); status.setText("Scanning..."); log("[SCAN] start");
        BluetoothLeScanner s=adapter.getBluetoothLeScanner();
        s.startScan(new android.bluetooth.le.ScanCallback(){public void onScanResult(int t,android.bluetooth.le.ScanResult r){BluetoothDevice d=r.getDevice(); if(!found.contains(d)){found.add(d); String n=d.getName(); if(n==null)n="(no name)"; labels.add(n+"  "+d.getAddress()); aa.notifyDataSetChanged(); log("[ADV] "+n+" "+d.getAddress()+" RSSI="+r.getRssi());}}});
        h.postDelayed(()->{s.stopScan(new android.bluetooth.le.ScanCallback(){}); status.setText("Scan complete");},8000);
    }
    void connect(BluetoothDevice d){status.setText("Connecting "+d.getName()); log("[CONNECT] "+d.getAddress()); gatt=d.connectGatt(this,false,cb);}
    final BluetoothGattCallback cb=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int st,int ns){log("[GATT] state="+st+" newState="+ns); if(ns==BluetoothProfile.STATE_CONNECTED){status.setText("CONNECTED"); g.discoverServices();} else if(ns==BluetoothProfile.STATE_DISCONNECTED){status.setText("DISCONNECTED"); g.close(); gatt=null; tx=rx=null;}}
        public void onServicesDiscovered(BluetoothGatt g,int st){
            log("[SERVICES] status="+st);
            for(BluetoothGattService s:g.getServices()){
                log("SERVICE "+s.getUuid()); if(s.getUuid().equals(AE00))log("*** AE00 FOUND ***");
                for(BluetoothGattCharacteristic c:s.getCharacteristics()){
                    int p=c.getProperties(); log("  CHAR "+c.getUuid()+" props="+props(p));
                    if(s.getUuid().equals(AE00)){if((p&(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE|BluetoothGattCharacteristic.PROPERTY_WRITE))!=0&&tx==null)tx=c; if((p&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0&&rx==null)rx=c;}
                }
            }
            if(rx!=null){g.setCharacteristicNotification(rx,true); BluetoothGattDescriptor d=rx.getDescriptor(CCCD); if(d!=null){d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);g.writeDescriptor(d);} log("[NOTIFY] enabled "+rx.getUuid());}
            status.setText(tx!=null&&rx!=null?"AE00 READY":"AE00 incomplete");
        }
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){log("[RX "+c.getUuid()+"] "+hx(c.getValue()));}
        public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int st){log("[TX ACK] "+c.getUuid()+" status="+st);}
        public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int st){log("[CCCD] "+d.getUuid()+" status="+st);}
    };
    String props(int p){ArrayList<String>a=new ArrayList<>();if((p&2)!=0)a.add("READ");if((p&8)!=0)a.add("WRITE");if((p&4)!=0)a.add("WRITE_NR");if((p&16)!=0)a.add("NOTIFY");if((p&32)!=0)a.add("INDICATE");return a.toString();}
    void sendHex(String s){if(tx==null||gatt==null){log("[TX] NOT READY");return;}try{byte[]b=parse(s);tx.setValue(b);tx.setWriteType((tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);boolean ok=gatt.writeCharacteristic(tx);log("[TX AE01] "+hx(b)+" result="+ok);}catch(Exception e){log("[TX ERROR] "+e);}}
    byte[]parse(String s){s=s.replaceAll("[^0-9A-Fa-f]","");if((s.length()&1)!=0)throw new IllegalArgumentException("Odd HEX length");byte[]b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    String hx(byte[]b){StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.US,"%02X ",v&255));return x.toString().trim();}
    void log(String s){runOnUiThread(()->{log.append("["+new java.text.SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+"] "+s+"\n");});}
}
