package com.haolin.vontlocal;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Local lighting and settings with explicit writes and fresh readback. */
public final class ScreenbarActivity extends Activity {
    Toast lastNotice;Ui design;LinearLayout nav;String settingsPage="计时";int modeSlot=0;EditText modeB,modeK;AlertDialog modeDialog;String restoreB="",restoreK="";boolean restoreMode=false;final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final List<View> controls=new ArrayList<>();
    ScreenbarClient client;ScreenbarClient.State state;LinearLayout body;ScrollView scroll;TextView status,summary;Button refresh;
    volatile boolean resumed=false,destroyed=false;boolean busy=false,fresh=false;volatile long foregroundEpoch=0;
    String tab="调节",credentialError="";
    static final String[] TABS={"调节","模式","设置"};

    @Override public void onCreate(Bundle saved){
        Ui.prepare(this);super.onCreate(saved);design=new Ui(this);if(saved!=null){tab=saved.getString("tab","调节");settingsPage=saved.getString("settingsPage","计时");modeSlot=saved.getInt("modeSlot",0);restoreB=saved.getString("modeB","");restoreK=saved.getString("modeK","");restoreMode=modeSlot>0;}if(saved==null&&getIntent().hasExtra("entryTab"))tab=getIntent().getStringExtra("entryTab");if(!Arrays.asList(TABS).contains(tab))tab="调节";
        try{client=new ScreenbarClient(this);}catch(Exception error){credentialError="挂灯连接配置不可用，请重新准备授权；无需重置或解绑设备。";}
        LinearLayout root=design.root(this);design.toolbar(this,root,"屏幕挂灯 1S","让桌面，亮得刚刚好");
        LinearLayout top=column();top.setPadding(dp(20),0,dp(20),dp(8));status=text(top,"准备读取灯光状态",13);status.setTextColor(design.muted);summary=text(top,"尚未读取",16);root.addView(top);
        LinearLayout links=row();top.addView(links);refresh=button(links,"刷新",()->runOperation("刷新状态",null,null),false);button(links,"打开米家",()->{try{startActivity(new Intent().setComponent(new ComponentName("com.xiaomi.smarthome","com.xiaomi.smarthome.SmartHomeMainActivity")));}catch(Exception error){notice("请从桌面打开米家");}},false);
        nav=row();nav.setPadding(dp(16),0,dp(16),dp(8));root.addView(nav);
        scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(dp(20),0,dp(20),dp(16));body=column();body.setPadding(dp(16),dp(4),dp(16),dp(24));body.setBackground(design.shape(design.surface,22));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showTab();
        if(!credentialError.isEmpty())status.setText(credentialError);
        else try{
            File cached=new File(getFilesDir(),ScreenbarClient.STATE);
            if(cached.exists()){
                JSONObject value=new JSONObject(new String(ScreenbarClient.readBytes(cached),StandardCharsets.UTF_8));
                JSONObject p=value.getJSONObject("properties");summary.setText("上次读取（正在核对当前状态）\n"+(p.optBoolean("2.1")?"开启":"关闭")+" · "+p.optInt("2.2")+"% · "+p.optInt("2.3")+"K");
            }
        }catch(Exception ignored){}
    }
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(design.text);t.setPadding(0,dp(5),0,dp(5));parent.addView(t);return t;}
    Button button(LinearLayout parent,String title,Runnable action,boolean requiresRead){Button b=design.button(parent,title,()->{try{action.run();}catch(IllegalArgumentException error){notice(error.getMessage());}},false);if(requiresRead){controls.add(b);b.setEnabled(fresh&&!busy&&resumed);}return b;}
    void notice(String value){if(lastNotice!=null)lastNotice.cancel();lastNotice=Toast.makeText(this,value,Toast.LENGTH_LONG);lastNotice.show();}
    void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    void section(String title){design.title(body,title);}
    void hint(String value){design.hint(body,value);}
    int number(int s,int p,int fallback){return state==null?fallback:state.number(s,p);}
    boolean flag(int s,int p){return state!=null&&state.flag(s,p);}
    EditText numeric(String label,String description,int value,int low,int high,boolean slider){
        text(body,label+"（"+low+"–"+high+"）",16);EditText edit=new EditText(this);edit.setSingleLine(true);edit.setSelectAllOnFocus(true);edit.setInputType(InputType.TYPE_CLASS_NUMBER);edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setContentDescription(description);edit.setText(String.valueOf(value));edit.setTextSize(18);body.addView(edit);controls.add(edit);edit.setEnabled(fresh&&!busy&&resumed);
        edit.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){hideKeyboard();edit.clearFocus();applyLive(description,edit);return true;}return false;});
        if(slider){
            SeekBar seek=new SeekBar(this);seek.setMax(high-low);seek.setProgress(Math.max(0,value-low));seek.setContentDescription(description+"滑块");body.addView(seek);controls.add(seek);seek.setEnabled(fresh&&!busy&&resumed);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){applyLive(description,edit);}public void onProgressChanged(SeekBar s,int n,boolean user){if(user)edit.setText(String.valueOf(low+n));}});
            edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){try{int n=Integer.parseInt(s.toString());if(n>=low&&n<=high)seek.setProgress(n-low);}catch(Exception ignored){}}public void afterTextChanged(Editable e){}});
        }
        return edit;
    }
    Spinner spinner(String label,String description,String[] choices,int selected){
        text(body,label,16);Spinner v=new Spinner(this);v.setContentDescription(description);v.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,choices));v.setSelection(Math.max(0,Math.min(selected,choices.length-1)));body.addView(v);controls.add(v);v.setEnabled(fresh&&!busy&&resumed);return v;
    }
    int input(EditText edit,int low,int high){return ScreenbarPolicy.integer(edit.getText().toString(),low,high);}
    ScreenbarPolicy.Change change(int s,int p,Object value){return new ScreenbarPolicy.Change(s,p,value);}
    void set(String label,ScreenbarPolicy.Change... changes){hideKeyboard();runOperation(label,changes,null);}
    void action(String label,int service,int action,Object... values){hideKeyboard();runOperation(label,null,new ScreenbarPolicy.Call(service,action,values));}
    void settingToggle(String name,int s,int p){hint(name+"："+(flag(s,p)?"开启":"关闭"));boolean next=!flag(s,p);button(body,(next?"开启":"关闭")+name,()->set(name,change(s,p,next)),true);}
    void applyLive(String desc,EditText field){try{if("挂灯亮度数值".equals(desc))set("设置亮度",change(2,2,input(field,1,100)));else if("挂灯色温数值".equals(desc))set("设置色温",change(2,3,input(field,2700,6500)));}catch(IllegalArgumentException e){field.setError(e.getMessage());}}
    void showTab(){
        body.removeAllViews();controls.clear();nav.removeAllViews();for(String name:TABS){Button b=design.button(nav,name,()->{hideKeyboard();tab=name;showTab();},name.equals(tab));b.setContentDescription("挂灯页面-"+name);}
        switch(tab){case "调节":lighting();break;case "模式":modes();break;case "设置":settings();break;}
        design.styleTree(body);for(View view:controls)view.setEnabled(fresh&&!busy&&resumed);
    }
    void settings(){LinearLayout links=row();body.addView(links);for(String name:new String[]{"计时","通电","遥控器","资料"})button(links,name,()->{settingsPage=name;showTab();},false);switch(settingsPage){case "计时":timers();break;case "通电":defaults();break;case "遥控器":remote();break;case "资料":more();break;}}
    void lighting(){
        section("开关与调光");LinearLayout power=row();body.addView(power);button(power,"开启挂灯",()->set("开启挂灯",change(2,1,true)),true);button(power,"关闭挂灯",()->set("关闭挂灯",change(2,1,false)),true);
        EditText brightness=numeric("亮度 %","挂灯亮度数值",number(2,2,100),1,100,true);
        EditText temperature=numeric("色温 K","挂灯色温数值",number(2,3,5000),2700,6500,true);
        hint("拖动后松手即生效；精确输入后按键盘“完成”。每次操作都会核对实际灯光，从米家返回时刷新。");
    }
    void modes(){
        section("挂灯内置情景");hint("当前："+ScreenbarPolicy.modeName(number(3,9,0)));
        int[] presets={6,5,7,9,8,10};for(int rowIndex=0;rowIndex<2;rowIndex++){LinearLayout choices=row();body.addView(choices);for(int j=0;j<3;j++){final int target=presets[rowIndex*3+j];button(choices,ScreenbarPolicy.modeName(target),()->action("应用"+ScreenbarPolicy.modeName(target),3,15,target),true);}}
        button(body,"退出情景模式",()->set("自由调光",change(3,9,0)),true);
        section("我的模式");hint("编辑挂灯内原有四个位置。保存参数与应用模式是独立操作。");
        for(int slot=1;slot<=4;slot++){final int n=slot;int b=19+(slot-1)*2;LinearLayout card=design.card(body);text(card,"我的模式 "+slot,18);design.hint(card,number(3,b,50)+"% · "+number(3,b+1,4000)+"K");LinearLayout actions=row();card.addView(actions);button(actions,"编辑",()->editMode(n,"",""),true);button(actions,"应用",()->action("应用我的模式"+n,3,15,n),true);}
    }
    void editMode(int slot,String brightnessDraft,String temperatureDraft){if(!fresh||busy)return;modeSlot=slot;LinearLayout form=column();form.setPadding(dp(20),dp(4),dp(20),dp(8));LinearLayout original=body;body=form;int b=19+(slot-1)*2;modeB=numeric("亮度 %","模式"+slot+"亮度",number(3,b,50),1,100,false);modeK=numeric("色温 K","模式"+slot+"色温",number(3,b+1,4000),2700,6500,false);body=original;if(!brightnessDraft.isEmpty())modeB.setText(brightnessDraft);if(!temperatureDraft.isEmpty())modeK.setText(temperatureDraft);design.styleTree(form);
        modeDialog=new AlertDialog.Builder(this).setTitle("编辑我的模式 "+slot).setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();modeDialog.setOnDismissListener(d->{modeSlot=0;modeDialog=null;});modeDialog.setOnShowListener(d->modeDialog.getButton(-1).setOnClickListener(v->{try{int bv=input(modeB,1,100),kv=input(modeK,2700,6500);modeDialog.dismiss();set("保存我的模式"+slot,change(3,b,bv),change(3,b+1,kv));}catch(IllegalArgumentException e){modeB.setError(e.getMessage());}}));modeDialog.show();
    }
    void timers(){
        section("延时关灯");hint("当前："+(flag(3,4)?"已开启":"未开启")+" · "+number(3,2,30)+"分钟");
        EditText minutes=numeric("延时时长 分钟","延时分钟数",number(3,2,30),1,60,false);
        button(body,"开灯并启动延时关灯",()->action("启动延时关灯",3,2,input(minutes,1,60)),true);button(body,"取消延时关灯",()->set("取消延时关灯",change(3,4,false)),true);
        section("专注模式");hint("当前："+(flag(3,18)?"开启":"关闭"));
        EditText work=numeric("工作时长 分钟","工作分钟数",number(3,16,45),1,60,false),rest=numeric("休息时长 分钟","休息分钟数",number(3,17,10),1,60,false);
        button(body,"保存专注时长",()->set("保存专注时长",change(3,16,input(work,1,60)),change(3,17,input(rest,1,60))),true);
        LinearLayout buttons=row();body.addView(buttons);button(buttons,"开启专注模式",()->set("开启专注模式",change(3,18,true)),true);button(buttons,"关闭专注模式",()->set("关闭专注模式",change(3,18,false)),true);
        hint("这里控制挂灯自身的延时和专注。米家固定时刻定时、自动化仍由米家保留。");
    }
    void defaults(){
        section("通电与默认灯光");settingToggle("通电后默认开灯",2,4);settingToggle("恢复断电前灯光",3,1);hint("开启断电记忆后恢复断电前灯光；关闭后使用已保存的默认灯光。");
        button(body,"将当前灯光保存为默认",()->new AlertDialog.Builder(this).setTitle("保存默认灯光").setMessage("将覆盖挂灯原来的默认灯光，以当前亮度和色温作为新的默认值。是否保存？").setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->action("保存默认灯光",3,1)).show(),true);
    }
    void remote(){
        section("遥控器绑定");String paired=state==null?"":state.paired();hint(state==null?"尚未读取":("已绑定 "+(paired.isEmpty()?0:paired.split(",").length)+" 个遥控器"));hint("保留原米家和遥控器绑定。这里只修改按键功能，不修改配对列表。");
        button(body,"查看遥控器配对说明",()->new AlertDialog.Builder(this).setTitle("遥控器配对").setMessage("原遥控器可继续使用。新增配对需要按产品说明，在挂灯通电及遥控器装入电池后的规定时间内长按实体遥控器；本软件不会自动解除已有配对。").setPositiveButton("知道了",null).show(),false);
        section("自定义长按");settingToggle("自定义长按",3,8);String[] choices=ScreenbarPolicy.MODE_NAMES.clone();choices[0]="不使用";
        Spinner one=spinner("长按模式位置1","长按位置1",choices,number(3,13,0)),two=spinner("长按模式位置2","长按位置2",choices,number(3,14,0)),three=spinner("长按模式位置3","长按位置3",choices,number(3,15,0));
        button(body,"保存长按模式组合",()->action("保存长按模式组合",3,13,one.getSelectedItemPosition(),two.getSelectedItemPosition(),three.getSelectedItemPosition()),true);button(body,"读取长按配置",()->action("读取长按配置",3,14),true);
        section("自定义双击");settingToggle("自定义双击",3,6);Spinner doubleClick=spinner("双击触发功能","双击功能",new String[]{"启动专注模式","启动延时关灯"},number(3,7,1)-1);
        button(body,"保存双击功能",()->set("保存双击功能",change(3,7,doubleClick.getSelectedItemPosition()+1)),true);
    }
    void more(){
        section("快捷调节");button(body,"切换开关",()->action("切换开关",2,1),true);
        String[] names={"亮度增加","亮度降低","循环亮度","开灯或切换亮度","色温增加","色温降低","循环色温","开灯或切换色温"};
        for(int i=0;i<names.length;i++){final int id=i+3;final String name=names[i];button(body,name,()->action(name,3,id),true);}
        section("设备信息");hint(state==null?"尚未读取":("型号："+state.information.optString("model")+"\n固件："+state.information.optString("firmware")+"\n硬件："+state.information.optString("hardware")+"\n地址："+state.information.optString("ip")+"\n已读取：27项属性"));
        button(body,"导出挂灯状态 JSON",()->exportState(false),true);button(body,"导出挂灯可读报告 TXT",()->exportState(true),true);
        hint("导出包含本次读取的灯光、模式和设置，不包含连接密钥；遥控器地址仅保留数量和校验摘要。米家平台的固定时刻定时、智能场景、灯组与共享继续在米家管理。");
    }

    JSONObject exportSnapshot()throws Exception{
        if(state==null||!fresh)throw new IOException("请先读取挂灯状态");
        String[] labels={"开关","亮度百分比","色温K","通电后默认开灯","设备模式","恢复断电前灯光","延时分钟数","延时关灯开启","自定义双击开启","双击功能(1专注/2延时)","自定义长按开启","情景模式","长按位置1","长按位置2","长按位置3","工作分钟数","休息分钟数","专注开启","模式1亮度","模式1色温","模式2亮度","模式2色温","模式3亮度","模式3色温","模式4亮度","模式4色温","绑定遥控器数量及摘要"};
        JSONObject snapshot=state.portable(),names=new JSONObject();
        for(int i=0;i<ScreenbarClient.KEYS.length;i++)names.put(ScreenbarClient.KEYS[i][0]+"."+ScreenbarClient.KEYS[i][1],labels[i]);
        return snapshot.put("appVersion","0.8.3").put("propertyLabels",names).put("modeNames",new JSONArray(Arrays.asList(ScreenbarPolicy.MODE_NAMES)))
            .put("notes",new JSONArray().put("时间为实际回读时间；不保证导出后状态仍未变化").put("已保存默认灯光的具体值没有可读属性，未包含在此快照").put("不包含米家云端定时、自动化、账号凭据或固件"));
    }
    void exportState(boolean readable){
        try{
            JSONObject snapshot=exportSnapshot();String content;
            if(readable){
                StringBuilder out=new StringBuilder("屏幕挂灯1S 本地状态报告\n读取时间：").append(new Date(state.readAt)).append("\n设备信息：").append(state.information.toString()).append("\n\n");
                JSONObject names=snapshot.getJSONObject("propertyLabels"),props=snapshot.getJSONObject("properties");
                for(int[] key:ScreenbarClient.KEYS){String id=key[0]+"."+key[1];out.append(id).append(" ").append(names.getString(id)).append("：").append(props.get(id)).append("\n");}
                out.append("\n当前情景：").append(ScreenbarPolicy.modeName(state.number(3,9))).append("\n说明：").append(snapshot.getJSONArray("notes").toString());content=out.toString();
            }else content=snapshot.toString(2);
            ScreenbarClient.atomic(new File(getFilesDir(),"screenbar-pending-export"),content.getBytes(StandardCharsets.UTF_8));
            getPreferences(0).edit().putBoolean("screenbarExportText",readable).apply();
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(readable?"text/plain":"application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"screenbar-state-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+(readable?".txt":".json"));startActivityForResult(intent,74);
        }catch(Exception error){notice("无法准备导出，请刷新后重试");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=74)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){notice("已取消导出");return;}
        try{
            byte[] value=ScreenbarClient.readBytes(new File(getFilesDir(),"screenbar-pending-export"));
            try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("无法写入目标文件");out.write(value);}
            ScreenbarClient.atomic(new File(getFilesDir(),getPreferences(0).getBoolean("screenbarExportText",false)?"screenbar-last-export.txt":"screenbar-last-export.json"),value);notice("挂灯状态已导出");
        }catch(Exception error){notice("导出未完成，请重试");}
    }

    static boolean same(Object a,Object b){
        if(a instanceof Number&&b instanceof Number)return ((Number)a).longValue()==((Number)b).longValue();return Objects.equals(a,b);
    }
    boolean expected(ScreenbarClient.State before,ScreenbarClient.State after,ScreenbarPolicy.Change[] changes,ScreenbarPolicy.Call call){
        if(after==null)return false;
        if(changes!=null){for(ScreenbarPolicy.Change c:changes)if(!same(c.value,after.value(c.service,c.property)))return false;return true;}
        if(call==null)return true;
        if(call.service==2)return before!=null&&before.flag(2,1)!=after.flag(2,1);
        switch(call.action){
            case 1:return true; // The saved default is not exposed as readable attributes.
            case 2:return after.flag(2,1)&&after.flag(3,4)&&after.number(3,2)==(Integer)call.values[0];
            case 13:return after.number(3,13)==(Integer)call.values[0]&&after.number(3,14)==(Integer)call.values[1]&&after.number(3,15)==(Integer)call.values[2];
            case 14:return true;
            case 15:return after.number(3,9)==(Integer)call.values[0];
            case 3:return before!=null&&after.number(2,2)>=before.number(2,2);
            case 4:return before!=null&&after.number(2,2)<=before.number(2,2);
            case 7:return before!=null&&after.number(2,3)>=before.number(2,3);
            case 8:return before!=null&&after.number(2,3)<=before.number(2,3);
            default:return true; // Native cycle result is shown in the actual readback.
        }
    }
    boolean isLightingChange(ScreenbarPolicy.Change[] changes,ScreenbarPolicy.Call call){if(changes!=null)for(ScreenbarPolicy.Change c:changes)if(c.service==2&&(c.property==1||c.property==2||c.property==3)||c.service==3&&(c.property==9||c.property==18))return true;return call!=null&&(call.service==2||call.service==3&&(call.action==2||call.action==15));}
    void runOperation(String label,ScreenbarPolicy.Change[] changes,ScreenbarPolicy.Call call){
        boolean writing=changes!=null||call!=null;
        if(writing&&isLightingChange(changes,call))FocusService.end(this,"手动调节挂灯，办公提醒结束");
        if(client==null||destroyed){if(!credentialError.isEmpty())notice(credentialError);return;}
        if(busy)return;
        if(writing&&LocalHub.busy){notice("两组灯联动进行中，请稍后单独调节");return;}
        if(writing&&(!resumed||!fresh)){notice("请先刷新挂灯状态");return;}
        if(!resumed)return;
        final long epoch=foregroundEpoch;busy=true;fresh=false;refresh.setEnabled(false);for(View control:controls)control.setEnabled(false);
        status.setText(writing?"正在执行并核对："+label:"正在读取挂灯状态……");
        worker.execute(()->{synchronized(LocalHub.SCREENBAR_LOCK){
            ScreenbarClient.State before=null,after=null;boolean acknowledged=false;String failure="";JSONObject receipt=new JSONObject();
            try{
                receipt.put("appVersion","0.8.3").put("source","phone").put("label",label).put("startedAtEpochMs",System.currentTimeMillis());
                if(writing){
                    if(!resumed||foregroundEpoch!=epoch||LocalHub.busy)throw new IOException("页面或联动任务已切换，本次操作未发送");
                    before=client.read();
                    if(!resumed||foregroundEpoch!=epoch||LocalHub.busy)throw new IOException("页面或联动任务已切换，本次操作未发送");
                    if(changes!=null){
                        JSONArray requested=new JSONArray();for(ScreenbarPolicy.Change c:changes)requested.put(new JSONObject().put("siid",c.service).put("piid",c.property).put("value",c.value));
                        receipt.put("kind","set").put("requested",requested).put("acknowledgement",client.write(changes));
                    }else{
                        JSONArray args=new JSONArray();for(Object value:call.values)args.put(value);
                        receipt.put("kind","action").put("siid",call.service).put("aiid",call.action).put("input",args).put("acknowledgement",client.call(call));
                    }
                    acknowledged=true;
                }else receipt.put("kind","read");
            }catch(Exception error){failure=error instanceof IOException?error.getMessage():"通信未完成，请核对最新状态";}
            try{after=client.read();}catch(Exception error){if(failure.isEmpty())failure="未能读取当前挂灯状态，请确认 Wi-Fi 后刷新";}
            boolean matched=expected(before,after,changes,call);
            boolean readable=call==null||!(call.service==3&&call.action==1);
            try{
                receipt.put("acknowledged",acknowledged).put("readBackAvailable",after!=null).put("targetReadable",readable)
                    .put("expectedValuesMatched",matched&&readable).put("completedAtEpochMs",System.currentTimeMillis());
                if(before!=null)receipt.put("before",before.portable());if(after!=null)receipt.put("after",after.portable());
                if(!failure.isEmpty())receipt.put("error",failure);client.record(receipt);
            }catch(Exception ignored){}
            final ScreenbarClient.State result=after;final String problem=failure;final boolean ack=acknowledged,confirmed=matched,targetReadable=readable;
            ui.post(()->{
                busy=false;if(destroyed)return;refresh.setEnabled(true);if(result!=null)state=result;
                if(resumed&&foregroundEpoch!=epoch){fresh=false;runOperation("返回页面刷新",null,null);return;}
                fresh=result!=null&&resumed;
                if(state!=null)summary.setText((fresh?"本次设备回读":"上次设备回读")+"："+(state.flag(2,1)?"开启":"关闭")+" · "+state.number(2,2)+"% · "+state.number(2,3)+"K\n"+ScreenbarPolicy.modeName(state.number(3,9))+" · "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(state.readAt)));
                String message;
                if(!problem.isEmpty())message=problem+(result!=null?"；已显示实际读取结果":"");
                else if(writing&&(!ack||!confirmed))message="操作结果未确认，请核对实际读取结果";
                else if(writing&&!targetReadable)message="设备已确认保存，当前灯光状态已刷新";
                else message=writing?label+"完成，已回读核对":"状态已刷新 · 27项设置";
                status.setText(message);int y=scroll.getScrollY();showTab();scroll.post(()->{if(!destroyed)scroll.scrollTo(0,y);});
                if(restoreMode&&fresh){restoreMode=false;editMode(modeSlot,restoreB,restoreK);}if(writing&&resumed)notice(message);
            });
        }});
    }
    @Override protected void onResume(){super.onResume();resumed=true;foregroundEpoch++;fresh=false;ui.postDelayed(()->{if(!destroyed&&resumed&&!busy)runOperation("进入页面刷新",null,null);},120);}
    @Override protected void onPause(){if(lastNotice!=null)lastNotice.cancel();resumed=false;fresh=false;super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("tab",tab);out.putString("settingsPage",settingsPage);if(modeSlot>0&&modeB!=null&&modeK!=null){out.putInt("modeSlot",modeSlot);out.putString("modeB",modeB.getText().toString());out.putString("modeK",modeK.getText().toString());}super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
