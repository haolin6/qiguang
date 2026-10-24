package com.haolin.vontlocal;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/** Short explicit jobs; native timers outlive the phone service. */
public final class LinkService extends Service implements BleController.Listener {
    static final String PREFIX="com.haolin.vontlocal.link.";
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService wifiWorker=Executors.newSingleThreadExecutor();
    final LinkPolicy.Gate gate=new LinkPolicy.Gate();
    static LinkService live;LocalHub hub;Job job;PowerManager.WakeLock wake;int latestStartId;
    static final class Job {
        final LinkPolicy.Request token;final LinkPolicy.Settings settings;final JSONObject status=new JSONObject();
        boolean vontDone,screenbarDone,vontPrepared,screenbarPrepared,arming;
        int vontStep,connectAttempts;long readMark,writeMark,clockMark,stageDeadline,endSeconds,lastConnectAt;
        boolean beforePower,beforeTimerEnabled;
        Job(LinkPolicy.Request token,LinkPolicy.Settings settings,String source){
            this.token=token;this.settings=settings;
            put(status,"schema","two-light-operation/v2");put(status,"appVersion","0.8.3");
            put(status,"requestId",UUID.randomUUID().toString());put(status,"action",token.action);
            put(status,"source",source);put(status,"startedAtEpochMs",System.currentTimeMillis());
            put(status,"parameters",LinkStore.parameters(settings));put(status,"completed",false);
            put(status,"vont",result("pending","等待连接",null));put(status,"screenbar",result("pending","等待连接",null));
        }
    }
    static void put(JSONObject j,String k,Object v){DeviceStore.put(j,k,v);}
    static JSONObject result(String state,String message,JSONObject observed){JSONObject j=new JSONObject();put(j,"status",state);put(j,"message",message);if(observed!=null)put(j,"observed",observed);put(j,"atEpochMs",System.currentTimeMillis());return j;}
    public static boolean start(Context context,String action,String source,boolean retry){
        if(!LinkPolicy.allowed(action))return false;
        if((LinkPolicy.scene(action)||"off".equals(action))&&(source==null||!source.startsWith("focus:")))FocusService.end(context,"场景已切换，办公提醒结束");
        Intent i=new Intent(context,LinkService.class).setAction(PREFIX+action).putExtra("source",source).putExtra("retryFailed",retry);
        try{context.startForegroundService(i);return true;}catch(Exception e){android.widget.Toast.makeText(context,"系统未允许本次桌面控制，请打开灯控重试",1).show();return false;}
    }
    @Override public void onCreate(){super.onCreate();live=this;hub=LocalHub.get(this);hub.add(this);}
    @Override public IBinder onBind(Intent i){return null;}
    void foreground(String action){
        NotificationManager m=getSystemService(NotificationManager.class);
        m.createNotificationChannel(new NotificationChannel("room-light-control","栖光 · 灯光控制",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,90,new Intent(this,HomeActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"room-light-control").setSmallIcon(R.drawable.ic_notification).setContentTitle("正在执行"+LinkPolicy.name(action))
            .setContentText("正在连接灯具并确认结果").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
        startForeground(50,n);
        if(wake==null){wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VontLocal:room-light");wake.setReferenceCounted(false);}
        wake.acquire(LinkPolicy.TIMEOUT_MS+5000);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        latestStartId=startId;String action=intent==null?"":intent.getAction();
        if(action==null||!action.startsWith(PREFIX)||!LinkPolicy.allowed(action.substring(PREFIX.length()))){if(job==null){foreground("work");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}return START_NOT_STICKY;}
        action=LinkPolicy.canonical(action.substring(PREFIX.length()));String source=intent.getStringExtra("source");
        if(source!=null&&source.startsWith("focus:")&&!FocusStore.valid(source.substring(6))){if(job==null){foreground("work");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}return START_NOT_STICKY;}
        if((LinkPolicy.scene(action)||"off".equals(action))&&(source==null||!source.startsWith("focus:")))FocusService.end(this,"场景已切换，办公提醒结束");
        LinkPolicy.Request request=gate.begin(action,SystemClock.elapsedRealtime());
        if(request==null){if(job!=null)foreground(job.token.action);return START_NOT_STICKY;}
        foreground(action);
        if(job!=null&&!job.status.optBoolean("completed")){put(job.status,"interruptedBy",action);put(job.status,"completed",true);put(job.status,"completedAtEpochMs",System.currentTimeMillis());LinkStore.record(this,job.status);}
        JSONObject old=LinkStore.status(this);boolean retry=intent.getBooleanExtra("retryFailed",false)&&old.optBoolean("completed")&&action.equals(old.optString("action"));
        LinkPolicy.Settings settings=LinkPolicy.scene(action)?SceneStore.settings(this,action):LinkStore.settings(this);
        if(source!=null&&source.startsWith("focus:")){FocusPolicy.Session f=FocusStore.gate.current;settings=new LinkPolicy.Settings(settings.warm1,settings.warm2,f.officeBrightness,f.officeTemperature,settings.minutes);}
        if(retry){JSONObject p=old.optJSONObject("parameters");if(p!=null)settings=new LinkPolicy.Settings(p.optInt("vontWarm1",settings.warm1),p.optInt("vontWarm2",settings.warm2),p.optInt("screenbarBrightness",settings.brightness),p.optInt("screenbarTemperature",settings.temperature),p.optInt("minutes",settings.minutes));}
        LinkPolicy.VONT=DeviceTargets.vont(this,hub.store);
        Job current=new Job(request,settings,intent.getStringExtra("source"));job=current;
        LocalHub.busy=true;hub.ble.stopScan();hub.ble.disconnect(false);hub.changed();
        if(LinkPolicy.scene(action)){JSONObject p=new JSONObject();put(p,"state","preparing");put(p,"requestId",current.status.optString("requestId"));put(p,"minutes",settings.minutes);LinkStore.plan(this,p);}
        if(retry){put(current.status,"retryOf",old.optString("requestId"));put(current.status,"retryScope",LinkPolicy.bed(action)?"both-new-countdown":"failed-only");
            if(!LinkPolicy.bed(action))for(String key:new String[]{"vont","screenbar"}){JSONObject part=old.optJSONObject(key);if(part!=null&&"success".equals(part.optString("status"))){put(current.status,key,part);if("vont".equals(key))current.vontDone=true;else current.screenbarDone=true;}}
        }
        LinkStore.publish(this,current.status);
        // Give Android's GATT stack time to release a superseded connection.
        if(!current.vontDone)main.postDelayed(()->{if(valid(current))startVont(current);},600);
        if(!current.screenbarDone)wifiWorker.execute(()->screenbarPrepare(current));
        main.postDelayed(()->timeout(current),LinkPolicy.TIMEOUT_MS+50);finishIfReady(current);return START_NOT_STICKY;
    }
    static boolean startFocusOffice(Context c,FocusPolicy.Session s){return start(c,"work","focus:"+s.id,false);}
    static void cancelOwnedFocus(String id){LinkService service=live;if(service!=null)service.main.post(()->service.abortFocus(id));}
    void abortFocus(String id){Job t=job;if(t==null||t.status.optBoolean("completed")||!("focus:"+id).equals(t.status.optString("source")))return;gate.finish(t.token);put(t.status,"completed",true);put(t.status,"interruptedBy","focus-end");put(t.status,"completedAtEpochMs",System.currentTimeMillis());for(String key:new String[]{"vont","screenbar"})if(!"success".equals(t.status.optJSONObject(key).optString("status")))put(t.status,key,result("canceled","已结束，不再发送后续指令",null));t.vontDone=t.screenbarDone=true;hub.ble.disconnect(false);LinkStore.record(this,t.status);LinkStore.publish(this,t.status);LocalHub.busy=false;hub.changed();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    boolean valid(Job t){String source=t.status.optString("source");return t==job&&gate.valid(t.token,SystemClock.elapsedRealtime())&&(!source.startsWith("focus:")||FocusStore.valid(source.substring(6)));}
    void guard(Job t)throws IOException{if(!valid(t))throw new IOException("请求已取消或过期");}
    void startVont(Job t){
        JSONObject r=hub.store.devices.get(LinkPolicy.VONT);
        if(r==null||r.optLong("productCode")!=300029||!"TLB01".equals(r.optString("modelFromName"))){vontFailure(t,"请先在设备页连接这组Vont");return;}
        if(!hub.ble.permitted()||hub.ble.adapter==null||!hub.ble.adapter.isEnabled()){vontFailure(t,"请打开蓝牙并允许附近设备权限");return;}
        t.readMark=hub.ble.verifiedReadEpoch;t.stageDeadline=SystemClock.elapsedRealtime()+21000;t.connectAttempts=1;t.lastConnectAt=SystemClock.elapsedRealtime();hub.ble.connect(LinkPolicy.VONT);pollVont(t);
    }
    JSONObject vontSnapshot()throws Exception{return new JSONObject(hub.store.record(LinkPolicy.VONT).toString());}
    byte[] timerRaw(){JSONObject a=hub.store.record(LinkPolicy.VONT).optJSONObject("attributes");JSONObject v=a==null?null:a.optJSONObject("105");return v==null?null:Protocol.unhex(v.optString("rawValueHex"));}
    boolean idle(){BleController b=hub.ble;return b.ready&&b.sessionRead&&b.pending==null&&b.commands.isEmpty()&&!b.writing;}
    String vontDescription(){DeviceStore s=hub.store;if(s.integer(LinkPolicy.VONT,1,0)==0)return "已关灯";if(s.integer(LinkPolicy.VONT,25,0)==3)return "暖光 "+((s.integer(LinkPolicy.VONT,26,0)>>8)&255)+" / "+(s.integer(LinkPolicy.VONT,26,0)&255);return "已开启 · "+s.integer(LinkPolicy.VONT,7,0)+"%";}
    boolean warmMatches(Job t){DeviceStore s=hub.store;return s.integer(LinkPolicy.VONT,1,0)==1&&s.integer(LinkPolicy.VONT,2,-1)==2&&s.integer(LinkPolicy.VONT,7,-1)==100&&s.integer(LinkPolicy.VONT,24,-1)==3&&s.integer(LinkPolicy.VONT,25,-1)==3&&s.integer(LinkPolicy.VONT,26,-1)==((t.settings.warm1<<8)|t.settings.warm2);}
    void pollVont(Job t){
        if(!valid(t)||t.vontDone||t.vontPrepared&&t.vontStep!=2)return;
        if(SystemClock.elapsedRealtime()>t.stageDeadline){vontFailure(t,"Vont连接或设备回读超时");return;}
        BleController b=hub.ble;
        // A failed initial connection has not sent a light or timer write.
        // Retry its connection once, within this request's original deadline.
        if(t.vontStep==0&&!b.ready&&b.gatt==null&&t.connectAttempts<2&&b.adapter!=null&&b.adapter.isEnabled()&&SystemClock.elapsedRealtime()-t.lastConnectAt>=2000){
            t.connectAttempts++;t.lastConnectAt=SystemClock.elapsedRealtime();t.readMark=b.verifiedReadEpoch;b.connect(LinkPolicy.VONT);put(t.status,"vontConnectAttempts",t.connectAttempts);
        }
        if(idle()&&b.verifiedReadEpoch>t.readMark)try{
            if(t.vontStep==0){
                for(int id:new int[]{1,2,7,15,24,25,26,105})if(!hub.store.lastReadContains(LinkPolicy.VONT,id))throw new IOException("Vont完整属性不足");
                JSONObject before=vontSnapshot();put(t.status,"vontBefore",before);byte[] raw=timerRaw();if(raw==null||raw.length!=6)throw new IOException("Vont睡眠参数不完整");
                t.beforePower=hub.store.integer(LinkPolicy.VONT,1,0)==1;t.beforeTimerEnabled=VontSleepCodec.enabled(raw);
                if("refresh".equals(t.token.action)){complete(t,"vont",result("success",vontDescription(),before));return;}
                if("cancel".equals(t.token.action)&&!t.beforeTimerEnabled){complete(t,"vont",result("success","延时已取消",before));return;}
                if("cancel".equals(t.token.action)&&!t.beforePower)throw new IOException("灯已关闭，灯内旧计时取消未确认");
                byte[] payload;
                if(LinkPolicy.bed(t.token.action))payload=Protocol.concat(LinkPolicy.warm(t.settings,hub.store.segmentColors(LinkPolicy.VONT)),VontSleepCodec.cancel(raw));
                else if("cancel".equals(t.token.action))payload=VontSleepCodec.cancel(raw);
                else payload=Protocol.concat(t.beforePower&&t.beforeTimerEnabled?VontSleepCodec.cancel(raw):new byte[0],LinkPolicy.off());
                guard(t);t.readMark=b.verifiedReadEpoch;t.writeMark=b.verifiedWriteEpoch;t.clockMark=b.verifiedClockEpoch;t.vontStep=1;t.stageDeadline=SystemClock.elapsedRealtime()+15000;
                b.attributes(payload,LinkPolicy.name(t.token.action));
                if(LinkPolicy.bed(t.token.action))b.send(135,VontSleepCodec.clock(System.currentTimeMillis()/1000),"同步当前时间并保留时区");
                b.read();
            }else if(b.verifiedWriteEpoch>t.writeMark){
                JSONObject observed=vontSnapshot();byte[] raw=timerRaw();
                if(t.vontStep==2){
                    if(hub.store.integer(LinkPolicy.VONT,1,0)!=1||!VontSleepCodec.matches(raw,t.endSeconds,t.settings.minutes))throw new IOException("Vont延时关灯回读不一致");
                    JSONObject r=result("success","暖光 · 延时已设",observed);put(r,"timerEndEpochSeconds",t.endSeconds);complete(t,"vont",r);return;
                }
                if(LinkPolicy.bed(t.token.action)){
                    if(b.verifiedClockEpoch<=t.clockMark||!warmMatches(t)||VontSleepCodec.enabled(raw))throw new IOException("Vont暖光或旧计时取消回读不一致");
                    prepared(t,"vont",result("ready","暖光就绪，等待计时",observed));return;
                }
                if("cancel".equals(t.token.action)){
                    if(VontSleepCodec.enabled(raw))throw new IOException("Vont仍报告旧计时启用");complete(t,"vont",result("success","延时已取消",observed));return;
                }
                if(hub.store.integer(LinkPolicy.VONT,1,-1)!=0)throw new IOException("Vont关灯回读不一致");
                complete(t,"vont",result(VontSleepCodec.enabled(raw)?"failed":"success",VontSleepCodec.enabled(raw)?"灯已关，旧计时取消未确认":"已关灯",observed));return;
            }
        }catch(Exception e){vontFailure(t,message(e));return;}
        main.postDelayed(()->pollVont(t),120);
    }
    static String message(Exception e){return e.getMessage()==null?"本地通信未完成":e.getMessage();}
    void vontFailure(Job t,String message){
        if(!valid(t))return;hub.ble.disconnect(false);
        if(LinkPolicy.bed(t.token.action)&&t.arming){
            t.vontDone=true;put(t.status,"vont",result("failed",message+"；灯内计时未确认，请取消延时或全关",null));t.screenbarDone=false;
            wifiWorker.execute(()->rollbackScreenbar(t,message));
        }else if(LinkPolicy.bed(t.token.action))prepared(t,"vont",result("failed",message,null));
        else complete(t,"vont",result("failed",message,null));
    }
    void sbWrite(Job t,ScreenbarClient c,ScreenbarPolicy.Change... changes)throws Exception{guard(t);c.write(changes);}
    void screenbarPrepare(Job t){
        JSONObject outcome;
        synchronized(LocalHub.SCREENBAR_LOCK){try{
            guard(t);ScreenbarClient c=new ScreenbarClient(this);ScreenbarClient.State before=c.read();guard(t);String action=t.token.action;
            if(LinkPolicy.scene(action)){
                if(!before.flag(2,1))sbWrite(t,c,new ScreenbarPolicy.Change(2,1,true));
                ScreenbarClient.State active=c.read();guard(t);
                if(active.flag(3,4))sbWrite(t,c,new ScreenbarPolicy.Change(3,4,false));
                if(active.flag(3,18))sbWrite(t,c,new ScreenbarPolicy.Change(3,18,false));
                if(active.number(3,9)!=0)sbWrite(t,c,new ScreenbarPolicy.Change(3,9,0));
                sbWrite(t,c,new ScreenbarPolicy.Change(2,2,t.settings.brightness),new ScreenbarPolicy.Change(2,3,t.settings.temperature));
            }else if("off".equals(action)){
                if(before.flag(3,4))sbWrite(t,c,new ScreenbarPolicy.Change(3,4,false));
                if(before.flag(3,18))sbWrite(t,c,new ScreenbarPolicy.Change(3,18,false));
                if(before.flag(2,1))sbWrite(t,c,new ScreenbarPolicy.Change(2,1,false));
            }else if("cancel".equals(action)&&before.flag(3,4))sbWrite(t,c,new ScreenbarPolicy.Change(3,4,false));
            guard(t);ScreenbarClient.State after=c.read();guard(t);
            boolean good="refresh".equals(action)||("off".equals(action)?!after.flag(2,1)&&!after.flag(3,4)&&!after.flag(3,18):"cancel".equals(action)?!after.flag(3,4):after.flag(2,1)&&after.number(2,2)==t.settings.brightness&&after.number(2,3)==t.settings.temperature&&after.number(3,9)==0&&!after.flag(3,4)&&!after.flag(3,18));
            String description="cancel".equals(action)?"延时已取消":after.flag(2,1)?after.number(2,2)+"% · "+after.number(2,3)+"K":"已关灯";
            outcome=result(good?(LinkPolicy.bed(action)?"ready":"success"):"failed",good?description:"挂灯回读与目标不一致",after.portable());
        }catch(Exception e){outcome=result("failed",message(e),null);}}
        JSONObject ready=outcome;main.post(()->{if(valid(t)){if(LinkPolicy.bed(t.token.action))prepared(t,"screenbar",ready);else complete(t,"screenbar",ready);}});
    }
    void prepared(Job t,String component,JSONObject outcome){
        if(!valid(t))return;if("vont".equals(component)){if(t.vontPrepared)return;t.vontPrepared=true;}else{if(t.screenbarPrepared)return;t.screenbarPrepared=true;}
        put(t.status,component,outcome);LinkStore.publish(this,t.status);
        if(!t.vontPrepared||!t.screenbarPrepared)return;
        boolean ready=true;for(String key:new String[]{"vont","screenbar"})ready&="ready".equals(t.status.optJSONObject(key).optString("status"));
        if(!ready){
            for(String key:new String[]{"vont","screenbar"}){JSONObject r=t.status.optJSONObject(key);if("ready".equals(r.optString("status")))put(t.status,key,result("failed","暖光已设，联动延时未启动",r.optJSONObject("observed")));}
            t.vontDone=t.screenbarDone=true;hub.ble.disconnect(false);finishIfReady(t);return;
        }
        t.arming=true;put(t.status,"phase","arming-native-timers");LinkStore.publish(this,t.status);wifiWorker.execute(()->armScreenbar(t));
    }
    void armScreenbar(Job t){
        synchronized(LocalHub.SCREENBAR_LOCK){try{
            guard(t);ScreenbarClient c=new ScreenbarClient(this);guard(t);c.call(new ScreenbarPolicy.Call(3,2,t.settings.minutes));guard(t);
            long end=LinkPolicy.commonEnd(c.lastActionSentAtEpochMs,c.lastActionReplyAtEpochMs,t.settings.minutes);
            ScreenbarClient.State after=c.read();guard(t);
            if(!after.flag(2,1)||!after.flag(3,4)||after.number(3,2)!=t.settings.minutes)throw new IOException("挂灯延时关灯回读不一致");
            JSONObject r=result("success","暖光 · 延时已设",after.portable());put(r,"timerEndEpochSeconds",end);put(r,"nativeTimerSentAtEpochMs",c.lastActionSentAtEpochMs);put(r,"nativeTimerReplyAtEpochMs",c.lastActionReplyAtEpochMs);
            main.post(()->{if(!valid(t))return;t.endSeconds=end;put(t.status,"endEpochSeconds",end);put(t.status,"screenbar",r);t.screenbarDone=true;
                BleController b=hub.ble;if(!b.ready){vontFailure(t,"Vont在计时设置前断开");return;}
                t.vontStep=2;t.readMark=b.verifiedReadEpoch;t.writeMark=b.verifiedWriteEpoch;t.stageDeadline=SystemClock.elapsedRealtime()+12000;
                b.attributes(VontSleepCodec.setting(end,t.settings.minutes,true),"设置共同结束时间");b.read();pollVont(t);});
        }catch(Exception e){String error=message(e);main.post(()->{if(valid(t)){t.vontDone=true;put(t.status,"vont",result("failed","未启动联动计时",t.status.optJSONObject("vont").optJSONObject("observed")));}});rollbackScreenbar(t,error);}}
    }
    void rollbackScreenbar(Job t,String reason){
        JSONObject r;
        synchronized(LocalHub.SCREENBAR_LOCK){try{
            guard(t);ScreenbarClient c=new ScreenbarClient(this);ScreenbarClient.State s=c.read();guard(t);
            if(s.flag(3,4))sbWrite(t,c,new ScreenbarPolicy.Change(3,4,false));s=c.read();guard(t);
            if(s.flag(3,4))throw new IOException("挂灯仍报告计时启用");
            r=result("failed","联动未完成，挂灯延时已取消",s.portable());put(r,"rollbackConfirmed",true);put(r,"reason",reason);
        }catch(Exception e){r=result("failed","联动未完成，挂灯计时取消未确认",null);put(r,"reason",reason);put(r,"rollbackError",message(e));}}
        JSONObject outcome=r;main.post(()->{if(valid(t)){t.vontDone=true;t.screenbarDone=true;put(t.status,"screenbar",outcome);finishIfReady(t);}});
    }
    void complete(Job t,String component,JSONObject outcome){
        if(t!=job||t.status.optBoolean("completed"))return;
        if("vont".equals(component)){if(t.vontDone)return;t.vontDone=true;hub.ble.disconnect(false);hub.store.save();}else{if(t.screenbarDone)return;t.screenbarDone=true;}
        put(t.status,component,outcome);LinkStore.publish(this,t.status);finishIfReady(t);
    }
    void updatePlan(Job t){
        boolean success=true;for(String k:new String[]{"vont","screenbar"})success&="success".equals(t.status.optJSONObject(k).optString("status"));
        JSONObject p=LinkStore.plan(this);String a=t.token.action;
        if(LinkPolicy.bed(a)){p=new JSONObject();put(p,"state",success?"armed":"failed");put(p,"requestId",t.status.optString("requestId"));put(p,"minutes",t.settings.minutes);put(p,"endEpochSeconds",t.endSeconds);put(p,"setAtEpochMs",System.currentTimeMillis());}
        else if(LinkPolicy.scene(a)){p=new JSONObject();put(p,"state",success?"idle":"failed");put(p,"sceneId",a);put(p,"requestId",t.status.optString("requestId"));}
        else if("off".equals(a)||"cancel".equals(a)){put(p,"state",success?("off".equals(a)?"off-confirmed":"canceled"):"failed");}
        else if(success&&"refresh".equals(a)){
            JSONObject v=t.status.optJSONObject("vont").optJSONObject("observed"),s=t.status.optJSONObject("screenbar").optJSONObject("observed");
            JSONObject va=v==null?null:v.optJSONObject("attributes"),sp=s==null?null:s.optJSONObject("properties");
            if(va!=null&&sp!=null)try{
                boolean on=va.getJSONObject("1").getBoolean("value"),sbOn=sp.getBoolean("2.1");byte[] raw=Protocol.unhex(va.getJSONObject("105").getString("rawValueHex"));
                boolean nativeActive=VontSleepCodec.enabled(raw)||sp.getBoolean("3.4");
                if(!on&&!sbOn)put(p,"state","off-confirmed");
                else if("armed".equals(p.optString("state"))){
                    if(System.currentTimeMillis()/1000>=p.optLong("endEpochSeconds"))put(p,"state","overdue");
                    else if(!VontSleepCodec.matches(raw,p.optLong("endEpochSeconds"),p.optInt("minutes"))||!sp.getBoolean("3.4")||sp.getInt("3.2")!=p.optInt("minutes"))put(p,"state","changed");
                }else if(nativeActive)put(p,"state","changed");
                else if(!"canceled".equals(p.optString("state")))put(p,"state","idle");
            }catch(Exception ignored){}
        }
        put(p,"lastCheckedAtEpochMs",System.currentTimeMillis());LinkStore.plan(this,p);
    }
    void finishIfReady(Job t){
        if(t!=job||t.status.optBoolean("completed")||!t.vontDone||!t.screenbarDone)return;
        gate.finish(t.token);put(t.status,"completed",true);put(t.status,"completedAtEpochMs",System.currentTimeMillis());updatePlan(t);
        LinkStore.record(this,t.status);LinkStore.publish(this,t.status);LocalHub.busy=false;hub.changed();
        if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(latestStartId);
    }
    void timeout(Job t){if(t!=job||t.status.optBoolean("completed"))return;
        if(!t.vontDone)put(t.status,"vont",result("failed",t.arming?"计时设置未确认，请取消延时或全关":"连接或执行超时",null));
        if(!t.screenbarDone)put(t.status,"screenbar",result("failed",t.arming?"计时设置未确认，请取消延时或全关":"连接或执行超时",null));
        t.vontDone=t.screenbarDone=true;hub.ble.disconnect(false);finishIfReady(t);
    }
    @Override public void changed(){}
    @Override public void notice(String message){}
    @Override public void onDestroy(){
        if(job!=null&&!job.status.optBoolean("completed")){
            gate.finish(job.token);put(job.status,"completed",true);put(job.status,"interrupted",true);
            if(!job.vontDone)put(job.status,"vont",result("failed","任务中断；灯内计时可能仍存在",null));
            if(!job.screenbarDone)put(job.status,"screenbar",result("failed","任务中断；灯内计时可能仍存在",null));
            LinkStore.record(this,job.status);LinkStore.publish(this,job.status);
        }
        if(live==this)live=null;LocalHub.busy=false;main.removeCallbacksAndMessages(null);wifiWorker.shutdownNow();hub.ble.disconnect(false);hub.remove(this);hub.changed();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();
    }
}
