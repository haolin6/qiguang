package com.haolin.vontlocal;

import android.app.Instrumentation;
import android.content.Context;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Installed only for acceptance; not included in the delivered application APK. */
public final class LocalAcceptance extends Instrumentation {
    Bundle args;LocalHub hub;
    final BleController.Listener listener=new BleController.Listener(){public void changed(){}public void notice(String s){}};
    @Override public void onCreate(Bundle a){args=a;start();}
    interface Work<T>{T call()throws Exception;}
    <T>T main(Work<T> task)throws Exception{AtomicReference<T> result=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();runOnMainSync(()->{try{result.set(task.call());}catch(Exception e){error.set(e);}});if(error.get()!=null)throw error.get();return result.get();}
    void waitFor(Work<Boolean> predicate,long milliseconds)throws Exception{long deadline=SystemClock.elapsedRealtime()+milliseconds;while(SystemClock.elapsedRealtime()<deadline){if(main(predicate))return;SystemClock.sleep(150);}throw new IOException("Acceptance wait timed out");}
    void connect()throws Exception{
        long epoch=main(()->{hub=LocalHub.get(getTargetContext());if(LocalHub.busy)throw new IOException("Room-light job is active");hub.add(listener);long old=hub.ble.verifiedReadEpoch;hub.ble.stopScan();hub.ble.connect(LinkPolicy.VONT);return old;});
        waitFor(()->hub.ble.ready&&hub.ble.verifiedReadEpoch>epoch&&hub.ble.pending==null&&hub.ble.commands.isEmpty(),26000);
    }
    JSONObject snapshot()throws Exception{return main(()->new JSONObject(hub.store.record(LinkPolicy.VONT).toString()));}
    void write(byte[] value)throws Exception{
        long[] old=main(()->new long[]{hub.ble.verifiedReadEpoch,hub.ble.verifiedWriteEpoch});
        main(()->{hub.ble.attributes(value,"受限验收写入");hub.ble.read();return true;});
        waitFor(()->hub.ble.verifiedWriteEpoch>old[1]&&hub.ble.verifiedReadEpoch>old[0]&&hub.ble.pending==null,15000);
    }
    JSONObject baseline()throws Exception{return new JSONObject(new String(ScreenbarClient.readBytes(new File(getTargetContext().getFilesDir(),"acceptance-vont-baseline.json")),StandardCharsets.UTF_8));}
    byte[] attr(JSONObject baseline,int id)throws Exception{JSONObject a=baseline.getJSONObject("attributes").getJSONObject(String.valueOf(id));return Protocol.attr(id,a.getInt("type"),Protocol.unhex(a.getString("rawValueHex")));}
    void restore()throws Exception{
        JSONObject target=baseline();JSONObject attributes=target.getJSONObject("attributes");
        if(target.optLong("productCode")!=300029||!LinkPolicy.VONT.equals(target.getString("bleAddress")))throw new IOException("Wrong restoration target");
        JSONObject now=snapshot();
        if(now.getJSONObject("attributes").getJSONObject("105").getString("rawValueHex").endsWith("01"))write(VontSleepCodec.cancel(Protocol.unhex(now.getJSONObject("attributes").getJSONObject("105").getString("rawValueHex"))));
        // Lighting transitions may reload remembered palette; restore exact values afterwards.
        write(Protocol.boolAttr(1,true));
        // Setting auxiliary power=0 also turns master power off; subsequent writes
        // are echoed but ignored by firmware. Keep channels powered until last.
        write(Protocol.byteAttr(24,3));
        int[] order={2,3,5,7,6,15,8,9,13,16,17,19,20,22,23,12,26,27,104,105,106,11,25,24};
        for(int id:order){
            JSONObject current=snapshot().getJSONObject("attributes").optJSONObject(String.valueOf(id));
            JSONObject wanted=attributes.optJSONObject(String.valueOf(id));
            if(wanted!=null&&current!=null&&!wanted.getString("rawValueHex").equals(current.getString("rawValueHex")))write(attr(target,id));
        }
        write(attr(target,1));
        JSONObject after=snapshot(),differences=new JSONObject();
        Iterator<String> keys=attributes.keys();while(keys.hasNext()){String key=keys.next();String before=attributes.getJSONObject(key).getString("rawValueHex");String actual=after.getJSONObject("attributes").getJSONObject(key).getString("rawValueHex");if(!before.equals(actual))differences.put(key,new JSONObject().put("before",before).put("after",actual));}
        output.put("restoredAll26",differences.length()==0).put("differences",differences).put("after",after);
        if(differences.length()!=0)throw new IOException("Full Vont restoration mismatch");
    }
    final JSONObject output=new JSONObject();
    @Override public void onStart(){
        int code=0;
        try{
            String command=args.getString("command","read");output.put("command",command).put("atEpochMs",System.currentTimeMillis());
            connect();output.put("before",snapshot());
            if("monitor".equals(command)){
                long until=Long.parseLong(args.getString("until","0"));long seconds=until-System.currentTimeMillis()/1000;
                if(seconds<1||seconds>180)throw new IOException("Monitor window must be 1..180 seconds");
                JSONArray samples=new JSONArray();
                while(System.currentTimeMillis()/1000<=until){
                    long mark=main(()->{long old=hub.ble.verifiedReadEpoch;hub.ble.read();return old;});
                    waitFor(()->hub.ble.verifiedReadEpoch>mark&&hub.ble.pending==null,6000);
                    JSONObject s=snapshot(),a=s.getJSONObject("attributes");
                    samples.put(new JSONObject().put("atEpochMs",System.currentTimeMillis()).put("power",a.getJSONObject("1").getBoolean("value"))
                        .put("brightness",a.getJSONObject("7").getInt("value")).put("warmLevelsHex",a.getJSONObject("26").getString("rawValueHex"))
                        .put("timerHex",a.getJSONObject("105").getString("rawValueHex")));
                    SystemClock.sleep(150);
                }
                output.put("samples",samples).put("readOnlyMonitor",true).put("after",snapshot());
            }else if("timer-start".equals(command)){
                // Original synchronizationTimeOnly(): preserve timezone using FFFFFFFF.
                String syncLabel="仅同步设备当前时间";String started=DeviceStore.iso(System.currentTimeMillis());
                main(()->{hub.ble.send(135,Protocol.concat(Protocol.number(System.currentTimeMillis()/1000,4),Protocol.unhex("FFFFFFFF")),syncLabel);return true;});
                waitFor(()->{for(JSONObject e:hub.store.events)if("reply".equals(e.optString("kind"))&&e.optString("message").startsWith(syncLabel)&&e.optString("at").compareTo(started)>=0&&hub.ble.pending==null)return true;return false;},10000);
                output.put("clockSynchronizedUsingOriginalTimezonePreservingCommand",true);
                long end=System.currentTimeMillis()/1000+600;
                write(Protocol.concat(Protocol.boolAttr(1,true),Protocol.byteAttr(7,10),VontSleepCodec.setting(end,10,true)));
                output.put("programmedEndEpochSeconds",end).put("durationMinutes",10).put("after",snapshot());
            }else if("timer-cancel".equals(command)){
                byte[] raw=Protocol.unhex(snapshot().getJSONObject("attributes").getJSONObject("105").getString("rawValueHex"));write(VontSleepCodec.cancel(raw));output.put("after",snapshot());
            }else if("restore".equals(command))restore();
            else if("read".equals(command))output.put("after",snapshot());
            else throw new IOException("Unknown acceptance command");
            output.put("status","passed");
        }catch(Exception e){code=1;try{output.put("status","failed").put("error",e.toString());}catch(Exception ignored){}}
        try{main(()->{if(hub!=null){hub.store.save();hub.ble.disconnect(false);hub.remove(listener);}return true;});
            ScreenbarClient.atomic(new File(getTargetContext().getFilesDir(),"acceptance-v05.json"),output.toString(2).getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){code=1;}
        Bundle result=new Bundle();result.putString("acceptance",output.toString());finish(code,result);
    }
}
