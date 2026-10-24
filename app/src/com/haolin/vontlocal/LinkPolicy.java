package com.haolin.vontlocal;

/** Platform-free limits and request fencing for explicit room-light actions. */
final class LinkPolicy {
    static String VONT=""; // Resolved from private local records for each fenced job.
    static final long TIMEOUT_MS=45000;
    static final class Settings {
        final int warm1,warm2,brightness,temperature,minutes;
        Settings(int one,int two,int brightness,int temperature){
            this(one,two,brightness,temperature,15);
        }
        Settings(int one,int two,int brightness,int temperature,int minutes){
            range(one,1,100);range(two,1,100);range(brightness,1,100);range(temperature,2700,6500);
            range(minutes,10,60);warm1=one;warm2=two;this.brightness=brightness;this.temperature=temperature;this.minutes=minutes;
        }
    }
    static void range(int n,int low,int high){if(n<low||n>high)throw new IllegalArgumentException("请输入 "+low+"–"+high+" 范围的整数");}
    static int integer(String value,int low,int high){int n;try{n=Integer.parseInt(value.trim());}catch(Exception e){throw new IllegalArgumentException("请输入整数");}range(n,low,high);return n;}
    static String canonical(String action){return "sleep".equals(action)?"bed":action;}
    static boolean scene(String action){action=canonical(action);return "work".equals(action)||"wind_down".equals(action)||"bed".equals(action);}
    static boolean bed(String action){return "bed".equals(canonical(action));}
    static boolean allowed(String action){return scene(action)||"off".equals(action)||"cancel".equals(action)||"refresh".equals(action);}
    static String name(String action){action=canonical(action);return "work".equals(action)?"办公":"wind_down".equals(action)?"睡前":"bed".equals(action)?"床上":"off".equals(action)?"全关":"cancel".equals(action)?"取消延时":"刷新状态";}
    static long commonEnd(long sentMillis,long replyMillis,int minutes){
        range(minutes,10,60);
        if(sentMillis<0||replyMillis<sentMillis||replyMillis-sentMillis>10000)throw new IllegalArgumentException("挂灯计时启动时间未确认");
        return (sentMillis+(replyMillis-sentMillis)/2+minutes*60000L)/1000;
    }
    static final class Request {
        final long sequence,deadline;final String action;volatile boolean finished;
        Request(long sequence,String action,long now){this.sequence=sequence;this.action=action;deadline=now+TIMEOUT_MS;}
    }
    static final class Gate {
        volatile Request current;long sequence;
        synchronized Request begin(String action,long now){
            if(!allowed(action))throw new IllegalArgumentException("未知联动动作");
            action=canonical(action);
            if(current!=null&&valid(current,now)){
                if("refresh".equals(action))return null;
                // Explicit all-off cannot be interrupted by a stray scene tap.
                if("off".equals(current.action)&&!"off".equals(action))return null;
            }
            if(current!=null)current.finished=true;
            return current=new Request(++sequence,action,now);
        }
        boolean valid(Request request,long now){return request!=null&&current==request&&!request.finished&&now<=request.deadline;}
        synchronized void finish(Request request){if(current==request)request.finished=true;}
    }
    static byte[] warm(Settings s,int[] existingSegments){
        if(existingSegments.length!=12)throw new IllegalArgumentException("这盏Vont的12段资料尚未读取");
        return Protocol.concat(Protocol.boolAttr(1,true),Protocol.byteAttr(2,2),Protocol.byteAttr(7,100),
            Protocol.segments(existingSegments),Protocol.byteAttr(24,3),Protocol.byteAttr(25,3),
            Protocol.attr(26,4,Protocol.number((s.warm1<<8)|s.warm2,2)));
    }
    static byte[] off(){return Protocol.boolAttr(1,false);}
}
