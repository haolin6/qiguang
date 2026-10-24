package com.haolin.vontlocal;
import android.app.*;import android.content.*;
public final class FocusStopReceiver extends BroadcastReceiver {
    static PendingIntent pending(Context c){return PendingIntent.getBroadcast(c,611,new Intent(c,FocusStopReceiver.class).setAction("com.haolin.vontlocal.focus.END"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    @Override public void onReceive(Context c,Intent i){FocusService.end(c,"提醒已结束，保留当前灯光");}
}
