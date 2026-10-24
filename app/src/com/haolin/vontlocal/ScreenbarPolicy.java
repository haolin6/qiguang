package com.haolin.vontlocal;

/** Typed, bounded device commands. Pairing, reset, upgrade and cloud methods are absent. */
final class ScreenbarPolicy {
    static final class Change {
        final int service,property;final Object value;
        Change(int s,int p,Object v){
            validate(s,p,v);if(s==3&&p==2)throw new IllegalArgumentException("通过启动延时关灯来设置时长");service=s;property=p;value=v;
        }
    }
    static final class Call {
        final int service,action;final Object[] values;final int[] inputs;
        Call(int s,int a,Object... args){
            service=s;action=a;values=args.clone();
            if(s==2&&a==1)inputs=new int[0];
            else if(s==3&&(a>=1&&a<=10||a>=13&&a<=15))
                inputs=a==2?new int[]{2}:a==13?new int[]{13,14,15}:a==15?new int[]{9}:new int[0];
            else throw new IllegalArgumentException("不支持的挂灯操作");
            if(inputs.length!=values.length)throw new IllegalArgumentException("操作参数数量不正确");
            for(int i=0;i<inputs.length;i++)validate(s,inputs[i],values[i]);
        }
    }
    static void range(Object value,int low,int high){
        if(!(value instanceof Integer)||(Integer)value<low||(Integer)value>high)
            throw new IllegalArgumentException("请输入 "+low+"–"+high+" 范围的整数");
    }
    static void bool(Object value){if(!(value instanceof Boolean))throw new IllegalArgumentException("需要开关值");}
    static void validate(int s,int p,Object value){
        if(s==2){
            switch(p){
                case 1:case 4:bool(value);return;
                case 2:range(value,1,100);return;
                case 3:range(value,2700,6500);return;
                case 5:range(value,5,9);return;
            }
        }else if(s==3){
            switch(p){
                case 1:case 4:case 6:case 8:case 18:bool(value);return;
                case 2:case 16:case 17:range(value,1,60);return;
                case 7:range(value,1,2);return;
                case 9:case 13:case 14:case 15:range(value,0,10);return;
                case 19:case 21:case 23:case 25:range(value,1,100);return;
                case 20:case 22:case 24:case 26:range(value,2700,6500);return;
            }
        }
        throw new IllegalArgumentException("该属性不能通过灯控修改");
    }
    static final String[] MODE_NAMES={"自由调光","我的模式1","我的模式2","我的模式3","我的模式4","阅读","办公","休闲","温馨","电脑","闪烁"};
    static String modeName(int id){return id>=0&&id<MODE_NAMES.length?MODE_NAMES[id]:"未知模式（"+id+"）";}
    static int integer(String value,int low,int high){
        final int number;try{number=Integer.parseInt(value.trim());}catch(Exception error){throw new IllegalArgumentException("请输入整数");}
        range(number,low,high);return number;
    }
}
