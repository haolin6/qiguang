package com.haolin.vontlocal;
import java.util.*;
public final class LinkPolicyTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void rejects(Runnable operation){boolean failed=false;try{operation.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,"invalid input accepted");}
    public static void main(String[] args){
        LinkPolicy.Settings s=new LinkPolicy.Settings(10,17,12,2900);
        rejects(()->new LinkPolicy.Settings(0,10,10,2700));rejects(()->new LinkPolicy.Settings(10,101,10,2700));
        rejects(()->new LinkPolicy.Settings(10,10,0,2700));rejects(()->new LinkPolicy.Settings(10,10,10,2699));rejects(()->new LinkPolicy.Settings(10,10,10,6501));
        rejects(()->LinkPolicy.integer("1.1",1,100));rejects(()->LinkPolicy.integer("",1,100));
        int[] segments=new int[12];Arrays.fill(segments,0xabcdef);
        List<Protocol.Attribute> values=Protocol.attributes(LinkPolicy.warm(s,segments));
        check(values.get(0).id==1&&values.get(0).value().equals(true),"power must precede warmth");
        Set<Integer> ids=new HashSet<>();for(Protocol.Attribute a:values)ids.add(a.id);
        check(ids.equals(new HashSet<>(Arrays.asList(1,2,7,15,24,25,26))),"unexpected property in sleep");
        check(Protocol.uint(values.get(values.size()-1).raw)==0x0a11,"independent warmth encoding");
        rejects(()->LinkPolicy.warm(s,new int[11]));
        List<Protocol.Attribute> off=Protocol.attributes(LinkPolicy.off());check(off.size()==1&&off.get(0).id==1&&off.get(0).value().equals(false),"all-off must be an explicit off");
        LinkPolicy.Gate gate=new LinkPolicy.Gate();LinkPolicy.Request sleep=gate.begin("sleep",100);
        check(gate.valid(sleep,101),"new request invalid");LinkPolicy.Request restarted=gate.begin("sleep",102);check(restarted!=null&&!gate.valid(sleep,103)&&gate.valid(restarted,103),"explicit scene did not restart");sleep=restarted;
        check(gate.begin("refresh",103)==null,"refresh interrupted writes");
        LinkPolicy.Request stop=gate.begin("off",104);check(!gate.valid(sleep,105),"old sleep survived off");check(gate.valid(stop,105),"off invalid");
        LinkPolicy.Request stop2=gate.begin("off",106);check(!gate.valid(stop,107),"old off survived supersession");
        check(!gate.valid(stop2,stop2.deadline+1),"expired request still valid");
        LinkPolicy.Request next=gate.begin("sleep",stop2.deadline+2);check(next!=null,"new click after expiry rejected");gate.finish(next);check(!gate.valid(next,stop2.deadline+3),"completed request replayed");
        rejects(()->gate.begin("toggle",0));
        Protocol.Attribute timer=Protocol.attributes(VontSleepCodec.setting(0x67782724L,10,true)).get(0);
        check(timer.id==105&&Protocol.hex(timer.raw).equals("677827240A01"),"sleep layout differs from original");
        check(Protocol.hex(Protocol.attributes(VontSleepCodec.cancel(timer.raw)).get(0).raw).equals("677827240A00"),"cancel must preserve timestamp and duration");
        check(timer.raw[5]==1,"cancel mutated original");rejects(()->VontSleepCodec.setting(1,9,true));rejects(()->VontSleepCodec.setting(1,241,true));rejects(()->VontSleepCodec.setting(0x100000000L,10,true));
        rejects(()->new LinkPolicy.Settings(10,10,10,2700,9));rejects(()->new LinkPolicy.Settings(10,10,10,2700,61));
        check(new LinkPolicy.Settings(10,10,10,2700,60).minutes==60,"common maximum lost");
        check(new LinkPolicy.Settings(10,10,10,2700).minutes==15,"upgrade default must be 15 minutes");
        check(LinkPolicy.commonEnd(1000000,1000200,10)==1600,"shared deadline midpoint or minute conversion wrong");
        check(LinkPolicy.commonEnd(1000000,1000200,60)==4600,"common maximum duration wrong");
        rejects(()->LinkPolicy.commonEnd(1000,999,10));rejects(()->LinkPolicy.commonEnd(0,10001,10));rejects(()->LinkPolicy.commonEnd(0,0,9));
        check(VontSleepCodec.matches(timer.raw,0x67782724L,10),"timer match failed");
        check(!VontSleepCodec.matches(timer.raw,0x67782725L,10),"different deadline accepted");
        check(!VontSleepCodec.matches(timer.raw,0x67782724L,11),"different duration accepted");
        check(!VontSleepCodec.enabled(Protocol.attributes(VontSleepCodec.cancel(timer.raw)).get(0).raw),"cancel still enabled");
        check(Protocol.hex(VontSleepCodec.clock(0x67782724L)).equals("67782724FFFFFFFF"),"time sync must preserve timezone");
        LinkPolicy.Request running=gate.begin("sleep",200000);LinkPolicy.Request canceled=gate.begin("cancel",200001);
        check(!gate.valid(running,200002)&&gate.valid(canceled,200002),"cancel did not preempt sleep");
        LinkPolicy.Request alloff=gate.begin("off",200003);check(!gate.valid(canceled,200004)&&gate.valid(alloff,200004),"all-off did not preempt cancel");
        check(LinkPolicy.allowed("work")&&LinkPolicy.allowed("wind_down")&&LinkPolicy.allowed("bed"),"scene action missing");
        check(LinkPolicy.bed("sleep")&&LinkPolicy.canonical("sleep").equals("bed"),"legacy widget alias broken");
        check(!LinkPolicy.bed("work")&&!LinkPolicy.bed("wind_down"),"desk scenes incorrectly arm timers");
        LinkPolicy.Gate scenes=new LinkPolicy.Gate();LinkPolicy.Request work=scenes.begin("work",1),bed=scenes.begin("bed",2),unwind=scenes.begin("wind_down",3);
        check(!scenes.valid(work,4)&&!scenes.valid(bed,4)&&scenes.valid(unwind,4),"old scene can replay");
        LinkPolicy.Request offNow=scenes.begin("off",5);check(!scenes.valid(unwind,6)&&scenes.valid(offNow,6),"all-off did not interrupt scene");
        check(scenes.begin("bed",7)==null&&scenes.valid(offNow,8),"scene interrupted all-off");
        scenes.finish(offNow);check(scenes.begin("bed",9)!=null,"scene remained blocked after off completed");
        rejects(()->new LinkPolicy.Settings(1,1,101,4500,15));check(new LinkPolicy.Settings(10,10,1,2700,10).brightness==1,"bed minimum lost");
        System.out.println("Room lighting policy checks passed: "+checks);
    }
}
