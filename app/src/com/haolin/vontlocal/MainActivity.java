package com.haolin.vontlocal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity implements BleController.Listener {
    Ui design;LinearLayout nav;String settingsPage="设备";DeviceStore store;BleController ble;final Handler ui=new Handler(Looper.getMainLooper());
    LinearLayout root,body;TextView connection,overview,details,currentColor,brightnessLabel,segmentLabel,logView;LinearLayout deviceList,segmentGrid,sceneList;
    final List<View> onlineControls=new ArrayList<>();
    SeekBar brightness;boolean draggingBrightness=false,renderScheduled=false,segmentDirty=false;
    String selected="",tab="调节",lastSegments="",pendingExport="",pendingMime="",lastDetail="";
    int[] draft=new int[0];int pickerColor=0xffffff;long sceneReadSequence=0;String sceneNamePending=null;
    long animationGeneration=0;boolean foreground=true;
    static final String[] TABS={"调节","分段","场景","设置"};
    @Override public void onCreate(Bundle b){
        Ui.prepare(this);super.onCreate(b);if(Intent.ACTION_MAIN.equals(getIntent().getAction())&&getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)){startActivity(new Intent(this,HomeActivity.class));finish();return;}design=new Ui(this);LocalHub hub=LocalHub.get(this);store=hub.store;ble=hub.ble;hub.add(this);
        if(b!=null){selected=b.getString("selected","");tab=b.getString("tab","设备");pendingMime=b.getString("pendingMime","");}else selected=getPreferences(0).getString("selected","");
        if(b==null&&getIntent().hasExtra("entryTab"))tab=getIntent().getStringExtra("entryTab");if(!Arrays.asList(TABS).contains(tab))tab="调节";
        if(b!=null&&b.containsKey("draft")){draft=b.getIntArray("draft");segmentDirty=b.getBoolean("segmentDirty");lastSegments=b.getString("lastSegments","");settingsPage=b.getString("settingsPage","设备");}
        root=design.root(this);design.toolbar(this,root,"Vont 双灯","暖光 · 色彩 · 分段");
        connection=design.label("",13,design.muted);connection.setPadding(dp(20),0,dp(20),dp(8));root.addView(connection);
        nav=row();nav.setPadding(dp(16),0,dp(16),dp(8));root.addView(nav);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(dp(20),0,dp(20),dp(16));body=column();body.setPadding(dp(16),dp(4),dp(16),dp(24));body.setBackground(design.shape(design.surface,22));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showTab();
        if(!store.loadError.isEmpty())notice(store.loadError);
        if(!ble.permitted())requestPermissions(Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        else connectRemembered();
    }
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    TextView text(LinearLayout p,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(design.text);t.setPadding(0,dp(4),0,dp(4));p.addView(t);return t;}
    Button button(LinearLayout p,String title,Runnable action,boolean online){Button b=design.button(p,title,action,false);if(online){onlineControls.add(b);b.setEnabled(selectedReady());}return b;}
    void section(String s){design.title(body,s);}
    void hint(String s){design.hint(body,s);}
    void tabs(){nav.removeAllViews();for(String t:TABS){Button b=design.button(nav,t,()->switchTab(t),tab.equals(t));b.setTextSize(13);b.setContentDescription("Vont页面-"+t);}}
    void connectRemembered(){if(LocalHub.busy)return;if(selected.isEmpty()&&store.devices.size()==1)selected=store.devices.keySet().iterator().next();if(!selected.isEmpty()&&store.devices.containsKey(selected)){if(!ble.ready||!selected.equals(ble.address))connectSelected(selected);}else if(store.devices.isEmpty()){tab="设置";settingsPage="设备";showTab();}}
    void settingsTab(){LinearLayout links=row();body.addView(links);for(String name:new String[]{"设备","详情","导出"})button(links,name,()->{settingsPage=name;showTab();},false);if("详情".equals(settingsPage))detailsTab();else if("导出".equals(settingsPage))exportTab();else devicesTab();}
    SeekBar numericSlider(LinearLayout parent,String name,int max,int initial){TextView label=text(parent,name+"："+initial,14);SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(initial);parent.addView(s);design.styleTree(s);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(name+"："+value);}});return s;}
    @Override public void notice(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public void changed(){if(renderScheduled)return;renderScheduled=true;ui.postDelayed(()->{renderScheduled=false;render();},100);}
    boolean selectedReady(){return !LocalHub.busy&&ble.ready&&!selected.isEmpty()&&selected.equals(ble.address);}
    void switchTab(String target){if("设备".equals(target)||"详情".equals(target)||"导出".equals(target)){settingsPage=target;target="设置";}final String destination=target;if(tab.equals("分段")&&!target.equals(tab)&&segmentDirty){new AlertDialog.Builder(this).setMessage("分段草稿尚未发送，离开后仍会保留到切换设备或关闭应用。").setPositiveButton("继续",(d,w)->{tab=destination;showTab();}).setNegativeButton("留在这里",null).show();return;}tab=destination;showTab();}
    void showTab(){
        body.removeAllViews();onlineControls.clear();deviceList=null;overview=null;details=null;currentColor=null;brightnessLabel=null;brightness=null;segmentLabel=null;segmentGrid=null;sceneList=null;logView=null;lastDetail="";
        tabs();switch(tab){case "调节":lightTab();break;case "分段":segmentsTab();break;case "场景":scenesTab();break;case "设置":settingsTab();break;}design.styleTree(body);render();
    }
    void devicesTab(){


        LinearLayout buttons=row();body.addView(buttons);button(buttons,"扫描附近的灯",()->{if(!LocalHub.busy)ble.scan();else notice("联动进行中");},false);button(buttons,"断开连接",()->{if(LocalHub.busy){notice("联动进行中");return;}stopAnimation();ble.disconnect(true);},false);
        hint("选择设备后连接。设备名称、房间和最后读取状态会保存在本机；当前一次连接一台灯控设备。");
        deviceList=column();body.addView(deviceList);refreshDevices();
        button(body,"编辑当前设备名称 / 房间",()->editDevice(),false);
        button(body,"查看设备详情",()->switchTab("详情"),false);
    }
    void refreshDevices(){if(deviceList==null)return;deviceList.removeAllViews();if(store.devices.isEmpty())text(deviceList,"尚未发现灯，请让灯通电并靠近手机。",16);for(Map.Entry<String,JSONObject> entry:store.devices.entrySet()){
        String id=entry.getKey();JSONObject d=entry.getValue();String room=d.optString("room");String title=(id.equals(selected)?"● ":"")+store.name(id)+(room.isEmpty()?"":" · "+room)+"\n"+d.optString("modelFromName","型号未知")+"  "+id+"\n"+(store.live.contains(id)?"已连接":"已保存，可点击连接");
        button(deviceList,title,()->selectDevice(id),false);
    }}
    void selectDevice(String id){if(LocalHub.busy){notice("联动进行中，请稍后手动连接");return;}if(segmentDirty&&!id.equals(selected)){new AlertDialog.Builder(this).setMessage("切换设备将丢弃尚未发送的分段草稿。").setPositiveButton("切换",(d,w)->{segmentDirty=false;connectSelected(id);}).setNegativeButton("取消",null).show();}else connectSelected(id);}
    void connectSelected(String id){stopAnimation();sceneNamePending=null;selected=id;DeviceTargets.remember(this,store,id);lastSegments="";draft=new int[0];getPreferences(0).edit().putString("selected",id).apply();ble.connect(id);render();}
    void editDevice(){if(selected.isEmpty()){notice("请先选择设备");return;}JSONObject d=store.record(selected);LinearLayout form=column();form.setPadding(dp(20),0,dp(20),0);EditText alias=new EditText(this);alias.setHint("设备名称，例如：电视氛围灯");alias.setText(d.optString("alias"));alias.setSingleLine(true);form.addView(alias);EditText room=new EditText(this);room.setHint("房间，例如：客厅");room.setText(d.optString("room"));room.setSingleLine(true);form.addView(room);new AlertDialog.Builder(this).setTitle("本机设备档案").setView(form).setPositiveButton("保存",(dialog,w)->{DeviceStore.put(d,"alias",alias.getText().toString().trim());DeviceStore.put(d,"room",room.getText().toString().trim());store.save();showTab();}).setNegativeButton("取消",null).show();}
    void detailsTab(){
        section("设备与状态");LinearLayout r=row();body.addView(r);button(r,"重新读取状态",()->ble.read(),true);button(r,"查询版本",()->ble.version(),true);
        overview=text(body,"",16);details=text(body,"",14);details.setTextIsSelectable(true);
        hint("字段名来自原版协议。属性出现表示设备报告了该字段，不代表所有取值均已验证。未返回的信息显示为未知；断开后显示带时间的缓存。");
    }
    void lightTab(){
        section("随手调光");LinearLayout r=row();body.addView(r);button(r,"开灯",()->write(Protocol.boolAttr(1,true),"开灯"),true);button(r,"关灯",()->write(Protocol.boolAttr(1,false),"关灯"),true);button(r,"读取状态",()->ble.read(),true);
        currentColor=text(body,"",16);int[] colors={0xff0000,0x00ff00,0x0000ff,0xffffff,0xff8800,0x00ffff,0x9900ff,0xffd2a0};String[] names={"红","绿","蓝","白","橙","青","紫","暖白混色"};for(int i=0;i<2;i++){LinearLayout line=row();body.addView(line);for(int j=0;j<4;j++){final int at=i*4+j;Button swatch=button(line,names[at],()->setColor(colors[at]),true);swatch.setTextSize(12);swatch.setTag(colors[at]);}}
        button(body,"自定义颜色（RGB / #RRGGBB）",()->colorPicker(pickerColor,c->setColor(c)),true);
        brightnessLabel=text(body,"亮度：等待设备回读",17);brightness=new SeekBar(this);brightness.setMax(99);body.addView(brightness);onlineControls.add(brightness);brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){draggingBrightness=true;}public void onProgressChanged(SeekBar s,int p,boolean user){if(user)brightnessLabel.setText("准备设置亮度："+(p+1)+"%");}public void onStopTrackingTouch(SeekBar s){draggingBrightness=false;write(Protocol.byteAttr(7,s.getProgress()+1),"设置亮度 "+(s.getProgress()+1)+"%");}});
        section("本地动态效果");hint("由手机持续发送颜色，保持本页前台运行。切到后台、断开或手动调节时停止。");LinearLayout effects=row();body.addView(effects);button(effects,"柔和变色",()->startAnimation(false),true);button(effects,"彩虹流动",()->startAnimation(true),true);button(effects,"停止",()->stopAnimation(),false);
        section("灯内效果与暖光");hint("以下项目按设备已报告的属性开放；模式编号沿用原协议，实际效果需逐项观察。");
        button(body,"灯内麦克风 / 音乐设置",()->musicDialog(),true);
        button(body,"设备内置默认彩色效果",()->NativeControls.nativeDefault(this),true);
        button(body,"恢复静态颜色模式",()->{write(Protocol.byteAttr(2,2),"切换静态颜色模式");ble.read();},true);
        button(body,"两路暖光调节",()->NativeControls.auxiliary(this),true);
        button(body,"渐变开关",()->gradientDialog(),true);
    }
    byte[] staticMode(){List<byte[]> parts=new ArrayList<>();parts.add(Protocol.byteAttr(2,2));if(hasCurrent(25))parts.add(Protocol.byteAttr(25,0));else if(hasCurrent(11))parts.add(Protocol.boolAttr(11,false));return Protocol.concat(parts.toArray(new byte[0][]));}
    void setColor(int rgb){pickerColor=rgb;write(Protocol.concat(staticMode(),Protocol.color(rgb)),"整体颜色 "+rgbString(rgb));}
    void write(byte[] data,String label){stopAnimation();ble.attributes(data,label);}
    interface ColorAction {void picked(int rgb);}
    String rgbString(int c){return String.format(Locale.US,"#%06X",c&0xffffff);}
    void colorPicker(int initial,ColorAction action){
        LinearLayout form=column();form.setPadding(dp(22),0,dp(22),0);TextView preview=text(form,"",18);preview.setGravity(Gravity.CENTER);EditText hex=new EditText(this);hex.setSingleLine(true);hex.setHint("#RRGGBB");form.addView(hex);SeekBar[] sliders=new SeekBar[3];String[] labels={"红 R","绿 G","蓝 B"};boolean[] sync={false};int[] value={initial&0xffffff};
        Runnable refresh=()->{preview.setText(rgbString(value[0]));preview.setBackgroundColor(0xff000000|value[0]);preview.setTextColor(((value[0]>>16&255)*299+(value[0]>>8&255)*587+(value[0]&255)*114)>128000?Color.BLACK:Color.WHITE);};
        for(int i=0;i<3;i++){final int index=i;text(form,labels[i],14);sliders[i]=new SeekBar(this);sliders[i].setMax(255);sliders[i].setProgress((initial>>(16-i*8))&255);form.addView(sliders[i]);sliders[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(!user||sync[0])return;value[0]=(sliders[0].getProgress()<<16)|(sliders[1].getProgress()<<8)|sliders[2].getProgress();sync[0]=true;hex.setText(rgbString(value[0]));sync[0]=false;refresh.run();}});}
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){if(sync[0])return;String v=s.toString().trim();if(!v.matches("#?[0-9a-fA-F]{6}"))return;value[0]=(int)Long.parseLong(v.replace("#",""),16);sync[0]=true;for(int i=0;i<3;i++)sliders[i].setProgress(value[0]>>(16-i*8)&255);sync[0]=false;refresh.run();}public void afterTextChanged(Editable e){}});hex.setText(rgbString(initial));refresh.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择颜色").setView(form).setPositiveButton("应用",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!hex.getText().toString().trim().matches("#?[0-9a-fA-F]{6}")){hex.setError("请输入 6 位 RGB 颜色");return;}action.picked(value[0]);dialog.dismiss();}));dialog.show();
    }
    void segmentsTab(){
        section("分段颜色编辑");segmentLabel=text(body,"",15);hint("点击色块编辑，最后点“发送分段”。编号为设备协议顺序，尚未对应到左右灯的具体位置。");
        LinearLayout r=row();body.addView(r);button(r,"从灯读取",()->{if(segmentDirty)new AlertDialog.Builder(this).setMessage("丢弃草稿并从灯重新读取？").setPositiveButton("读取",(d,w)->{segmentDirty=false;lastSegments="";ble.read();}).setNegativeButton("取消",null).show();else{lastSegments="";ble.read();}},true);button(r,"填充颜色",()->colorPicker(pickerColor,c->{if(needSegments()){Arrays.fill(draft,c);segmentDirty=true;drawSegments();}}),true);
        LinearLayout presets=row();body.addView(presets);button(presets,"彩虹",()->{if(needSegments()){for(int i=0;i<draft.length;i++)draft[i]=Color.HSVToColor(new float[]{360f*i/draft.length,1,1})&0xffffff;segmentDirty=true;drawSegments();}},true);button(presets,"暖冷渐变",()->{if(needSegments()){for(int i=0;i<draft.length;i++)draft[i]=blend(0xff8a32,0x369dff,i/(float)Math.max(1,draft.length-1));segmentDirty=true;drawSegments();}},true);button(presets,"反转",()->{if(needSegments()){for(int i=0;i<draft.length/2;i++){int c=draft[i];draft[i]=draft[draft.length-i-1];draft[draft.length-i-1]=c;}segmentDirty=true;drawSegments();}},true);
        segmentGrid=column();body.addView(segmentGrid);button(body,"发送分段颜色",()->{if(needSegments()){byte[] payload=Protocol.concat(staticMode(),Protocol.segments(draft));write(payload,"设置 "+draft.length+" 段颜色");segmentDirty=false;lastSegments="";ui.postDelayed(()->{if(selectedReady())ble.read();},800);}},true);
    }
    boolean needSegments(){if(draft.length==0){notice("请先连接并读取分段信息");return false;}return true;}
    static int blend(int a,int b,float t){int c=0;for(int shift:new int[]{16,8,0})c|=Math.round(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t)<<shift;return c;}
    void drawSegments(){if(segmentGrid==null)return;segmentGrid.removeAllViews();for(int i=0;i<draft.length;i+=3){LinearLayout r=row();segmentGrid.addView(r);for(int j=i;j<Math.min(i+3,draft.length);j++){final int n=j;Button b=button(r,(j+1)+"\n"+rgbString(draft[j]),()->colorPicker(draft[n],c->{draft[n]=c;segmentDirty=true;drawSegments();}),false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff000000|draft[j]));b.setTextColor((((draft[j]>>16)&255)*299+((draft[j]>>8)&255)*587+(draft[j]&255)*114)>128000?Color.BLACK:Color.WHITE);b.setEnabled(selectedReady());}}if(segmentLabel!=null)segmentLabel.setText(draft.length+" 段 · "+(segmentDirty?"草稿尚未发送":"按设备回读显示"));}
    void scenesTab(){section("本地静态场景");hint("保存灯当前回读的开关、亮度、整体颜色和分段配色。场景保存在本机并包含在 JSON 导出中；不会保存云端动态素材。");button(body,"读取并保存当前灯光",()->saveSceneDialog(),true);sceneList=column();body.addView(sceneList);refreshScenes();}
    void saveSceneDialog(){if(!selectedReady()){notice("请先连接灯");return;}EditText name=new EditText(this);name.setHint("例如：观影、阅读、晚间");new AlertDialog.Builder(this).setTitle("保存静态场景").setView(name).setPositiveButton("读取并保存",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty())n="场景 "+(store.scenes.length()+1);sceneNamePending=n;JSONObject record=store.record(selected);sceneReadSequence=record.optLong("lastFullReadSequence",-1);ble.read();String wanted=n;ui.postDelayed(()->{if(wanted.equals(sceneNamePending)){sceneNamePending=null;notice("没有取得新的完整状态，未保存场景");}},8000);}).setNegativeButton("取消",null).show();}
    void captureScene(){
        if(sceneNamePending==null||!selectedReady()||!ble.sessionRead||store.record(selected).optLong("lastFullReadSequence",-1)==sceneReadSequence)return;
        try{JSONObject s=new JSONObject();DeviceStore.put(s,"id",UUID.randomUUID().toString());DeviceStore.put(s,"name",sceneNamePending);DeviceStore.put(s,"sourceDeviceId",store.record(selected).optString("deviceId"));DeviceStore.put(s,"createdAt",DeviceStore.iso(System.currentTimeMillis()));DeviceStore.put(s,"kind","static_colors");JSONArray attrs=new JSONArray();
            for(int id:new int[]{6,7,15,11,12,24,25,26,1}){JSONObject a=store.attribute(selected,id);if(a!=null)attrs.put(new JSONObject(a.toString()));}JSONObject mode=new JSONObject();DeviceStore.put(mode,"index",2);DeviceStore.put(mode,"type",1);DeviceStore.put(mode,"rawValueHex","02");attrs.put(mode);DeviceStore.put(s,"attributes",attrs);DeviceStore.put(s,"segmentCount",store.segmentColors(selected).length);store.scenes.put(s);store.save();sceneNamePending=null;notice("场景已保存到本机");refreshScenes();}catch(Exception e){sceneNamePending=null;notice("场景保存失败："+e.getMessage());}
    }
    void refreshScenes(){if(sceneList==null)return;sceneList.removeAllViews();if(store.scenes.length()==0)text(sceneList,"尚未保存场景。",15);for(int i=0;i<store.scenes.length();i++){final int index=i;JSONObject s=store.scenes.optJSONObject(i);LinearLayout r=row();sceneList.addView(r);button(r,s.optString("name"),()->applyScene(s),false);button(r,"删除",()->new AlertDialog.Builder(this).setMessage("删除本机场景“"+s.optString("name")+"”？").setPositiveButton("删除",(d,w)->{store.scenes.remove(index);store.save();refreshScenes();}).setNegativeButton("取消",null).show(),false);}}
    void applyScene(JSONObject s){
        if(!selectedReady()){notice("请先连接目标灯");return;}int count=s.optInt("segmentCount"),actual=store.segmentColors(selected).length;if(count>0&&count!=actual){notice("分段数不同，请先在目标灯上调整配色");return;}
        try{List<byte[]> parts=new ArrayList<>();Map<Integer,JSONObject> values=new HashMap<>();JSONArray a=s.getJSONArray("attributes");for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);values.put(v.getInt("index"),v);}parts.add(Protocol.byteAttr(2,2));
            // Apply power before colors: power-on can restore a previous device palette.
            // TV bars use auxiliary warm channels; the generic warm switch has side effects.
            for(int id:new int[]{1,7,6,15,24,12,11,26,25}){JSONObject v=values.get(id);if(v==null||!store.lastReadContains(selected,id))continue;if(hasCurrent(25)&&(id==11||id==12))continue;byte[] raw=Protocol.unhex(v.getString("rawValueHex"));if(id!=6&&id!=15&&Arrays.equals(raw,store.raw(selected,id)))continue;parts.add(Protocol.attr(id,v.getInt("type"),raw));}
            write(Protocol.concat(parts.toArray(new byte[0][])),"应用静态场景 "+s.optString("name"));ui.postDelayed(()->{if(selectedReady())ble.read();},900);
        }catch(Exception e){notice("场景格式错误："+e.getMessage());}
    }
    void exportTab(){section("Vont 读取与导出");hint("本页导出已保存的 Vont 设备：身份、房间、状态属性、时间、蓝牙服务、原始数据、最近 1000 条通信记录和本地场景。挂灯请在“屏幕挂灯1S灯控 → 更多”中单独导出。JSON 适合后续程序接入，TXT 适合直接阅读。");
        button(body,"导出 Vont 设备 JSON",()->exportFile(false),false);button(body,"导出 Vont 可读报告 TXT",()->exportFile(true),false);button(body,"刷新当前灯状态",()->ble.read(),true);hint("导出通过系统文件选择器保存到你选择的位置。断开设备的数据为历史缓存；导出不等于完整固件或云端账号备份。");
        section("通信与解析状态");logView=text(body,"",11);logView.setTextIsSelectable(true);
    }
    void exportFile(boolean readable){try{store.save();pendingExport=readable?store.text():store.json();pendingMime=readable?"text/plain":"application/json";try(FileOutputStream f=openFileOutput("pending-export",MODE_PRIVATE)){f.write(pendingExport.getBytes(StandardCharsets.UTF_8));}getPreferences(0).edit().putString("pendingMime",pendingMime).apply();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(pendingMime);intent.putExtra(Intent.EXTRA_TITLE,"vont-devices-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+(readable?".txt":".json"));startActivityForResult(intent,71);}catch(Exception e){notice("无法打开保存窗口："+e.getMessage());}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=71)return;if(result!=RESULT_OK||data==null||data.getData()==null){pendingExport="";notice("已取消导出");return;}try{if(pendingMime.isEmpty())pendingMime=getPreferences(0).getString("pendingMime","application/json");if(pendingExport.isEmpty()){try(FileInputStream in=openFileInput("pending-export");ByteArrayOutputStream buf=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);pendingExport=new String(buf.toByteArray(),StandardCharsets.UTF_8);}}if(pendingExport.isEmpty())throw new IOException("导出快照不存在，请重新导出");Uri uri=data.getData();try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("目标文件不可写");out.write(pendingExport.getBytes(StandardCharsets.UTF_8));}try(FileOutputStream backup=openFileOutput(pendingMime.equals("text/plain")?"last-export.txt":"last-export.json",MODE_PRIVATE)){backup.write(pendingExport.getBytes(StandardCharsets.UTF_8));}notice("导出成功");ble.log("export","saved "+pendingMime);}catch(Exception e){notice("导出失败："+e.getMessage());}finally{pendingExport="";}}
    void musicDialog(){
        if(!hasCurrent(3,8,9,13,17)){notice("本次连接尚未读到音乐相关属性");return;}
        LinearLayout form=column();form.setPadding(dp(20),0,dp(20),0);text(form,"使用灯内麦克风，不录制手机音频。模式编号沿用原协议，逐项试用。",14);
        Switch enabled=new Switch(this);enabled.setText("启用设备音乐模式");enabled.setChecked(store.integer(selected,2,2)==1);form.addView(enabled);Switch auto=new Switch(this);auto.setText("自动变色");auto.setChecked(store.integer(selected,8,1)!=0);form.addView(auto);
        text(form,"灵敏度（1–100）",14);SeekBar sensitivity=new SeekBar(this);sensitivity.setMax(99);sensitivity.setProgress(Math.max(0,Math.min(99,store.integer(selected,3,50)-1)));form.addView(sensitivity);Spinner modes=new Spinner(this);String[] labels=new String[16];for(int i=0;i<16;i++)labels[i]="音乐模式 "+(i+1);modes.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));modes.setSelection(Math.max(0,Math.min(15,store.integer(selected,17,1)-1)));form.addView(modes);
        new AlertDialog.Builder(this).setTitle("灯内音乐控制 · 待逐项验证").setView(form).setPositiveButton("发送设置",(d,w)->{byte[] p=Protocol.concat(Protocol.byteAttr(13,0),Protocol.byteAttr(3,sensitivity.getProgress()+1),Protocol.byteAttr(9,enabled.isChecked()?1:0),Protocol.byteAttr(8,auto.isChecked()?1:0),Protocol.byteAttr(17,modes.getSelectedItemPosition()+1),Protocol.byteAttr(2,enabled.isChecked()?1:2));write(p,"设置灯内音乐模式");ui.postDelayed(()->{if(selectedReady())ble.read();},900);}).setNegativeButton("取消",null).show();
    }
    void warmDialog(){
        if(hasCurrent(25,26)){NativeControls.auxiliary(this);return;}
        if(!hasCurrent(11,12)){notice("设备本次未报告暖光通道，仍可用暖白混色按钮");return;}LinearLayout form=column();form.setPadding(dp(20),0,dp(20),0);text(form,"暖光数值沿用协议的 0–100，不直接等同于色温 K。",14);Switch enabled=new Switch(this);enabled.setText("暖光通道");enabled.setChecked(store.integer(selected,11,0)!=0);form.addView(enabled);SeekBar warmth=new SeekBar(this);warmth.setMax(100);warmth.setProgress(Math.max(0,Math.min(100,store.integer(selected,12,50))));form.addView(warmth);new AlertDialog.Builder(this).setTitle("设备暖光通道 · 待观察").setView(form).setPositiveButton("发送设置",(d,w)->{write(Protocol.concat(Protocol.attr(12,4,Protocol.number(warmth.getProgress(),2)),Protocol.boolAttr(11,enabled.isChecked())),"设置设备暖光通道");ui.postDelayed(()->{if(selectedReady())ble.read();},900);}).setNegativeButton("取消",null).show();
    }
    void gradientDialog(){if(!hasCurrent(14)){notice("本次连接未读到渐变属性，暂不开启此控制");return;}new AlertDialog.Builder(this).setTitle("设备渐变").setItems(new String[]{"关闭","开启"},(d,n)->write(Protocol.byteAttr(14,n),"设备渐变 "+n)).show();}
    boolean hasCurrent(int... ids){if(!selectedReady()||!ble.sessionRead)return false;for(int id:ids)if(!store.lastReadContains(selected,id))return false;return true;}
    void stopAnimation(){++animationGeneration;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void startAnimation(boolean segments){if(!selectedReady()||!ble.sessionRead){notice("请先连接并读取状态");return;}if(segments&&store.segmentColors(selected).length==0){notice("未读取到分段信息");return;}stopAnimation();long generation=animationGeneration;String target=selected;long start=SystemClock.elapsedRealtime();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);ble.attributes(staticMode(),"本地动态效果：静态颜色模式");Runnable tick=new Runnable(){public void run(){if(generation!=animationGeneration||!foreground||!selectedReady()||!selected.equals(target))return;if(ble.pending==null&&ble.commands.isEmpty()){float hue=((SystemClock.elapsedRealtime()-start)/90f)%360;if(segments){int n=store.segmentColors(selected).length;int[] c=new int[n];for(int i=0;i<n;i++)c[i]=Color.HSVToColor(new float[]{(hue+360f*i/n)%360,1,1})&0xffffff;ble.attributes(Protocol.segments(c),"本地彩虹流动");}else ble.attributes(Protocol.color(Color.HSVToColor(new float[]{hue,.85f,1})&0xffffff),"本地柔和变色");}ui.postDelayed(this,500);}};ui.postDelayed(tick,400);notice("本地效果已启动；切到后台会停止");}
    void render(){
        if(connection==null)return;connection.setText(ble.status+(selected.isEmpty()?"":"\n当前："+store.name(selected)));
        for(View v:onlineControls)v.setEnabled(selectedReady());refreshDevices();
        if(!selected.isEmpty()){
            JSONObject d=store.record(selected);JSONObject attrs=DeviceStore.object(d,"attributes");
            if(overview!=null)overview.setText(store.name(selected)+"\n型号："+d.optString("modelFromName","未知")+"　产品代码："+d.optString("productCode","未知")+"\n房间："+d.optString("room","未填写")+"\n蓝牙地址："+selected+"\nWi-Fi MAC（广播）："+d.optString("wifiMacFromAdvertisement","未知")+"\n信号："+d.optString("rssiDbm","未知")+" dBm　MTU："+d.optString("negotiatedMtu","未协商")+"\n最后发现："+d.optString("lastSeenAt","未知")+"\n完整读取："+d.optString("lastFullReadAt","尚未读取")+"\n已保存属性："+attrs.length()+" 个\n状态来源："+(selectedReady()&&ble.sessionRead?"本次连接已读取":"历史缓存 / 尚未读取")+"\n固件版本："+(d.optJSONObject("deviceInformation")!=null?d.optJSONObject("deviceInformation").optString("softwareVersion")+"　硬件："+d.optJSONObject("deviceInformation").optString("hardwareVersion"):"未报告")+(d.has("versionReplyHex")?"\n版本原始值："+d.optString("versionReplyHex"):""));
            if(details!=null){StringBuilder s=new StringBuilder();List<Integer> ids=new ArrayList<>();Iterator<String> iterator=attrs.keys();while(iterator.hasNext())ids.add(Integer.parseInt(iterator.next()));Collections.sort(ids);for(int id:ids){JSONObject a=attrs.optJSONObject(String.valueOf(id));s.append('#').append(id).append(' ').append(a.optString("label")).append("\n").append(a.optString("display")).append("\n").append(a.optString("updatedAt")).append("\n");}String content=s.toString();if(!content.equals(lastDetail)){details.setText(content);lastDetail=content;}}
            int rgb=store.integer(selected,6,0xffffff)&0xffffff;JSONObject colorAttr=store.attribute(selected,6);if(colorAttr!=null)pickerColor=rgb;if(currentColor!=null)currentColor.setText("电源："+(store.attribute(selected,1)==null?"未知":store.integer(selected,1,0)==0?"关闭":"开启")+"　颜色："+(colorAttr==null?"未知":rgbString(rgb)));
            if(brightness!=null&&!draggingBrightness){int n=store.integer(selected,7,-1);brightnessLabel.setText(n<0?"亮度：尚未读取":"设备回读亮度："+n+"%");if(n>=1&&n<=100)brightness.setProgress(n-1);}
            if(segmentGrid!=null){JSONObject a=store.attribute(selected,15);String raw=a==null?"":a.optString("rawValueHex");if(!segmentDirty&&!raw.equals(lastSegments)){lastSegments=raw;draft=store.segmentColors(selected);drawSegments();}if(draft.length==0)segmentLabel.setText("尚未读取分段信息，请连接后读取状态");}
        }else if(overview!=null)overview.setText("请先在设备页选择一台灯。");
        captureScene();if(logView!=null){StringBuilder s=new StringBuilder();int skip=Math.max(0,store.events.size()-65),i=0;for(JSONObject e:store.events){if(i++<skip)continue;s.append(e.optString("at")).append(' ').append(e.optString("kind")).append(' ').append(e.optString("message")).append('\n');}logView.setText(s.toString());}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] result){super.onRequestPermissionsResult(r,p,result);if(ble.permitted()&&!LocalHub.busy)connectRemembered();else notice("允许附近设备权限后可扫描灯");}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("selected",selected);b.putString("tab",tab);b.putString("pendingMime",pendingMime);b.putIntArray("draft",draft);b.putBoolean("segmentDirty",segmentDirty);b.putString("lastSegments",lastSegments);b.putString("settingsPage",settingsPage);super.onSaveInstanceState(b);}
    @Override protected void onStart(){super.onStart();foreground=true;}
    @Override protected void onStop(){foreground=false;stopAnimation();if(store!=null)store.save();super.onStop();}
    @Override protected void onDestroy(){LocalHub.get(this).remove(this);ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
