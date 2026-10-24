package com.haolin.vontlocal;

/** Original native sleeping layout. Deadline is UTC Unix seconds, not phone uptime. */
final class VontSleepCodec {
    static byte[] setting(long endEpochSeconds,int minutes,boolean enabled){
        if(endEpochSeconds<0||endEpochSeconds>0xffffffffL)throw new IllegalArgumentException("时间超出设备格式");
        LinkPolicy.range(minutes,10,240);
        return Protocol.attr(105,8,Protocol.concat(Protocol.number(endEpochSeconds,4),new byte[]{(byte)minutes,(byte)(enabled?1:0)}));
    }
    static byte[] cancel(byte[] original){
        if(original==null||original.length!=6)throw new IllegalArgumentException("尚未读取睡眠参数");
        byte[] copy=original.clone();copy[5]=0;return Protocol.attr(105,8,copy);
    }
    static boolean enabled(byte[] raw){return raw!=null&&raw.length==6&&raw[5]==1;}
    static long end(byte[] raw){if(raw==null||raw.length!=6)throw new IllegalArgumentException("睡眠参数不完整");return Protocol.uint(java.util.Arrays.copyOfRange(raw,0,4));}
    static boolean matches(byte[] raw,long end,int minutes){return enabled(raw)&&end(raw)==end&&(raw[4]&255)==minutes;}
    static byte[] clock(long nowSeconds){return Protocol.concat(Protocol.number(nowSeconds,4),Protocol.unhex("FFFFFFFF"));}
}
