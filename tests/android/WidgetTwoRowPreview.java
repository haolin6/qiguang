package com.haolin.vontlocal;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;

/** Exact native widget render; emulator only, no light commands or credentials. */
public final class WidgetTwoRowPreview extends UiAcceptance {
    int dp(float n){return Ui.dp(context(),n);}
    void png(Bitmap b,String name)throws Exception{try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    void fixture(String phase)throws Exception{
        FocusStore.gate.end();ScreenbarClient.atomic(new File(context().getFilesDir(),FocusStore.FILE),"{}".getBytes("UTF-8"));
        ScreenbarClient.atomic(new File(context().getFilesDir(),LinkStore.STATUS),"{}".getBytes("UTF-8"));
        if(!phase.equals("idle")){FocusPolicy.Session s=FocusStore.gate.begin("preview-"+phase,new FocusPolicy.Settings(50,5,30,3500),100,4500);s.ready(phase,SystemClock.elapsedRealtime(),System.currentTimeMillis());Calendar clock=Calendar.getInstance();clock.set(Calendar.HOUR_OF_DAY,14);clock.set(Calendar.MINUTE,50);clock.set(Calendar.SECOND,0);clock.set(Calendar.MILLISECOND,0);if(clock.getTimeInMillis()<=System.currentTimeMillis())clock.add(Calendar.DATE,1);s.nextWall=clock.getTimeInMillis()+(phase.equals("resting")?300000:0);FocusStore.publish(context(),s,"布局预览");}
    }
    View widget(HomeActivity host,int width,int height,String phase,Map<Integer,Rect> reference)throws Exception{
        fixture(phase);FrameLayout parent=new FrameLayout(host);View view=LinkWidget.views(host,true,width).apply(host,parent);
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(height),View.MeasureSpec.EXACTLY));view.layout(0,0,dp(width),dp(height));
        int firstWidth=-1,firstY=-1;
        for(int id:new int[]{R.id.widget_work,R.id.widget_wind_down,R.id.widget_sleep,R.id.widget_off}){View v=view.findViewById(id);check(v.getHeight()==dp(44)&&v.getWidth()>=dp(44),"four scene targets >=44dp "+width+":"+id);if(firstWidth<0){firstWidth=v.getWidth();firstY=v.getTop();}check(Math.abs(firstWidth-v.getWidth())<=1&&firstY==v.getTop(),"four scenes equal width and baseline "+id);}
        for(int id:new int[]{R.id.widget_work,R.id.widget_wind_down,R.id.widget_sleep,R.id.widget_off,R.id.widget_title_area,R.id.widget_focus_info,R.id.widget_focus_action}){View v=view.findViewById(id);check(v.hasOnClickListeners(),"existing entry retained "+id);check(v.getHeight()>=dp(44)&&v.getWidth()>=dp(44),"entry touch size "+id);Rect r=new Rect();v.getDrawingRect(r);((android.view.ViewGroup)view).offsetDescendantRectToMyCoords(v,r);check(r.left>=0&&r.top>=0&&r.right<=view.getWidth()&&r.bottom<=view.getHeight(),"entry inside widget "+id);if(reference!=null){if(reference.containsKey(id))check(reference.get(id).equals(r),"control stable across states "+id);else reference.put(id,r);}}
        for(View v:nodes(view))if(v instanceof TextView){TextView t=(TextView)v;if(t.getText().length()==0)continue;check(t.getLayout()!=null&&t.getLayout().getHeight()+t.getCompoundPaddingTop()+t.getCompoundPaddingBottom()<=t.getHeight(),"text fits height: "+t.getText());for(int i=0;i<t.getLineCount();i++)check(t.getLayout().getLineWidth(i)<=t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+1,"text fits width: "+t.getText());if(width>=360)check(t.getLayout().getEllipsisCount(0)==0,"no ellipsis at normal width: "+t.getText());}
        View top=view.findViewById(R.id.widget_scene_row),bottom=view.findViewById(R.id.widget_reminder_row);check(top.getHeight()==dp(44)&&bottom.getHeight()==dp(44),"two fixed rows");check(top.getLeft()==bottom.getLeft()&&top.getRight()==bottom.getRight(),"rows share outer insets");check(bottom.getBottom()<=view.getHeight()-dp(12),"bottom breathing room");return view;
    }
    void board(HomeActivity host,String theme)throws Exception{
        context().getSharedPreferences("appearance-v1",0).edit().putString("theme",theme).commit();Ui u=new Ui(context());Bitmap bitmap=Bitmap.createBitmap(dp(408),dp(620),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(u.bg);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setColor(u.text);p.setTextSize(dp(22));canvas.drawText("栖光 · 两排桌面组件",dp(24),dp(44),p);p.setTypeface(Typeface.create("sans-serif",0));p.setColor(u.muted);p.setTextSize(dp(12));canvas.drawText("4×2 · 留白调整版",dp(24),dp(70),p);
        int index=0;Map<Integer,Rect> reference=new HashMap<>();
        for(String phase:new String[]{"idle","working","resting"}){p.setColor(u.text);p.setTextSize(dp(14));canvas.drawText(phase.equals("idle")?"待开启":phase.equals("working")?"工作中":"休息中",dp(24),dp(104+index*164),p);View v=widget(host,360,124,phase,reference);Bitmap card=Bitmap.createBitmap(dp(360),dp(124),Bitmap.Config.ARGB_8888);v.draw(new Canvas(card));png(card,"widget-4x2-"+phase+"-"+theme);canvas.drawBitmap(card,dp(24),dp(116+index*164),null);card.recycle();index++;}
        p.setColor(u.muted);p.setTextSize(dp(11));canvas.drawText("设计预览 · 实际桌面占格待验证",dp(24),dp(600),p);png(bitmap,"widget-4x2-board-"+theme);bitmap.recycle();
        for(int width:new int[]{260,300,400})widget(host,width,124,"idle",null);widget(host,360,144,"idle",null);
    }
    @Override public void onStart(){int code=0;try{
        if(!(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish")))throw new IOException("Emulator only");folder=new File(context().getFilesDir(),"widget-two-row-preview");folder.mkdirs();report.put("runId",runId).put("fontScale",context().getResources().getConfiguration().fontScale).put("scope","Native RemoteViews layout only, synthetic states; no phone or lamp control");check(!new File(context().getFilesDir(),ScreenbarClient.SEALED).exists(),"no real credentials");FocusStore.saveSettings(context(),new FocusPolicy.Settings(50,5,30,3500));HomeActivity host=(HomeActivity)open(HomeActivity.class);main(()->{for(String theme:new String[]{"light","dark"})board(host,theme);fixture("idle");return true;});finishActive();report.put("status","passed").put("checkCount",count).put("checks",checks).put("physicalDeviceTested",false);
    }catch(Exception e){code=1;try{report.put("status","failed").put("error",e.toString()).put("checks",checks);}catch(Exception ignored){}}
        try{ScreenbarClient.atomic(new File(context().getFilesDir(),"widget-two-row-report.json"),report.toString(2).getBytes("UTF-8"));}catch(Exception e){code=1;}Bundle result=new Bundle();result.putString("result",report.toString());finish(code,result);
    }
}
