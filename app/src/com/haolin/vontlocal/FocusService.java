package com.haolin.vontlocal;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;

/** Explicitly confirmed, foreground phone scheduler. Never resumes a killed session. */
public final class FocusService extends Service {
    static FocusService live;FocusPolicy.Session session;final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();PowerManager.WakeLock wake;
    public static boolean start(Context c,FocusPolicy.Settings settings,int officeBrightness,int officeTemperature){
        FocusPolicy.Session s=FocusStore.gate.begin(UUID.randomUUID().toString(),settings,officeBrightness,officeTemperature);if(s==null)return false;
        FocusStore.publish(c,s,"等待确认办公灯光");try{c.startForegroundService(new Intent(c,FocusService.class).putExtra("sessionId",s.id));return true;}catch(Exception e){FocusStore.end(c,"系统未允许后台提醒，请打开应用后重试","failed");return false;}
    }
    static void end(Context c,String reason){
        if(!FocusStore.running()){FocusStore.state(c);return;}
        FocusPolicy.Session stopped=FocusStore.end(c,reason,"stopped");if(stopped==null)return;
        LinkService.cancelOwnedFocus(stopped.id);FocusService service=live;if(service!=null)service.main.post(()->service.stopOwned(stopped.id));
    }
    @Override public void onCreate(){super.onCreate();live=this;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){String id=intent==null?"":intent.getStringExtra("sessionId");FocusPolicy.Session s=FocusStore.gate.current;if(s==null||!FocusStore.valid(id)){foreground();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);return START_NOT_STICKY;}if(session==s)return START_NOT_STICKY;session=s;
        foreground();acquire(120000);boolean accepted=LinkService.startFocusOffice(this,s);if(!accepted){fail(s,"办公操作未能启动，提醒未开始");return START_NOT_STICKY;}
        long deadline=SystemClock.elapsedRealtime()+LinkPolicy.TIMEOUT_MS+2000;main.post(()->awaitOffice(s,deadline));return START_NOT_STICKY;
    }
    boolean valid(FocusPolicy.Session s){return session==s&&FocusStore.gate.valid(s);}
    void awaitOffice(FocusPolicy.Session s,long deadline){if(!valid(s))return;JSONObject result=LinkStore.status(this);
        if(("focus:"+s.id).equals(result.optString("source"))&&result.optBoolean("completed")){
            boolean success=true;for(String k:new String[]{"vont","screenbar"}){JSONObject r=result.optJSONObject(k);success&=r!=null&&"success".equals(r.optString("status"));}
            if(!success){fail(s,"办公灯光或旧计时未全部确认，提醒未开始");return;}s.ready("working",SystemClock.elapsedRealtime(),System.currentTimeMillis());schedule(s);return;
        }
        if(SystemClock.elapsedRealtime()>deadline){fail(s,"办公准备超时，提醒未开始");return;}main.postDelayed(()->awaitOffice(s,deadline),150);
    }
    void schedule(FocusPolicy.Session s){if(!valid(s))return;FocusStore.publish(this,s,"已核对灯光");foreground();long delay=Math.max(1,s.deadlineElapsed-SystemClock.elapsedRealtime());acquire(delay+FocusPolicy.TRANSITION_TIMEOUT_MS+10000);main.postDelayed(()->boundary(s),delay);}
    void boundary(FocusPolicy.Session s){if(!valid(s))return;String next=s.due(SystemClock.elapsedRealtime());if(!s.active){fail(s,"提醒已延迟，循环已停止；请确认后重新开始");return;}if(next==null){schedule(s);return;}FocusStore.publish(this,s,"正在核对并调整灯光");foreground();long until=SystemClock.elapsedRealtime()+FocusPolicy.TRANSITION_TIMEOUT_MS;
        worker.execute(()->{try{synchronized(LocalHub.SCREENBAR_LOCK){FocusTransition.Guard guard=()->{if(!valid(s)||SystemClock.elapsedRealtime()>until)throw new IOException("提醒已结束、操作被替换或已超时");};guard.check();ScreenbarClient client=new ScreenbarClient(this);
                    FocusTransition.Lamp lamp=new FocusTransition.Lamp(){public FocusTransition.Light read()throws Exception{ScreenbarClient.State state=client.read();return new FocusTransition.Light(state.flag(2,1),state.number(2,2),state.number(2,3),state.flag(3,4),state.flag(3,18),state.number(3,9));}public void brightness(int b)throws Exception{guard.check();client.write(new ScreenbarPolicy.Change[]{new ScreenbarPolicy.Change(2,2,b)});}public void light(int b,int k)throws Exception{guard.check();client.write(new ScreenbarPolicy.Change[]{new ScreenbarPolicy.Change(2,2,b),new ScreenbarPolicy.Change(2,3,k)});}};
                    FocusTransition.change(s,next,lamp,guard,millis->{for(long left=millis;left>0;left-=50){guard.check();Thread.sleep(Math.min(50,left));}});
                }main.post(()->{if(valid(s)){s.ready(next,SystemClock.elapsedRealtime(),System.currentTimeMillis());schedule(s);}});
            }catch(Exception e){main.post(()->{if(valid(s))fail(s,e.getMessage()==null?"提醒未确认，循环已停止":e.getMessage());});}});
    }
    void foreground(){NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("focus-reminder","栖光 · 办公提醒",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,610,new Intent(this,HomeActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification notification=new Notification.Builder(this,"focus-reminder").setSmallIcon(R.drawable.ic_notification).setContentTitle("栖光 · "+FocusStore.title(this)).setContentText(FocusStore.detail(this)+" · 手机后台提醒").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,"结束提醒",FocusStopReceiver.pending(this)).build()).build();startForeground(61,notification);}
    void acquire(long timeout){if(wake==null){wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Qiguang:focus");wake.setReferenceCounted(false);}if(wake.isHeld())wake.release();wake.acquire(timeout);}
    void fail(FocusPolicy.Session s,String reason){if(session!=s)return;FocusStore.end(this,reason,"failed");LinkService.cancelOwnedFocus(s.id);stopOwned(s.id);}
    void stopOwned(String id){if(session!=null&&!session.id.equals(id))return;main.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){FocusPolicy.Session old=session;if(old!=null&&FocusStore.gate.valid(old)){FocusStore.end(this,"后台提醒已中断，请确认后重新开始","interrupted");LinkService.cancelOwnedFocus(old.id);}main.removeCallbacksAndMessages(null);worker.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();if(live==this)live=null;super.onDestroy();}
}
