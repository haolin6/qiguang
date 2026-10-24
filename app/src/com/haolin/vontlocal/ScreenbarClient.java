package com.haolin.vontlocal;

import android.content.Context;
import android.net.*;
import android.security.keystore.*;
import android.util.AtomicFile;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Device-local MIoT transport and encrypted credential storage. No cloud traffic. */
final class ScreenbarClient {
    static final String MODEL="yeelink.light.lamp22";
    static final String KEY_ALIAS="screenbar.connection.v1",IMPORT="screenbar-import.json",SEALED="screenbar-credential.bin";
    static final String STATE="screenbar-state-v04.json",EVENTS="screenbar-operations.jsonl";
    static final int[][] KEYS={{2,1},{2,2},{2,3},{2,4},{2,5},{3,1},{3,2},{3,4},{3,6},{3,7},{3,8},{3,9},{3,13},{3,14},{3,15},{3,16},{3,17},{3,18},{3,19},{3,20},{3,21},{3,22},{3,23},{3,24},{3,25},{3,26},{3,27}};
    final Context context;final JSONObject credential;final byte[] token;int sequence=(int)(System.nanoTime()%1000000);
    long lastActionSentAtEpochMs,lastActionReplyAtEpochMs;
    static final class State {
        final JSONObject properties,information;final long readAt;
        State(JSONObject p,JSONObject i,long t){properties=p;information=i;readAt=t;}
        int number(int s,int p){return properties.optInt(s+"."+p,-1);}
        boolean flag(int s,int p){return properties.optBoolean(s+"."+p);}
        Object value(int s,int p){return properties.opt(s+"."+p);}
        String paired(){return properties.optString("3.27","");}
        JSONObject portable()throws Exception{
            JSONObject props=new JSONObject(properties.toString());String paired=paired();
            props.put("3.27",new JSONObject().put("pairedRemoteCount",paired.isEmpty()?0:paired.split(",").length)
                .put("sha256",hex(MessageDigest.getInstance("SHA-256").digest(paired.getBytes(StandardCharsets.UTF_8)))));
            return new JSONObject().put("schema","screenbar-state/v2").put("readAtEpochMs",readAt)
                .put("information",information).put("properties",props).put("transport","phone Wi-Fi / miIO UDP").put("authenticatedRead",true);
        }
    }
    ScreenbarClient(Context c)throws Exception{
        context=c.getApplicationContext();importIfPresent();credential=loadCredential();validateCredential(credential);token=unhex(credential.getString("token"));
    }
    static byte[] readBytes(File file)throws IOException{
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>65536)throw new IOException("文件过大");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    static void atomic(File file,byte[] value)throws IOException{
        AtomicFile target=new AtomicFile(file);FileOutputStream out=null;
        try{out=target.startWrite();out.write(value);target.finishWrite(out);}catch(IOException error){if(out!=null)target.failWrite(out);throw error;}
    }
    SecretKey storageKey()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(KEY_ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();
        }
        return (SecretKey)store.getKey(KEY_ALIAS,null);
    }
    static void validateCredential(JSONObject c)throws Exception{
        if(!DeviceTargets.validMac(c.optString("mac"))||!MODEL.equals(c.optString("model"))||!c.optString("token").matches("[0-9a-fA-F]{32}")||!DeviceTargets.privateAddress(c.optString("localip")))throw new IOException("连接配置与挂灯不匹配");
    }
    void importIfPresent()throws Exception{
        File source=new File(context.getFilesDir(),IMPORT);if(!source.exists())return;
        try{
            JSONObject c=new JSONObject(new String(readBytes(source),StandardCharsets.UTF_8));validateCredential(c);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,storageKey());
            byte[] data=cipher.doFinal(c.toString().getBytes(StandardCharsets.UTF_8));
            atomic(new File(context.getFilesDir(),SEALED),ByteBuffer.allocate(cipher.getIV().length+data.length).put(cipher.getIV()).put(data).array());
        }finally{if(source.exists()&&!source.delete())throw new IOException("临时连接配置清理失败");}
    }
    JSONObject loadCredential()throws Exception{
        byte[] bytes=readBytes(new File(context.getFilesDir(),SEALED));if(bytes.length<29)throw new IOException("连接配置损坏");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,storageKey(),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
        return new JSONObject(new String(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),StandardCharsets.UTF_8));
    }
    Network wifi()throws IOException{
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))return network;
        }
        throw new IOException("请连接挂灯所在的 Wi-Fi");
    }
    static byte[] md5(byte[]... chunks)throws Exception{MessageDigest d=MessageDigest.getInstance("MD5");for(byte[] chunk:chunks)d.update(chunk);return d.digest();}
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.US,"%02x",v&255));return b.toString();}
    static byte[] unhex(String text){byte[] b=new byte[text.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return b;}
    static byte[] crypt(byte[] bytes,byte[] token,boolean decrypt)throws Exception{
        byte[] key=md5(token);Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(decrypt?Cipher.DECRYPT_MODE:Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(md5(key,token)));return cipher.doFinal(bytes);
    }
    static byte[] packet(JSONObject request,byte[] token,byte[] device,int timestamp)throws Exception{
        byte[] bytes=crypt((request.toString()+"\0").getBytes(StandardCharsets.UTF_8),token,false);
        byte[] header=ByteBuffer.allocate(16).putShort((short)0x2131).putShort((short)(32+bytes.length)).putInt(0).put(device).putInt(timestamp).array();
        return ByteBuffer.allocate(32+bytes.length).put(header).put(md5(header,token,bytes)).put(bytes).array();
    }
    static JSONObject decode(byte[] packet,byte[] token,byte[] device,int id)throws Exception{
        if(packet.length<48||packet[0]!=0x21||packet[1]!=0x31||(((packet[2]&255)<<8)|(packet[3]&255))!=packet.length||!Arrays.equals(device,Arrays.copyOfRange(packet,8,12)))throw new IOException("挂灯应答不匹配");
        byte[] body=Arrays.copyOfRange(packet,32,packet.length);
        if(!MessageDigest.isEqual(Arrays.copyOfRange(packet,16,32),md5(Arrays.copyOfRange(packet,0,16),token,body)))throw new IOException("挂灯应答校验失败");
        byte[] plain=crypt(body,token,true);int end=plain.length;while(end>0&&plain[end-1]==0)end--;
        JSONObject reply=new JSONObject(new String(plain,0,end,StandardCharsets.UTF_8));
        if(reply.optInt("id",-1)!=id||reply.has("error"))throw new IOException("挂灯拒绝请求或应答过期");return reply;
    }
    static byte[] receive(DatagramSocket socket)throws IOException{
        byte[] b=new byte[8192];DatagramPacket packet=new DatagramPacket(b,b.length);socket.receive(packet);return Arrays.copyOf(b,packet.getLength());
    }
    JSONObject query(Network network,String method,Object params)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new IOException("操作已停止");
        if(!Arrays.asList("miIO.info","get_properties","set_properties","action").contains(method))throw new IOException("不支持的本地请求");
        try(DatagramSocket socket=new DatagramSocket()){
            network.bindSocket(socket);socket.connect(InetAddress.getByName(credential.getString("localip")),54321);socket.setSoTimeout(4000);
            byte[] hello=unhex("21310020ffffffffffffffffffffffffffffffffffffffffffffffffffffffff");socket.send(new DatagramPacket(hello,hello.length));
            byte[] response=receive(socket);if(response.length!=32||response[0]!=0x21||response[1]!=0x31||response[2]!=0||response[3]!=32)throw new IOException("挂灯握手失败");
            byte[] device=Arrays.copyOfRange(response,8,12);int stamp=ByteBuffer.wrap(response,12,4).getInt()+1;int id=++sequence;
            byte[] data=packet(new JSONObject().put("id",id).put("method",method).put("params",params),token,device,stamp);
            if("action".equals(method))lastActionSentAtEpochMs=System.currentTimeMillis();
            socket.send(new DatagramPacket(data,data.length));JSONObject decoded=decode(receive(socket),token,device,id);
            if("action".equals(method))lastActionReplyAtEpochMs=System.currentTimeMillis();return decoded;
        }
    }
    JSONObject identity(Network network)throws Exception{
        JSONObject info=query(network,"miIO.info",new JSONArray()).getJSONObject("result");
        if(!MODEL.equals(info.optString("model"))||!credential.getString("mac").equalsIgnoreCase(info.optString("mac")))throw new IOException("当前地址不是已授权挂灯");
        return new JSONObject().put("model",MODEL).put("mac",credential.getString("mac")).put("firmware",info.optString("fw_ver")).put("hardware",info.optString("hw_ver")).put("ip",credential.getString("localip"));
    }
    State read()throws Exception{
        Network network=wifi();JSONObject info=identity(network),props=new JSONObject();
        for(int start=0;start<KEYS.length;start+=8){
            JSONArray requests=new JSONArray();int end=Math.min(KEYS.length,start+8);
            for(int i=start;i<end;i++)requests.put(new JSONObject().put("did",credential.getString("did")).put("siid",KEYS[i][0]).put("piid",KEYS[i][1]));
            JSONArray replies=query(network,"get_properties",requests).getJSONArray("result");
            if(replies.length()!=end-start)throw new IOException("挂灯设置读取不完整");
            for(int i=0;i<replies.length();i++){
                JSONObject r=replies.getJSONObject(i);if(r.optInt("code",-1)!=0||!r.has("value"))throw new IOException("部分挂灯设置无法读取");
                int s=r.getInt("siid"),p=r.getInt("piid");boolean expected=false;for(int j=start;j<end;j++)if(KEYS[j][0]==s&&KEYS[j][1]==p)expected=true;
                if(!expected||props.has(s+"."+p))throw new IOException("挂灯属性应答不匹配");props.put(s+"."+p,r.get("value"));
            }
        }
        if(props.length()!=KEYS.length)throw new IOException("挂灯设置读取不完整");
        for(int[] key:KEYS)if(!(key[0]==3&&key[1]==27)&&!(key[0]==2&&key[1]==5))ScreenbarPolicy.validate(key[0],key[1],props.get(key[0]+"."+key[1]));
        State state=new State(props,info,System.currentTimeMillis());atomic(new File(context.getFilesDir(),STATE),state.portable().toString(2).getBytes(StandardCharsets.UTF_8));return state;
    }
    JSONArray write(ScreenbarPolicy.Change[] changes)throws Exception{
        if(changes.length<1||changes.length>8)throw new IllegalArgumentException("设置数量不正确");
        Network network=wifi();identity(network);JSONArray params=new JSONArray();
        for(ScreenbarPolicy.Change change:changes){new ScreenbarPolicy.Change(change.service,change.property,change.value);
            params.put(new JSONObject().put("did",credential.getString("did")).put("siid",change.service).put("piid",change.property).put("value",change.value));}
        JSONArray replies=query(network,"set_properties",params).getJSONArray("result");
        if(replies.length()!=changes.length)throw new IOException("部分设置未获得回执");
        for(int i=0;i<replies.length();i++){
            JSONObject r=replies.getJSONObject(i);ScreenbarPolicy.Change change=changes[i];
            if(r.optInt("code",-1)!=0||r.optInt("siid")!=change.service||r.optInt("piid")!=change.property)throw new IOException("部分设置未完成，请核对读取结果");
        }
        return replies;
    }
    JSONObject call(ScreenbarPolicy.Call call)throws Exception{
        Network network=wifi();identity(network);JSONArray args=new JSONArray();
        for(int i=0;i<call.inputs.length;i++)args.put(new JSONObject().put("piid",call.inputs[i]).put("value",call.values[i]));
        JSONObject reply=query(network,"action",new JSONObject().put("did",credential.getString("did")).put("siid",call.service).put("aiid",call.action).put("in",args)).getJSONObject("result");
        if(reply.optInt("code",-1)!=0)throw new IOException("挂灯未确认该操作");return reply;
    }
    void record(JSONObject value){
        try{
            File file=new File(context.getFilesDir(),EVENTS);if(file.length()>350000){atomic(file,new byte[0]);}
            try(FileOutputStream out=new FileOutputStream(file,true)){out.write((value.toString()+"\n").getBytes(StandardCharsets.UTF_8));}
        }catch(Exception ignored){}
    }
}
