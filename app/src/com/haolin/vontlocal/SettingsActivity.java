package com.haolin.vontlocal;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;
public final class SettingsActivity extends Activity {
    Button widgetAppearanceEntry;
    @Override public void onCreate(Bundle b){Ui.prepare(this);super.onCreate(b);Ui u=new Ui(this);LinearLayout root=u.root(this);u.toolbar(this,root,"设置与帮助","栖光 0.8.3 · 你的本地灯光");LinearLayout body=u.scrollBody(root),theme=u.card(body);u.text(theme,"外观",21);u.hint(theme,"浅色清爽，深色适合夜间。也可以跟随手机系统。");String current=Ui.appearance(this);String[] values={"system","light","dark"},names={"跟随系统","暖米白","夜间炭灰"};for(int i=0;i<values.length;i++){final String value=values[i];u.button(theme,(current.equals(value)?"✓  ":"")+names[i],()->{getSharedPreferences("appearance-v1",0).edit().putString("theme",value).commit();LinkWidget.updateAll(this);recreate();},current.equals(value));}
        widgetAppearanceEntry=u.button(theme,"小组件背景透明度",()->startActivity(new Intent(this,WidgetSettingsActivity.class)),false);
        LinearLayout help=u.card(body);u.text(help,"三个时刻，一键就好",21);u.hint(help,"办公：明亮略暖，只留挂灯。\n睡前：更暖更柔和，只留挂灯。\n床上：两组低亮暖光，到时间一起关闭。");u.hint(help,"点场景卡片立即执行；点铅笔编辑下次使用的参数。保存不会改变现在的灯光或正在运行的计时。再次点床上会重新计时。");u.hint(help,"办公、睡前会取消两组旧倒计时并关闭 Vont。全关优先中止旧任务。设置成功后，床上倒计时由灯执行，软件无需常驻。");
        LinearLayout reminder=u.card(body);u.text(reminder,"办公提醒",21);u.hint(reminder,"默认工作50分钟、休息5分钟，休息光30% / 3500K；参数可调整保存。桌面组件的开始会打开确认页，确认后才切到办公。单独结束不调灯，切换场景或全关会停止后续提醒。");u.hint(reminder,"自定义提醒由手机后台执行，需要同一局域网；显示常驻通知，可直接结束。系统中断后需重新确认，不会自动重放旧调光。此功能的真实光变、锁屏和后台表现待真机验收。");u.button(reminder,"设置办公提醒",()->startActivity(new Intent(this,FocusSettingsActivity.class)),false);LinearLayout widget=u.card(body);u.text(widget,"把场景放到桌面",21);u.hint(widget,"两排组件：上排是办公、睡前、床上、全关，下排是栖光标识、办公提醒和开始／结束。点标识打开首页，点提醒文字查看参数；开始先确认。预计时间是本次计划，不代表实时在线状态。");
        LinearLayout connection=u.card(body);u.text(connection,"连接与原有规则",21);u.hint(connection,"Vont 需要打开蓝牙和附近设备权限；挂灯需要手机处于同一局域网。没有连接时会显示具体未确认的设备，恢复连接后可重试。");u.hint(connection,"米家自动化、固定定时和遥控器继续有效，它们可能改变灯光。场景不会重置灯具、解绑或升级固件。");
        LinearLayout more=u.card(body);u.text(more,"设备与资料",21);u.button(more,"Vont 设备管理与导出",()->startActivity(new Intent(this,MainActivity.class).putExtra("entryTab","设置")),false);u.button(more,"挂灯设置、遥控器与导出",()->startActivity(new Intent(this,ScreenbarActivity.class).putExtra("entryTab","设置")),false);u.hint(more,"栖光保留原版应用与本机连接配置。导出报告不包含挂灯连接密钥。");
    }
    @Override public void onResume(){super.onResume();if(widgetAppearanceEntry!=null)widgetAppearanceEntry.setText("小组件背景透明度 · "+WidgetAppearance.transparency(this)+"%");}
}
