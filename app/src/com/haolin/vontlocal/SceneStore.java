package com.haolin.vontlocal;

import android.content.*;

/** Local presets only. Seeding never writes to either light. */
final class SceneStore {
    static final String[] IDS={"work","wind_down","bed"};
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("scene-presets-v1",0);}
    static synchronized void ensure(Context c){
        SharedPreferences p=prefs(c);if(p.getBoolean("initialized",false))return;
        SharedPreferences old=c.getSharedPreferences("sleep-settings-v1",0);
        int one=bounded(old.getInt("warm1",10),1,100,10),two=bounded(old.getInt("warm2",10),1,100,10),minutes=bounded(old.getInt("minutes",15),10,60,15);
        SharedPreferences.Editor e=p.edit();
        seed(e,"work",one,two,100,4500,minutes);seed(e,"wind_down",one,two,70,3500,minutes);seed(e,"bed",one,two,1,2700,minutes);
        if(!e.putBoolean("initialized",true).commit())throw new IllegalStateException("场景初始化失败，请重试");
    }
    static int bounded(int n,int low,int high,int fallback){return n>=low&&n<=high?n:fallback;}
    static void seed(SharedPreferences.Editor e,String id,int one,int two,int b,int k,int m){e.putInt(id+".warm1",one).putInt(id+".warm2",two).putInt(id+".brightness",b).putInt(id+".temperature",k).putInt(id+".minutes",m);}
    static String id(String id){id=LinkPolicy.canonical(id);if(!LinkPolicy.scene(id))throw new IllegalArgumentException("未知场景");return id;}
    static LinkPolicy.Settings settings(Context c,String id){ensure(c);id=id(id);SharedPreferences p=prefs(c);return new LinkPolicy.Settings(p.getInt(id+".warm1",10),p.getInt(id+".warm2",10),p.getInt(id+".brightness",1),p.getInt(id+".temperature",2700),p.getInt(id+".minutes",15));}
    static void save(Context c,String id,LinkPolicy.Settings s){ensure(c);id=id(id);SharedPreferences.Editor e=prefs(c).edit();seed(e,id,s.warm1,s.warm2,s.brightness,s.temperature,s.minutes);if(!e.commit())throw new IllegalStateException("场景未保存，请重试");
        if(LinkPolicy.bed(id))c.getSharedPreferences("sleep-settings-v1",0).edit().putInt("warm1",s.warm1).putInt("warm2",s.warm2).putInt("brightness",s.brightness).putInt("temperature",s.temperature).putInt("minutes",s.minutes).commit();
        LinkWidget.updateAll(c);
    }
    static String description(Context c,String id){LinkPolicy.Settings s=settings(c,id);return s.brightness+"% · "+s.temperature+"K"+(LinkPolicy.bed(id)?" · "+s.minutes+" 分钟后关灯":" · Vont 关闭");}
    static String mood(String id){return "work".equals(id)?"明亮，专注当下":"wind_down".equals(id)?"暖一点，慢下来":"两束暖光，安心入睡";}
}
