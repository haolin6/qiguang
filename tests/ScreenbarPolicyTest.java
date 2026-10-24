package com.haolin.vontlocal;

public final class ScreenbarPolicyTest {
    static int count=0;
    static void ok(boolean value){count++;if(!value)throw new AssertionError("check "+count);}
    static void reject(Runnable block){boolean rejected=false;try{block.run();}catch(IllegalArgumentException expected){rejected=true;}ok(rejected);}
    public static void main(String[] args){
        int[][] booleans={{2,1},{2,4},{3,1},{3,4},{3,6},{3,8},{3,18}};
        for(int[] key:booleans){new ScreenbarPolicy.Change(key[0],key[1],true);new ScreenbarPolicy.Change(key[0],key[1],false);count+=2;reject(()->new ScreenbarPolicy.Change(key[0],key[1],1));}
        int[][] ranges={{2,2,1,100},{2,3,2700,6500},{3,2,1,60},{3,16,1,60},{3,17,1,60},{3,9,0,10},{3,13,0,10},{3,14,0,10},{3,15,0,10}};
        for(int[] key:ranges){if(key[0]==3&&key[1]==2){reject(()->new ScreenbarPolicy.Change(3,2,30));continue;}new ScreenbarPolicy.Change(key[0],key[1],key[2]);new ScreenbarPolicy.Change(key[0],key[1],key[3]);count+=2;reject(()->new ScreenbarPolicy.Change(key[0],key[1],key[2]-1));reject(()->new ScreenbarPolicy.Change(key[0],key[1],key[3]+1));reject(()->new ScreenbarPolicy.Change(key[0],key[1],String.valueOf(key[2])));}
        for(int slot=0;slot<4;slot++){final int b=19+slot*2,c=b+1;new ScreenbarPolicy.Change(3,b,100);new ScreenbarPolicy.Change(3,c,6500);count+=2;reject(()->new ScreenbarPolicy.Change(3,b,0));reject(()->new ScreenbarPolicy.Change(3,c,6501));}
        reject(()->new ScreenbarPolicy.Change(3,27,"replacement pairing"));reject(()->new ScreenbarPolicy.Change(4,1,true));reject(()->new ScreenbarPolicy.Change(2,6,1));reject(()->new ScreenbarPolicy.Change(2,5,0));reject(()->new ScreenbarPolicy.Change(3,7,3));
        new ScreenbarPolicy.Call(2,1);new ScreenbarPolicy.Call(3,2,1);new ScreenbarPolicy.Call(3,13,0,9,10);new ScreenbarPolicy.Call(3,14);new ScreenbarPolicy.Call(3,15,10);count+=5;
        reject(()->new ScreenbarPolicy.Call(3,2,0));reject(()->new ScreenbarPolicy.Call(3,2,61));reject(()->new ScreenbarPolicy.Call(3,13,1,2));reject(()->new ScreenbarPolicy.Call(3,13,1,2,11));reject(()->new ScreenbarPolicy.Call(3,14,1));reject(()->new ScreenbarPolicy.Call(3,15,11));reject(()->new ScreenbarPolicy.Call(3,11));reject(()->new ScreenbarPolicy.Call(4,1));
        ok(ScreenbarPolicy.integer(" 2700 ",2700,6500)==2700);reject(()->ScreenbarPolicy.integer("",1,100));reject(()->ScreenbarPolicy.integer("999999999999",1,100));reject(()->ScreenbarPolicy.integer("10.5",1,100));reject(()->ScreenbarPolicy.integer("0",1,100));
        ok("闪烁".equals(ScreenbarPolicy.modeName(10)));ok("我的模式4".equals(ScreenbarPolicy.modeName(4)));
        System.out.println("Screenbar policy checks passed: "+count);
    }
}
