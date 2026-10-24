package com.haolin.vontlocal;

import java.io.IOException;

/** Runs exactly one bounded transition with cancellation before every write. */
final class FocusTransition {
    static final class Light {
        final boolean on,delay,nativeFocus;final int brightness,temperature,mode;
        Light(boolean on,int b,int k,boolean delay,boolean focus){this(on,b,k,delay,focus,0);}
        Light(boolean on,int b,int k,boolean delay,boolean focus,int mode){this.on=on;brightness=b;temperature=k;this.delay=delay;nativeFocus=focus;this.mode=mode;}
    }
    interface Lamp {Light read()throws Exception;void brightness(int value)throws Exception;void light(int brightness,int temperature)throws Exception;}
    interface Guard {void check()throws Exception;}
    interface Waiter {void waitMillis(long millis)throws Exception;}
    static void change(FocusPolicy.Session session,String next,Lamp lamp,Guard guard,Waiter wait)throws Exception{
        guard.check();Light before=lamp.read();guard.check();String previous="resting".equals(next)?"working":"resting";
        if(!before.on||before.delay||before.nativeFocus||before.mode!=0||before.brightness!=session.brightness(previous)||before.temperature!=session.temperature(previous))throw new IOException("挂灯状态已被改变，提醒已停止，保留当前灯光");
        int low=Math.max(1,(int)Math.round(before.brightness*.65));
        // A gentle dip, never power-off; no queued restore after cancellation.
        for(int i=0;i<2;i++){guard.check();lamp.brightness(low);wait.waitMillis(350);guard.check();lamp.brightness(before.brightness);wait.waitMillis(350);}
        guard.check();lamp.light(session.brightness(next),session.temperature(next));guard.check();Light after=lamp.read();guard.check();
        if(!after.on||after.delay||after.nativeFocus||after.mode!=0||after.brightness!=session.brightness(next)||after.temperature!=session.temperature(next))throw new IOException("阶段灯光未回读确认，提醒已停止");
    }
}
