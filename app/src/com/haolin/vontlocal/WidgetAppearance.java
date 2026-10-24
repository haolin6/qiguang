package com.haolin.vontlocal;
import android.content.Context;

/** Widget background only; existing lighting and appearance settings are independent. */
final class WidgetAppearance {
    static final String PREFS="widget-appearance-v1",KEY="backgroundTransparency";
    static int transparency(Context c){return Math.max(0,Math.min(100,c.getSharedPreferences(PREFS,0).getInt(KEY,0)));}
    static int alpha(int transparency){if(transparency<0||transparency>100)throw new IllegalArgumentException("透明度须为0–100%");return Math.round(255f*(100-transparency)/100f);}
    static void save(Context c,int value){alpha(value);if(!c.getSharedPreferences(PREFS,0).edit().putInt(KEY,value).commit())throw new IllegalStateException("透明度未保存，请重试");LinkWidget.updateAll(c);}
}
