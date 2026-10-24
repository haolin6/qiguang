package com.haolin.vontlocal;

import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class LinkStore {
    static final String STATUS="link-status.json",EVENTS="link-events.jsonl",PLAN="countdown-plan.json";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("sleep-settings-v1",0);}
    static LinkPolicy.Settings settings(Context c){return SceneStore.settings(c,"bed");}
    static void saveSettings(Context c,LinkPolicy.Settings s){SceneStore.save(c,"bed",s);}
    static JSONObject parameters(LinkPolicy.Settings s){JSONObject p=new JSONObject();DeviceStore.put(p,"vontWarm1",s.warm1);DeviceStore.put(p,"vontWarm2",s.warm2);DeviceStore.put(p,"screenbarBrightness",s.brightness);DeviceStore.put(p,"screenbarTemperature",s.temperature);DeviceStore.put(p,"minutes",s.minutes);return p;}
    static JSONObject plan(Context c){try{return new JSONObject(new String(ScreenbarClient.readBytes(new File(c.getFilesDir(),PLAN)),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    static void plan(Context c,JSONObject p){try{ScreenbarClient.atomic(new File(c.getFilesDir(),PLAN),p.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception e){android.util.Log.e("RoomLight","Countdown plan persistence failed");}}
    static String time(long millis){return new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.CHINA).format(new java.util.Date(millis));}
    static String planDescription(Context c){JSONObject p=plan(c);String state=p.optString("state");if("armed".equals(state)){if(System.currentTimeMillis()/1000>=p.optLong("endEpochSeconds"))return "预计时间已到 · 点刷新确认";return "预计 "+time(p.optLong("endEpochSeconds")*1000)+" 两组关灯";}if("off-confirmed".equals(state))return "已回读确认两组关闭";if("canceled".equals(state))return "两组延时已取消";if("changed".equals(state))return "灯内计时已有变动，请重新设置或全关";if("overdue".equals(state))return "预计时间已到，当前仍有灯开启";if("preparing".equals(state))return "正在准备场景";if("failed".equals(state))return "操作未全部确认，请查看两组结果";return "";}
    static String compact(JSONObject r){if(r==null)return "未确认";String state=r.optString("status");if("failed".equals(state))return "未确认，点标题查看";if(!"success".equals(state))return "执行中，尚未确认";return r.optString("message","已确认");}
    static String widgetSummary(Context c){
        JSONObject s=status(c);if(s.length()==0)return "选一个场景，准备好光";
        boolean failed=false;for(String k:new String[]{"vont","screenbar"}){JSONObject r=s.optJSONObject(k);failed|=r!=null&&"failed".equals(r.optString("status"));}
        if(!s.optBoolean("completed"))return System.currentTimeMillis()-s.optLong("startedAtEpochMs")>LinkPolicy.TIMEOUT_MS+5000?"操作已中断 · 点此刷新":"正在设置 · "+LinkPolicy.name(s.optString("action"));
        if(failed)return "部分未确认 · 点标题查看";
        String d=planDescription(c);if(!d.isEmpty())return d;
        return "上次设置 · "+LinkPolicy.name(s.optString("action"))+"  "+time(s.optLong("completedAtEpochMs"));
    }
    static String summary(Context c){String p=planDescription(c);return (p.isEmpty()?"":p+"\n")+summary(status(c));}
    static JSONObject status(Context c){try{return new JSONObject(new String(ScreenbarClient.readBytes(new File(c.getFilesDir(),STATUS)),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    static void publish(Context c,JSONObject status){
        try{ScreenbarClient.atomic(new File(c.getFilesDir(),STATUS),status.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception e){android.util.Log.e("RoomLight","Status persistence failed");}
        LinkWidget.updateAll(c);
    }
    static void record(Context c,JSONObject value){try{File file=new File(c.getFilesDir(),EVENTS);if(file.length()>400000)ScreenbarClient.atomic(file,new byte[0]);try(FileOutputStream out=new FileOutputStream(file,true)){out.write((value.toString()+"\n").getBytes(StandardCharsets.UTF_8));}}catch(Exception e){android.util.Log.e("RoomLight","Receipt persistence failed");}}
    static String resultName(JSONObject result){if(result==null)return "等待";switch(result.optString("status")){case "success":return result.optString("message","成功");case "failed":return "未确认："+result.optString("message");case "canceled":return "已取消";default:return "正在执行，尚未确认";}}
    static String summary(JSONObject value){
        if(value.length()==0)return "尚未执行；点击设置准备睡前参数";
        String at=new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.CHINA).format(new java.util.Date(value.optLong("startedAtEpochMs")));
        String title="上次"+LinkPolicy.name(value.optString("action"))+" · "+at;
        if(!value.optBoolean("completed")&&System.currentTimeMillis()-value.optLong("startedAtEpochMs")>LinkPolicy.TIMEOUT_MS+5000)return title+"\n任务已中断，请查看或重试";
        return title+"\nVont："+resultName(value.optJSONObject("vont"))+"\n挂灯："+resultName(value.optJSONObject("screenbar"));
    }
}
