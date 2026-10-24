package com.haolin.vontlocal;

import android.app.AlertDialog;
import android.widget.*;
import java.io.ByteArrayOutputStream;

/** Narrowly recovered native controls; guarded by attributes returned in this session. */
final class NativeControls {
    static void auxiliary(MainActivity a){
        if(!a.hasCurrent(15,25,26)){a.notice("本次连接未报告双灯暖光通道");return;}
        LinearLayout f=a.column();f.setPadding(a.dp(20),0,a.dp(20),0);a.text(f,"滑块松手生效，开关立即设置。通道编号沿用设备协议，不代表摆放左右。数值为 0–100。",14);
        Switch warmLeft=new Switch(a),warmRight=new Switch(a);int warm=a.store.integer(a.selected,25,0),levels=a.store.integer(a.selected,26,0x6464);
        warmLeft.setText("通道 1 暖光");warmLeft.setChecked((warm&2)!=0);warmRight.setText("通道 2 暖光");warmRight.setChecked((warm&1)!=0);f.addView(warmLeft);f.addView(warmRight);
        SeekBar level1=a.numericSlider(f,"通道 1 暖光数值",100,Math.min(100,(levels>>8)&255));SeekBar level2=a.numericSlider(f,"通道 2 暖光数值",100,Math.min(100,levels&255));
        Runnable apply=()->{if(!a.hasCurrent(15,25,26))return;int bits=(warmLeft.isChecked()?2:0)|(warmRight.isChecked()?1:0);a.write(Protocol.concat(Protocol.byteAttr(2,2),Protocol.segments(a.store.segmentColors(a.selected)),Protocol.byteAttr(25,bits),Protocol.attr(26,4,Protocol.number((level1.getProgress()<<8)|level2.getProgress(),2))),"设置双灯暖光通道");a.ui.postDelayed(()->{if(a.selectedReady())a.ble.read();},500);};
        for(SeekBar slider:new SeekBar[]{level1,level2}){TextView label=(TextView)f.getChildAt(f.indexOfChild(slider)-1);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){label.setText((b==level1?"通道 1":"通道 2")+" 暖光数值："+n);}public void onStopTrackingTouch(SeekBar b){apply.run();}});}
        warmLeft.setOnCheckedChangeListener((v,on)->apply.run());warmRight.setOnCheckedChangeListener((v,on)->apply.run());
        a.design.styleTree(f);new AlertDialog.Builder(a).setTitle("两路暖光").setView(f).setPositiveButton("完成",null).show();
    }

    static void nativeDefault(MainActivity a){
        if(!a.hasCurrent(20)||a.store.record(a.selected).optLong("productCode")!=300029){a.notice("此内置效果仅针对已分析的 TLB01 开放");return;}
        LinearLayout f=a.column();f.setPadding(a.dp(20),0,a.dp(20),0);a.text(f,"红绿蓝内置彩色效果，由灯执行，离开本页也会继续。调整速度后点击启动。",14);SeekBar speed=a.numericSlider(f,"速度数值（0–100）",100,93);
        new AlertDialog.Builder(a).setTitle("设备内置默认彩色效果").setView(f).setPositiveButton("启动",(d,w)->{
            // Original DeviceUtils.getDiyAttribute: combo count, speed, style, effect,
            // palette size, 00RRGGBB colors, second palette size, TV-lamp warm sentinel.
            byte[] bytes=Protocol.concat(new byte[]{0,(byte)speed.getProgress(),2,1,3},Protocol.number(0xff0000,4),Protocol.number(0x00ff00,4),Protocol.number(0x0000ff,4),new byte[]{0,(byte)0xff});
            a.write(Protocol.concat(Protocol.byteAttr(2,4),Protocol.attr(20,8,bytes)),"设备内置默认彩色效果");a.ui.postDelayed(()->{if(a.selectedReady())a.ble.read();},900);
        }).setNegativeButton("取消",null).show();
    }
}
