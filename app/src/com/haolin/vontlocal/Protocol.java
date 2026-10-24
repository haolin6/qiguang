package com.haolin.vontlocal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** TLB01 wire format, independent of Android; no reset, pairing or OTA commands. */
public final class Protocol {
    public static final String[] KEYS = {"", "power_onoff", "work_mode", "microphone_sensitivity", "scence_id", "general_timer", "color_data", "brightness", "auto_color_switch", "music_state", "DIY_scene", "warm_switch", "temp_value", "pickup_equipment", "Gradient", "color_segment", "direction", "music_mode", "segment_number", "color_match", "DIY_effects", "music_gradient", "cloud_scence_id", "diy_id", "auxiliary_switch", "auxiliary_warm_switch", "auxiliary_temp_value", "lamp_order"};
    static final String[] LABELS = {"", "电源", "工作模式", "麦克风灵敏度", "场景编号", "设备定时原始数据", "整体颜色", "亮度", "自动变色", "音乐状态", "DIY 场景", "暖光开关", "暖光数值", "拾音设备", "渐变开关", "分段颜色", "方向", "音乐模式编号", "音乐分段数", "音乐配色", "DIY 效果原始数据", "音乐渐变", "云场景编号", "DIY 编号", "辅助灯开关", "辅助暖光开关", "辅助暖光数值", "灯序"};
    static final String[] TYPES = {"VARCHAR", "BYTE", "SIGNEDSHORT", "SIGNEDINT", "SHORT", "INT", "FLOAT", "SIGNEDBYTE", "BYTEARRAY", "BOOL"};
    public static String key(int id) {
        if (id > 0 && id < KEYS.length) return KEYS[id];
        switch (id) { case 101: return "device_reset"; case 102: return "ic_number"; case 104: return "wakeup"; case 105: return "sleeping"; case 106: return "vacation_mode"; default: return "unknown_"+id; }
    }
    public static String label(int id) {
        if (id > 0 && id < LABELS.length) return LABELS[id];
        switch (id) { case 101: return "重置标志（只读显示）"; case 102: return "IC 数量"; case 104: return "唤醒原始数据"; case 105: return "睡眠原始数据"; case 106: return "假期模式"; default: return "未识别属性 "+id; }
    }
    public static int u16(byte[] b, int i) { return ((b[i]&255)<<8)|(b[i+1]&255); }
    public static long uint(byte[] b) { long n=0; for(byte x:b) n=(n<<8)|(x&255L); return n; }
    public static byte[] number(long n,int size) { byte[] b=new byte[size];for(int i=size-1;i>=0;i--){b[i]=(byte)n;n>>>=8;}return b; }
    public static String hex(byte[] b) { StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02X",x&255));return s.toString(); }
    public static byte[] unhex(String s) {
        if (s == null || !s.matches("(?i)([0-9a-f]{2})*")) throw new IllegalArgumentException("无效十六进制数据");
        byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;
    }
    public static byte[] concat(byte[]... parts) {ByteArrayOutputStream o=new ByteArrayOutputStream();for(byte[] b:parts)o.write(b,0,b.length);return o.toByteArray();}
    public static byte[] attr(int id,int type,byte[] value) {
        if(id<0||id>65535||type<0||type>9)throw new IllegalArgumentException("属性格式错误");
        int fixed=(type==1||type==7||type==9)?1:(type==2||type==4)?2:4;
        boolean variable=type==0||type==8;
        if(value.length>2048||(!variable&&value.length!=fixed))throw new IllegalArgumentException("属性长度错误");
        return concat(number(id,2),new byte[]{(byte)type},variable?number(value.length,2):new byte[0],value);
    }
    public static byte[] byteAttr(int id,int n) {if(n<0||n>255)throw new IllegalArgumentException("数值超出范围");return attr(id,1,number(n,1));}
    public static byte[] boolAttr(int id,boolean n) {return attr(id,9,new byte[]{(byte)(n?1:0)});}
    public static byte[] color(int rgb) {return attr(6,5,number(rgb&0xffffff,4));}
    public static String version(long n){return (n>>>16)+"."+((n>>>8)&255)+"."+(n&255);}
    public static byte[] segments(int[] colors) {
        if(colors.length<1||colors.length>128)throw new IllegalArgumentException("分段数超出范围");
        ByteArrayOutputStream o=new ByteArrayOutputStream();for(int c:colors){byte[] b=number(c&0xffffff,4);o.write(b,0,b.length);}return attr(15,8,o.toByteArray());
    }
    public static class Attribute {
        public final int id,type;public final byte[] raw;
        Attribute(int i,int t,byte[] r){id=i;type=t;raw=r;}
        public Object value() {
            long n;switch(type){
                case 0:return new String(raw,StandardCharsets.UTF_8);
                case 8:return hex(raw);
                case 9:return raw[0]!=0;
                case 7:return (long)raw[0];
                case 2:return (long)(short)u16(raw,0);
                case 3:return (long)(int)uint(raw);
                case 6:return (double)Float.intBitsToFloat((int)uint(raw));
                default:n=uint(raw);return n;
            }
        }
        public String display() {
            if(id==6&&raw.length==4)return String.format(Locale.US,"#%06X",uint(raw)&0xffffff);
            if(id==15&&type==8&&raw.length%4==0){StringBuilder s=new StringBuilder();for(int i=0;i<raw.length;i+=4){if(i>0)s.append(" ");s.append(String.format(Locale.US,"%d:#%06X",i/4+1,uint(Arrays.copyOfRange(raw,i,i+4))&0xffffff));}return s.toString();}
            if(type==9)return raw[0]==0?"关闭":"开启";
            return String.valueOf(value())+(id==7||id==3?"%":"");
        }
        public byte[] encode(){return attr(id,type,raw);}
    }
    /** Transactional: a malformed/unknown wire type rejects the entire payload. */
    public static List<Attribute> attributes(byte[] data) {
        List<Attribute> result=new ArrayList<>();int p=0;
        while(p<data.length){
            if(data.length-p<3)throw new IllegalArgumentException("属性头被截断 @"+p);
            int id=u16(data,p),type=data[p+2]&255;p+=3;int length;
            if(type==0||type==8){if(data.length-p<2)throw new IllegalArgumentException("属性长度被截断");length=u16(data,p);p+=2;}
            else if(type==1||type==7||type==9)length=1;
            else if(type==2||type==4)length=2;
            else if(type==3||type==5||type==6)length=4;
            else throw new IllegalArgumentException("未知数据类型 "+type+"，原始帧已保留");
            if(length>2048||length>data.length-p)throw new IllegalArgumentException("属性值被截断 #"+id);
            result.add(new Attribute(id,type,Arrays.copyOfRange(data,p,p+length)));p+=length;
        }return result;
    }
    public static byte[] packet(int command,byte[] data,int sequence,int flag) {
        if(data.length>4087)throw new IllegalArgumentException("帧过长");
        byte[] p=new byte[data.length+9];p[0]=(byte)0xaa;p[1]=0x55;p[2]=(byte)(p.length>>8);p[3]=(byte)p.length;p[4]=(byte)(sequence>>8);p[5]=(byte)sequence;p[6]=(byte)flag;p[7]=(byte)command;System.arraycopy(data,0,p,8,data.length);int sum=0;for(int i=0;i<p.length-1;i++)sum+=p[i]&255;p[p.length-1]=(byte)(sum^0x33);return p;
    }
    public static class Frame {
        public final int sequence,flag,command;public final byte[] data,raw;
        Frame(byte[] p){raw=p;sequence=u16(p,4);flag=p[6]&255;command=p[7]&255;data=Arrays.copyOfRange(p,8,p.length-1);}
    }
    public static class Stream {
        byte[] pending=new byte[0];public int rejected=0;
        public List<Frame> feed(byte[] next){
            pending=concat(pending,next);if(pending.length>16384){pending=new byte[0];rejected++;}
            List<Frame> frames=new ArrayList<>();
            while(pending.length>=2){
                if((pending[0]&255)!=0xaa||(pending[1]&255)!=0x55){pending=Arrays.copyOfRange(pending,1,pending.length);continue;}
                if(pending.length<4)break;int n=u16(pending,2);
                if(n<9||n>4096){pending=Arrays.copyOfRange(pending,1,pending.length);rejected++;continue;}
                if(pending.length<n)break;int sum=0;for(int i=0;i<n-1;i++)sum+=pending[i]&255;
                if(((sum^0x33)&255)!=(pending[n-1]&255)){pending=Arrays.copyOfRange(pending,1,pending.length);rejected++;continue;}
                frames.add(new Frame(Arrays.copyOf(pending,n)));pending=Arrays.copyOfRange(pending,n,pending.length);
            }return frames;
        }
    }
}
