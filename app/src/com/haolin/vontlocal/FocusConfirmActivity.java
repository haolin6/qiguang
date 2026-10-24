package com.haolin.vontlocal;
import android.app.*;import android.content.*;import android.os.*;import android.net.Uri;import android.view.*;import android.widget.*;import org.json.*;

/** Widget PendingIntent opens this activity directly. No lighting write until confirmation. */
public final class FocusConfirmActivity extends Activity {
    FocusPolicy.Settings settings;LinkPolicy.Settings office;Button positive;TextView title,details;Ui u;
    static PendingIntent pending(Context c){Intent i=new Intent(c,FocusConfirmActivity.class).setData(Uri.parse("qiguang://focus/confirm")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(c,612,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void open(Context c){c.startActivity(new Intent(c,FocusConfirmActivity.class));}
    @Override public void onCreate(Bundle b){setTheme(Ui.dark(this)?R.style.FocusConfirmDark:R.style.FocusConfirmLight);super.onCreate(b);u=new Ui(this);settings=FocusStore.settings(this);office=SceneStore.settings(this,"work");LinearLayout root=u.column();root.setPadding(u.dp(this,22),u.dp(this,20),u.dp(this,22),u.dp(this,16));root.setBackground(u.shape(u.surface,24));
        title=u.text(root,"",22);details=u.text(root,"",15);u.gap(root,10);u.hint(root,"手机后台运行，需保持局域网连接。结束后停止后台提醒；手机强制退出或重启不会自动继续。");LinearLayout buttons=u.row();u.button(buttons,"保持当前状态",()->finish(),false);positive=u.button(buttons,"切换并开始",this::confirm,true);root.addView(buttons);setContentView(root);getWindow().setBackgroundDrawableResource(android.R.color.transparent);getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-u.dp(this,32),u.dp(this,420)),-2);render();}
    void render(){boolean active=FocusStore.running();String last=LinkPolicy.canonical(LinkStore.status(this).optString("action"));boolean night="bed".equals(last)||"wind_down".equals(last);title.setText(active?"办公提醒正在进行":night?"切换到办公并开始专注？":"开始办公专注？");
        details.setText(active?FocusStore.title(this)+"\n"+FocusStore.detail(this):"确认后将使用办公灯光："+office.brightness+"% · "+office.temperature+"K，关闭 Vont，并取消原有延时关灯。\n\n工作 "+settings.workMinutes+" 分钟，休息 "+settings.restMinutes+" 分钟，自动循环。\n休息灯光："+settings.restBrightness+"% · "+settings.restTemperature+"K。\n复工时恢复本次开始的办公参数。");positive.setEnabled(!active);positive.setText(night?"切换并开始":"开始本次专注");}
    void confirm(){if(FocusService.start(this,settings,office.brightness,office.temperature)){finish();}else{Toast.makeText(this,"未能开始，或已有专注任务正在运行",1).show();render();}}
    @Override public void onResume(){super.onResume();if(title!=null)render();}
}
