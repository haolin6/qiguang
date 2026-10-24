package com.haolin.vontlocal;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local device registry and portable, versioned evidence export. */
final class DeviceStore {
    final Context context;
    final LinkedHashMap<String,JSONObject> devices=new LinkedHashMap<>();
    final ArrayDeque<JSONObject> events=new ArrayDeque<>();
    JSONArray scenes=new JSONArray();
    final Set<String> live=new HashSet<>();
    String loadError="";
    DeviceStore(Context c){context=c;load();}
    static void put(JSONObject o,String key,Object value){try{o.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static String iso(long t){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(t));}
    static JSONObject object(JSONObject o,String k){JSONObject v=o.optJSONObject(k);if(v==null){v=new JSONObject();put(o,k,v);}return v;}
    JSONObject record(String address){JSONObject d=devices.get(address);if(d==null){d=new JSONObject();put(d,"deviceId","ble:"+address.toUpperCase(Locale.US));put(d,"bleAddress",address);put(d,"alias","");put(d,"room","");put(d,"attributes",new JSONObject());put(d,"createdAt",iso(System.currentTimeMillis()));devices.put(address,d);}return d;}
    void discovered(String address,String name,int rssi,byte[] manufacturer,byte[] advertising){
        JSONObject d=record(address);put(d,"advertisedName",name);put(d,"rssiDbm",rssi);put(d,"lastSeenAt",iso(System.currentTimeMillis()));put(d,"manufacturerHex",Protocol.hex(manufacturer));put(d,"advertisementHex",Protocol.hex(advertising));
        if(manufacturer.length>=16){put(d,"productCode",Protocol.uint(Arrays.copyOfRange(manufacturer,2,6)));put(d,"wifiMacFromAdvertisement",mac(Arrays.copyOfRange(manufacturer,6,12)));put(d,"advertisementFlagsHex",Protocol.hex(Arrays.copyOfRange(manufacturer,12,16)));}
        if(name.startsWith("Vont "))put(d,"modelFromName",name.substring(5).split("_")[0]);
    }
    static String mac(byte[] b){String s=Protocol.hex(b);StringBuilder x=new StringBuilder();for(int i=0;i<s.length();i+=2){if(i>0)x.append(':');x.append(s.substring(i,i+2));}return x.toString();}
    String name(String address){JSONObject d=record(address);String alias=d.optString("alias");return alias.isEmpty()?d.optString("advertisedName",address):alias;}
    void event(String address,String kind,String message){JSONObject e=new JSONObject();put(e,"at",iso(System.currentTimeMillis()));put(e,"deviceId",address==null?JSONObject.NULL:"ble:"+address);put(e,"kind",kind);put(e,"message",message);events.add(e);while(events.size()>1000)events.removeFirst();}
    void attributes(String address,Protocol.Frame f){
        // Parse first; never publish a partial state after a malformed frame.
        List<Protocol.Attribute> parsed=Protocol.attributes(f.data);
        JSONObject d=record(address),attrs=object(d,"attributes");String at=iso(System.currentTimeMillis());
        String source=f.command==0x82?"device_read":f.command==0x83?"device_write_reply":"device_notification";
        for(Protocol.Attribute a:parsed){JSONObject j=new JSONObject();put(j,"index",a.id);put(j,"key",Protocol.key(a.id));put(j,"label",Protocol.label(a.id));put(j,"type",a.type);put(j,"typeName",Protocol.TYPES[a.type]);Object value=a.value();if(value instanceof Double&&!Double.isFinite((Double)value))value=String.valueOf(value);put(j,"value",value);put(j,"rawValueHex",Protocol.hex(a.raw));put(j,"display",a.display());put(j,"source",source);put(j,"updatedAt",at);put(j,"sequence",f.sequence);put(j,"semantics",Protocol.key(a.id).startsWith("unknown_")?"unknown":"name_from_original_apk; value_range_may_be_unverified");
            if(a.id==6&&a.raw.length==4)put(j,"rgbHex",String.format(Locale.US,"#%06X",Protocol.uint(a.raw)&0xffffff));
            if(a.id==15&&a.type==8&&a.raw.length%4==0){JSONArray colors=new JSONArray();for(int i=0;i<a.raw.length;i+=4)colors.put(String.format(Locale.US,"#%06X",Protocol.uint(Arrays.copyOfRange(a.raw,i,i+4))&0xffffff));put(j,"segmentColors",colors);}
            put(attrs,String.valueOf(a.id),j);
        }
        put(d,"lastAttributeUpdateAt",at);
        if(f.command==0x82){JSONArray ids=new JSONArray();for(Protocol.Attribute a:parsed)ids.put(a.id);put(d,"lastFullReadIndices",ids);put(d,"lastFullReadAt",at);put(d,"lastFullReadSequence",f.sequence);put(d,"lastFullReadPayloadHex",Protocol.hex(f.data));put(d,"lastFullReadAttributeCount",parsed.size());}
    }
    JSONObject attribute(String address,int id){return object(record(address),"attributes").optJSONObject(String.valueOf(id));}
    boolean lastReadContains(String address,int id){JSONArray ids=record(address).optJSONArray("lastFullReadIndices");if(ids==null)return false;for(int i=0;i<ids.length();i++)if(ids.optInt(i,-1)==id)return true;return false;}
    byte[] raw(String address,int id){JSONObject a=attribute(address,id);return a==null?null:Protocol.unhex(a.optString("rawValueHex"));}
    int integer(String address,int id,int fallback){JSONObject a=attribute(address,id);if(a==null)return fallback;Object v=a.opt("value");if(v instanceof Boolean)return (Boolean)v?1:0;return a.optInt("value",fallback);}
    int[] segmentColors(String address){byte[] b=raw(address,15);if(b==null||b.length==0||b.length%4!=0)return new int[0];int[] c=new int[b.length/4];for(int i=0;i<c.length;i++)c[i]=(int)Protocol.uint(Arrays.copyOfRange(b,i*4,i*4+4))&0xffffff;return c;}
    JSONObject export() {
        JSONObject o=new JSONObject();put(o,"schema","vont-local-export/v1");put(o,"appVersion","0.6");put(o,"exportedAt",iso(System.currentTimeMillis()));put(o,"transport","BLE; local only");
        put(o,"coverage","Device-returned attributes and local records; not a firmware dump or cloud-account backup. Missing attributes mean unknown, not unsupported. Timestamps distinguish saved and current observations.");
        JSONArray list=new JSONArray();for(Map.Entry<String,JSONObject> entry:devices.entrySet()){try{JSONObject copy=new JSONObject(entry.getValue().toString());put(copy,"connectedAtExport",live.contains(entry.getKey()));put(copy,"firmwareVersionStatus",copy.has("deviceInformation")?"see deviceInformation; absent fields unknown":"not_reported; version query raw response retained if available");list.put(copy);}catch(JSONException e){throw new IllegalStateException(e);}}put(o,"devices",list);put(o,"localStaticScenes",scenes);put(o,"recentEvents",new JSONArray(events));
        JSONObject p=new JSONObject();put(p,"serviceUuid","0000ffe0-0000-1000-8000-00805f9b34fb");put(p,"characteristicUuid","0000ffe1-0000-1000-8000-00805f9b34fb");put(p,"readCommand",130);put(p,"writeCommand",131);put(p,"checksum","(sum(unsigned preceding bytes) XOR 0x33) & 0xff");put(o,"protocol",p);return o;
    }
    String json(){try{return export().toString(2);}catch(JSONException e){throw new IllegalStateException(e);}}
    String text(){StringBuilder s=new StringBuilder("Vont 本地灯控 0.6\n导出时间："+iso(System.currentTimeMillis())+"\n状态带读取时间；缺失字段表示未知。\n\n");for(Map.Entry<String,JSONObject> entry:devices.entrySet()){
        String id=entry.getKey();JSONObject d=entry.getValue();s.append(name(id)).append("\n").append("设备 ID：").append(d.optString("deviceId")).append("\n房间：").append(d.optString("room")).append("\n型号：").append(d.optString("modelFromName","未报告")).append("\n产品代码：").append(d.optString("productCode","未知")).append("\nWi-Fi MAC（广播）：").append(d.optString("wifiMacFromAdvertisement","未知")).append("\n信号：").append(d.optString("rssiDbm","未知")).append(" dBm\n最后发现：").append(d.optString("lastSeenAt","未知")).append("\n最后完整读取：").append(d.optString("lastFullReadAt","尚未读取")).append("\n当前连接：").append(live.contains(id)?"是":"否，以下为保存的状态").append("\n");
        JSONObject info=d.optJSONObject("deviceInformation");if(info!=null)s.append("软件版本：").append(info.optString("softwareVersion","未知")).append("\n硬件版本：").append(info.optString("hardwareVersion","未知")).append("\n版本读取时间：").append(d.optString("versionReadAt")).append("\n");s.append("版本原始回复：").append(d.optString("versionReplyHex","未读取")).append("\n蓝牙服务：").append(d.optString("gattServices","未读取")).append("\n");
        JSONObject attrs=object(d,"attributes");List<Integer> ids=new ArrayList<>();Iterator<String> keys=attrs.keys();while(keys.hasNext())ids.add(Integer.parseInt(keys.next()));Collections.sort(ids);for(int key:ids){JSONObject a=attrs.optJSONObject(String.valueOf(key));s.append("#").append(key).append(" ").append(a.optString("label")).append(" = ").append(a.optString("display")).append("\n  ").append(a.optString("updatedAt")).append("  ").append(a.optString("source")).append("  raw=").append(a.optString("rawValueHex")).append("\n");}s.append('\n');}
        s.append("最近通信记录（最多 1000 条）\n");for(JSONObject e:events)s.append(e).append('\n');return s.toString();}
    void save(){AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"registry.json"));FileOutputStream out=null;try{JSONObject saved=new JSONObject();put(saved,"devices",new JSONArray(devices.values()));put(saved,"scenes",scenes);put(saved,"events",new JSONArray(events));out=file.startWrite();out.write(saved.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);loadError="保存失败："+e.getMessage();}}
    void load(){AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"registry.json"));try{JSONObject o=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));JSONArray list=o.optJSONArray("devices");if(list!=null)for(int i=0;i<list.length();i++){JSONObject d=list.getJSONObject(i);devices.put(d.getString("bleAddress"),d);}JSONArray a=o.optJSONArray("scenes");if(a!=null)scenes=a;JSONArray e=o.optJSONArray("events");if(e!=null)for(int i=Math.max(0,e.length()-1000);i<e.length();i++)events.add(e.getJSONObject(i));}catch(FileNotFoundException expected){}catch(Exception e){loadError="设备档案读取失败，原文件保留："+e.getMessage();}}
}
