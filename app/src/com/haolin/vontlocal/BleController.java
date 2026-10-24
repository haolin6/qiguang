package com.haolin.vontlocal;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import org.json.*;
import java.util.*;

/** One active connection, device-scoped queue, fragment transport and verified replies. */
final class BleController {
    static final UUID SERVICE=UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    static final UUID CHARACTERISTIC=UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    interface Listener {void changed();void notice(String message);}
    final Context context;final DeviceStore store;final Listener listener;final Handler ui=new Handler(Looper.getMainLooper());
    final BluetoothAdapter adapter;
    BluetoothLeScanner scanner;BluetoothGatt gatt;BluetoothGattCharacteristic channel;
    String address="",status="请选择设备";
    boolean scanning=false,ready=false,writing=false,discovering=false,sessionRead=false;
    long scanGeneration=0,connectionGeneration=0,verifiedReadEpoch=0,verifiedWriteEpoch=0,verifiedClockEpoch=0;int serial=0xffff,mtu=23;
    Protocol.Stream stream=new Protocol.Stream();
    final Map<String,Long> scanUpdates=new HashMap<>();
    final ArrayDeque<Command> commands=new ArrayDeque<>();
    final ArrayDeque<Wire> wires=new ArrayDeque<>();
    Command pending;Wire wire;
    final Runnable saveTask;
    static final class Command {int cmd,seq;byte[] data;String label;Command(int c,byte[] b,String s){cmd=c;data=b;label=s;}}
    static final class Wire {byte[] bytes;int offset=0;Wire(byte[] b){bytes=b;}}
    BleController(Context c,DeviceStore s,Listener l){context=c;store=s;listener=l;BluetoothManager m=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);adapter=m==null?null:m.getAdapter();saveTask=()->store.save();}
    boolean permitted(){return Build.VERSION.SDK_INT>=31?context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED:context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void log(String kind,String text){Log.i("VontLocal",kind+" "+text);store.event(address.isEmpty()?null:address,kind,text);ui.removeCallbacks(saveTask);ui.postDelayed(saveTask,400);}
    void status(String text){status=text;log("status",text);listener.changed();}
    void scan(){
        if(!permitted()){listener.notice("请允许附近设备权限");return;}
        if(adapter==null||!adapter.isEnabled()){listener.notice("请先打开手机蓝牙");return;}
        stopScan();scanner=adapter.getBluetoothLeScanner();if(scanner==null){status("蓝牙扫描不可用");return;}scanUpdates.clear();scanning=true;long generation=++scanGeneration;
        try{scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);status("正在扫描附近的 Vont 灯（20 秒）");ui.postDelayed(()->{if(scanGeneration==generation){stopScan();status(ready?"蓝牙已就绪":"扫描结束，可选择设备连接");}},20000);}catch(Exception e){scanning=false;status("扫描失败："+e.getMessage());}
    }
    void stopScan(){++scanGeneration;if(scanning&&scanner!=null){try{scanner.stopScan(scanCallback);}catch(Exception ignored){}}scanning=false;}
    final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanFailed(int n){ui.post(()->{scanning=false;status("扫描失败："+n);});}
        @Override public void onScanResult(int t,ScanResult r){ui.post(()->{
            if(!scanning)return;ScanRecord sr=r.getScanRecord();if(sr==null)return;String name=sr.getDeviceName();if(name==null)return;
            byte[] raw=sr.getBytes(),mf=manufacturer(raw);if(!(name.startsWith("Vont")||name.startsWith("T-Smart_"))||mf.length<16||mf[0]!=0x54||mf[1]!=0x42)return;
            String id=r.getDevice().getAddress();long now=SystemClock.elapsedRealtime();Long prior=scanUpdates.get(id);if(prior!=null&&now-prior<1500)return;scanUpdates.put(id,now);
            boolean fresh=!store.devices.containsKey(id);store.discovered(id,name,r.getRssi(),mf,raw);if(fresh||prior==null)log("discovery",id+" "+name+" manufacturer="+Protocol.hex(mf));listener.changed();
        });}
    };
    static byte[] manufacturer(byte[] b){if(b!=null)for(int i=0;i<b.length;){int n=b[i]&255;if(n==0||i+n>=b.length)break;if((b[i+1]&255)==255)return Arrays.copyOfRange(b,i+2,i+n+1);i+=n+1;}return new byte[0];}
    void connect(String id){
        if(!permitted()){listener.notice("请允许附近设备权限");return;}
        if(adapter==null||!adapter.isEnabled()){listener.notice("请先打开蓝牙");return;}
        if(!store.devices.containsKey(id)){listener.notice("请先扫描并选择灯");return;}
        stopScan();disconnect(false);address=id;serial=0xffff;mtu=23;discovering=false;sessionRead=false;stream=new Protocol.Stream();long generation=++connectionGeneration;status("正在连接 "+store.name(id));
        try{gatt=adapter.getRemoteDevice(id).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);ui.postDelayed(()->{if(connectionGeneration==generation&&!ready){status("连接超时，请靠近灯并重试");disconnect(false);listener.changed();}},18000);}catch(Exception e){status("连接失败："+e.getMessage());}
    }
    void disconnect(boolean user){
        ++connectionGeneration;ready=false;sessionRead=false;writing=false;discovering=false;commands.clear();wires.clear();pending=null;wire=null;channel=null;
        if(!address.isEmpty())store.live.remove(address);
        if(gatt!=null){BluetoothGatt old=gatt;gatt=null;try{old.disconnect();old.close();}catch(Exception ignored){}}
        if(user)status("已断开，显示保存的设备状态");
    }
    void discover(BluetoothGatt g){if(g!=gatt||discovering)return;discovering=true;if(!g.discoverServices()){status("无法读取蓝牙服务，请重连");disconnect(false);}}
    final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){ui.post(()->{
            if(g!=gatt)return;log("gatt","status="+code+" state="+state);
            if(state==BluetoothProfile.STATE_CONNECTED&&code==0){status("已连接，协商蓝牙数据长度…");boolean requested=false;try{requested=g.requestMtu(247);}catch(Exception ignored){}if(!requested)discover(g);else ui.postDelayed(()->discover(g),1800);}
            else if(state==BluetoothProfile.STATE_DISCONNECTED||code!=0){disconnect(false);status("蓝牙已断开（"+code+"），可重新连接");}
        });}
        @Override public void onMtuChanged(BluetoothGatt g,int n,int code){ui.post(()->{if(g!=gatt)return;if(code==0)mtu=n;DeviceStore.put(store.record(address),"negotiatedMtu",mtu);log("mtu",String.valueOf(mtu));discover(g);});}
        @Override public void onServicesDiscovered(BluetoothGatt g,int code){ui.post(()->{
            if(g!=gatt)return;JSONArray services=new JSONArray();for(BluetoothGattService s:g.getServices()){JSONObject j=new JSONObject();DeviceStore.put(j,"uuid",s.getUuid().toString());JSONArray chars=new JSONArray();for(BluetoothGattCharacteristic c:s.getCharacteristics()){JSONObject ch=new JSONObject();DeviceStore.put(ch,"uuid",c.getUuid().toString());DeviceStore.put(ch,"properties",c.getProperties());chars.put(ch);}DeviceStore.put(j,"characteristics",chars);services.put(j);}DeviceStore.put(store.record(address),"gattServices",services);
            BluetoothGattService service=g.getService(SERVICE);channel=service==null?null:service.getCharacteristic(CHARACTERISTIC);
            if(code!=0||channel==null||(channel.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0){disconnect(false);status("未找到兼容的灯控服务或通知通道");return;}
            if(!g.setCharacteristicNotification(channel,true)){disconnect(false);status("无法订阅灯状态");return;}
            BluetoothGattDescriptor d=channel.getDescriptor(CCCD);if(d==null){disconnect(false);status("灯未提供状态通知描述符");return;}d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);if(!g.writeDescriptor(d)){disconnect(false);status("无法开启状态通知");}
        });}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code){ui.post(()->{if(g!=gatt)return;if(code==0){ready=true;store.live.add(address);DeviceStore.put(store.record(address),"lastConnectedAt",DeviceStore.iso(System.currentTimeMillis()));status("蓝牙已就绪："+store.name(address));read();version();}else{disconnect(false);status("状态订阅失败："+code);}});}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int code){ui.post(()->{if(g!=gatt||!writing)return;writing=false;if(code!=0){log("write_error",String.valueOf(code));disconnect(false);status("发送失败，已断开以清除未完成指令："+code);return;}if(wire!=null){wire.offset+=Math.min(Math.max(19,Math.min(mtu-4,246)),wire.bytes.length-wire.offset);if(wire.offset>=wire.bytes.length)wire=null;}ui.postDelayed(()->pumpWire(),25);});}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] b=c.getValue();if(b==null)return;byte[] copy=b.clone();ui.post(()->{if(g==gatt)receive(copy);});}
    };
    void read(){send(0x82,new byte[0],"读取完整状态");}
    void version(){send(0x91,new byte[0],"读取版本信息");}
    void attributes(byte[] data,String label){
        try{for(Protocol.Attribute a:Protocol.attributes(data))if(a.id==101||a.id==102)throw new IllegalArgumentException("此应用不发送重置或 IC 配置指令");send(0x83,data,label);}catch(Exception e){listener.notice(e.getMessage());}
    }
    void send(int cmd,byte[] data,String label){if(!ready){listener.notice("请先连接灯，等待蓝牙就绪");return;}if(commands.size()>30){listener.notice("指令较多，请等待设备回复");return;}commands.add(new Command(cmd,data,label));pumpCommand();}
    void pumpCommand(){
        if(!ready||pending!=null||commands.isEmpty())return;Command c=commands.removeFirst();c.seq=serial--;if(serial<=0)serial=0xffff;pending=c;byte[] bytes=Protocol.packet(c.cmd,c.data,c.seq,0x80);log("TX",c.label+" "+Protocol.hex(bytes));wires.add(new Wire(bytes));pumpWire();
        long generation=connectionGeneration;ui.postDelayed(()->{if(connectionGeneration!=generation||pending!=c)return;log("timeout",c.label+"，未收到匹配的设备回执");pending=null;if(writing||wire!=null){disconnect(false);status("通信超时，请重连后读取状态");return;}if(c.cmd==0x91){DeviceStore.put(store.record(address),"versionQueryStatus","no_reply");listener.notice("灯未回复版本查询；原始记录仍可导出");pumpCommand();}else{commands.clear();status("未收到设备回执，请读取状态核对");}listener.changed();},Math.max(6000,2000+bytes.length*25));
    }
    void pumpWire(){
        if(!ready||gatt==null||channel==null||writing)return;if(wire==null){if(wires.isEmpty())return;wire=wires.removeFirst();}
        int length=Math.min(Math.max(19,Math.min(mtu-4,246)),wire.bytes.length-wire.offset);byte[] piece=Arrays.copyOfRange(wire.bytes,wire.offset,wire.offset+length);
        int type=(channel.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        writing=true;boolean ok;try{if(Build.VERSION.SDK_INT>=33)ok=gatt.writeCharacteristic(channel,piece,type)==0;else{channel.setWriteType(type);channel.setValue(piece);ok=gatt.writeCharacteristic(channel);}}catch(Exception e){ok=false;log("write_error",e.toString());}
        if(!ok){writing=false;disconnect(false);status("蓝牙拒绝发送，请重连后重试");}
        else{BluetoothGatt expected=gatt;Wire expectedWire=wire;int offset=wire.offset;ui.postDelayed(()->{if(gatt==expected&&writing&&wire==expectedWire&&wire.offset==offset){disconnect(false);status("蓝牙写入超时，请重连");}},4000);}
    }
    void receive(byte[] b){
        log("RX",Protocol.hex(b));int before=stream.rejected;
        for(Protocol.Frame f:stream.feed(b)){
            log("FRAME","cmd="+f.command+" flag="+f.flag+" seq="+f.sequence+" payload="+Protocol.hex(f.data));
            if(f.flag==0x80&&f.command!=0xe0&&f.command!=0xe1){wires.add(new Wire(Protocol.packet(f.command,new byte[0],f.sequence,0x40)));pumpWire();}
            boolean parsedAttributes=false;
            if(f.flag==0x40&&(f.command==0x81||f.command==0x82||f.command==0x83)){
                try{store.attributes(address,f);parsedAttributes=true;if(f.command==0x82)sessionRead=true;}
                catch(Exception e){log("parse_error",e.getMessage());listener.notice("状态解析失败，原始数据已保存："+e.getMessage());}
            }
            if(f.flag==0x40&&f.command==0x91){JSONObject record=store.record(address);DeviceStore.put(record,"versionQueryStatus","raw_reply_received");DeviceStore.put(record,"versionReplyHex",Protocol.hex(f.data));DeviceStore.put(record,"versionReadAt",DeviceStore.iso(System.currentTimeMillis()));if(f.data.length>=9){JSONObject info=new JSONObject();long soft=Protocol.uint(Arrays.copyOfRange(f.data,0,4)),hard=Protocol.uint(Arrays.copyOfRange(f.data,4,8));DeviceStore.put(info,"softwareVersion",Protocol.version(soft));DeviceStore.put(info,"hardwareVersion",Protocol.version(hard));DeviceStore.put(info,"softwareVersionNumber",soft);DeviceStore.put(info,"hardwareVersionNumber",hard);DeviceStore.put(info,"moduleType",f.data[8]&255);DeviceStore.put(info,"source","BLE command 0x91; parser from original DeviceControlVM and VersionUtils");DeviceStore.put(record,"deviceInformation",info);}}
            if(pending!=null&&f.flag==0x40&&f.command==pending.cmd&&f.sequence==pending.seq){if(parsedAttributes&&f.command==0x82)verifiedReadEpoch++;if(parsedAttributes&&f.command==0x83)verifiedWriteEpoch++;if(f.command==135)verifiedClockEpoch++;Command done=pending;pending=null;log("reply",done.label+"：收到设备回执");pumpCommand();}
            listener.changed();
        }
        if(stream.rejected>before)log("checksum_error","损坏的帧已忽略，不更新设备状态");
    }
    void close(){stopScan();disconnect(false);ui.removeCallbacksAndMessages(null);store.save();}
}
