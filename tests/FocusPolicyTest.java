package com.haolin.vontlocal;
import java.util.*;

public final class FocusPolicyTest {
    static int checks;static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    interface Task{void run()throws Exception;}
    static void rejects(Task task){boolean rejected=false;try{task.run();}catch(Exception e){rejected=true;}check(rejected,"invalid operation accepted");}
    static final class FakeLamp implements FocusTransition.Lamp {
        int b=100,k=4500,mode=0,reads;boolean on=true,delay=false,focus=false,badReadback;List<String> writes=new ArrayList<>();
        public FocusTransition.Light read(){reads++;return new FocusTransition.Light(on,b,k+(badReadback&&reads>1?1:0),delay,focus,mode);}
        public void brightness(int n){b=n;writes.add("brightness:"+n);}
        public void light(int n,int t){b=n;k=t;writes.add("light:"+n+":"+t);}
    }
    public static void main(String[] args)throws Exception{
        FocusPolicy.Settings settings=new FocusPolicy.Settings(50,5,30,3500);check(settings.workMinutes==50&&settings.restMinutes==5,"defaults");
        rejects(()->new FocusPolicy.Settings(0,5,30,3500));rejects(()->new FocusPolicy.Settings(61,5,30,3500));rejects(()->new FocusPolicy.Settings(50,0,30,3500));rejects(()->new FocusPolicy.Settings(50,61,30,3500));rejects(()->new FocusPolicy.Settings(50,5,0,3500));rejects(()->new FocusPolicy.Settings(50,5,101,3500));rejects(()->new FocusPolicy.Settings(50,5,30,2699));rejects(()->new FocusPolicy.Settings(50,5,30,6501));
        FocusPolicy.Gate gate=new FocusPolicy.Gate();FocusPolicy.Session session=gate.begin("one",settings,100,4500);check(gate.valid(session),"new session invalid");check(gate.begin("duplicate",settings,10,2700)==null,"duplicate cycle began");
        check(session.due(99999)==null,"preparation started a timer");session.ready("working",1000,1000000);check(session.deadlineElapsed==3001000&&session.nextWall==4000000,"work deadline");check(session.due(3000999)==null,"early reminder");check("resting".equals(session.due(3001000)),"work did not enter rest transition");check(session.due(3001001)==null,"same transition duplicated");
        FakeLamp lamp=new FakeLamp();FocusTransition.change(session,"resting",lamp,()->{if(!gate.valid(session))throw new IllegalStateException("ended");},n->{});
        check(lamp.writes.equals(Arrays.asList("brightness:65","brightness:100","brightness:65","brightness:100","light:30:3500")),"rest light/pulse order");check(lamp.reads==2,"transition not verified before and after");
        session.ready("resting",3002000,4001000);check(session.deadlineElapsed==3302000,"rest deadline");check("working".equals(session.due(3302000)),"rest did not enter work transition");lamp.writes.clear();FocusTransition.change(session,"working",lamp,()->{if(!gate.valid(session))throw new IllegalStateException();},n->{});check(lamp.b==100&&lamp.k==4500,"office light was not restored");check(lamp.writes.get(0).equals("brightness:20"),"rest reminder is too abrupt");
        for(int i=0;i<3;i++){session.ready("working",i*10000000L,1);check("resting".equals(session.due(session.deadlineElapsed)),"work cycle");session.ready("resting",i*10000000L+3000000,2);check("working".equals(session.due(session.deadlineElapsed)),"rest cycle");}
        gate.end();check(!gate.valid(session),"end did not invalidate session");int old=lamp.writes.size();rejects(()->FocusTransition.change(session,"resting",lamp,()->{if(!gate.valid(session))throw new IllegalStateException();},n->{}));check(lamp.writes.size()==old,"ended task sent a restore");check(session.due(Long.MAX_VALUE)==null,"ended timer replayed");
        FocusPolicy.Session next=gate.begin("two",settings,82,4200);check(!gate.valid(session)&&gate.valid(next),"new cycle revived old token");next.ready("working",1000,2000);long end=next.deadlineElapsed;check(next.due(end+60001)==null&&!next.active&&next.phase.equals("interrupted"),"late timer replayed");
        for(String reason:new String[]{"power-off","manual-brightness","manual-temperature","native-delay","native-focus","native-mode"}){FocusPolicy.Session s=new FocusPolicy.Session(reason,settings,100,4500);FakeLamp f=new FakeLamp();switch(reason){case "power-off":f.on=false;break;case "manual-brightness":f.b=70;break;case "manual-temperature":f.k=4000;break;case "native-delay":f.delay=true;break;case "native-focus":f.focus=true;break;case "native-mode":f.mode=8;break;}rejects(()->FocusTransition.change(s,"resting",f,()->{},n->{}));check(f.writes.isEmpty(),"external state was overwritten: "+reason);}
        FocusPolicy.Gate cancel=new FocusPolicy.Gate();FocusPolicy.Session c=cancel.begin("cancel-during-dip",settings,100,4500);FakeLamp f=new FakeLamp();rejects(()->FocusTransition.change(c,"resting",f,()->{if(!cancel.valid(c))throw new IllegalStateException();},n->{cancel.end();}));check(f.writes.equals(Arrays.asList("brightness:65")),"a canceled dip restored office light");
        FakeLamp bad=new FakeLamp();bad.badReadback=true;rejects(()->FocusTransition.change(new FocusPolicy.Session("bad",settings,100,4500),"resting",bad,()->{},n->{}));check(bad.writes.size()==5,"unconfirmed phase retried automatically");
        FocusPolicy.Session frozen=new FocusPolicy.Session("frozen",new FocusPolicy.Settings(45,7,23,3100),87,4100);FocusPolicy.Settings edited=new FocusPolicy.Settings(60,10,50,3700);check(edited.workMinutes==60&&frozen.settings.workMinutes==45&&frozen.officeBrightness==87,"saved settings altered a running session");
        FocusPolicy.Session minimum=new FocusPolicy.Session("minimum",new FocusPolicy.Settings(1,1,1,2700),1,2700);FakeLamp low=new FakeLamp();low.b=1;low.k=2700;FocusTransition.change(minimum,"resting",low,()->{},n->{});check(low.writes.stream().noneMatch(x->x.equals("brightness:0")),"reminder powered light off");
        System.out.println("Focus policy and transition checks passed: "+checks);
    }
}
