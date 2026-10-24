package com.haolin.vontlocal;

/** Phone-driven focus cycles; no native firmware timing is assumed. */
final class FocusPolicy {
    static final long MAX_LATE_MS=60000,TRANSITION_TIMEOUT_MS=30000;
    static final class Settings {
        final int workMinutes,restMinutes,restBrightness,restTemperature;
        Settings(int w,int r,int b,int k){LinkPolicy.range(w,1,60);LinkPolicy.range(r,1,60);LinkPolicy.range(b,1,100);LinkPolicy.range(k,2700,6500);workMinutes=w;restMinutes=r;restBrightness=b;restTemperature=k;}
    }
    static final class Session {
        final String id;final Settings settings;final int officeBrightness,officeTemperature;
        volatile boolean active=true;volatile String phase="preparing";volatile long deadlineElapsed,nextWall,transitionStarted;
        Session(String id,Settings s,int b,int k){LinkPolicy.range(b,1,100);LinkPolicy.range(k,2700,6500);this.id=id;settings=s;officeBrightness=b;officeTemperature=k;}
        int brightness(String phase){return "resting".equals(phase)?settings.restBrightness:officeBrightness;}
        int temperature(String phase){return "resting".equals(phase)?settings.restTemperature:officeTemperature;}
        synchronized void ready(String next,long elapsed,long wall){if(!active)throw new IllegalStateException("专注已结束");if(!"working".equals(next)&&!"resting".equals(next))throw new IllegalArgumentException("未知专注阶段");phase=next;long duration=(next.equals("working")?settings.workMinutes:settings.restMinutes)*60000L;deadlineElapsed=elapsed+duration;nextWall=wall+duration;}
        synchronized String due(long now){if(!active||!("working".equals(phase)||"resting".equals(phase))||now<deadlineElapsed)return null;if(now-deadlineElapsed>MAX_LATE_MS){active=false;phase="interrupted";return null;}String next="working".equals(phase)?"resting":"working";phase="transitioning-to-"+next;transitionStarted=now;return next;}
    }
    static final class Gate {
        volatile Session current;
        synchronized Session begin(String id,Settings s,int b,int k){if(current!=null&&current.active)return null;return current=new Session(id,s,b,k);}
        boolean valid(Session s){return s!=null&&current==s&&s.active;}
        synchronized Session end(){Session s=current;if(s!=null)s.active=false;current=null;return s;}
    }
}
