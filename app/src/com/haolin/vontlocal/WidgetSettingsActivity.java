package com.haolin.vontlocal;
import android.app.*;import android.os.*;import android.graphics.*;import android.graphics.drawable.Drawable;import android.view.*;import android.widget.*;

/** Saved widget background transparency. Preview controls never execute lamp actions. */
public final class WidgetSettingsActivity extends Activity {
    Ui u;SeekBar slider;TextView value,status;ImageView plate;boolean dirty;
    @Override public void onCreate(Bundle saved){Ui.prepare(this);super.onCreate(saved);u=new Ui(this);LinearLayout root=u.root(this);u.toolbar(this,root,"小组件外观","保持两排布局，自由调整背景");LinearLayout body=u.scrollBody(root),preview=u.card(body);u.text(preview,"透明度预览",20);u.hint(preview,"示例壁纸，仅预览外观");
        int width=Math.max(260,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-76);FrameLayout wallpaper=new FrameLayout(this);wallpaper.setBackground(new Wallpaper());int padding=u.dp(this,8);wallpaper.setPadding(padding,padding,padding,padding);preview.addView(wallpaper,new LinearLayout.LayoutParams(-1,u.dp(this,140)));View widget=LinkWidget.views(this,true,width).apply(this,wallpaper);wallpaper.addView(widget,new FrameLayout.LayoutParams(-1,u.dp(this,124)));disableActions(widget);widget.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);plate=widget.findViewById(R.id.widget_background_layer);
        LinearLayout options=u.card(body);u.text(options,"背景透明度",20);value=u.text(options,"",27);value.setTextColor(u.accent);slider=new SeekBar(this);slider.setMax(100);slider.setContentDescription("小组件背景透明度");options.addView(slider,new LinearLayout.LayoutParams(-1,u.dp(this,48)));u.styleTree(slider);LinearLayout labels=u.row();labels.addView(u.label("0% · 不透明",13,u.muted),new LinearLayout.LayoutParams(0,-2,1));labels.addView(u.label("100% · 全透明",13,u.muted));options.addView(labels);u.gap(options,12);LinearLayout quick=u.row();for(int n:new int[]{0,50,100})u.button(quick,n+"%",()->{slider.setProgress(n);save();},false);options.addView(quick);u.hint(options,"只改变组件底板，文字、图标和按钮保持清晰。应用于本机所有栖光组件。");status=u.hint(options,"松手自动保存，并更新桌面组件");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){save();}public void onProgressChanged(SeekBar s,int n,boolean user){dirty=true;render(n);}});slider.setProgress(WidgetAppearance.transparency(this));render(slider.getProgress());dirty=false;
    }
    void disableActions(View v){v.setOnClickListener(null);v.setClickable(false);v.setFocusable(false);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)disableActions(((ViewGroup)v).getChildAt(i));}
    void render(int n){value.setText(n+"%");plate.setImageAlpha(WidgetAppearance.alpha(n));}
    void save(){try{WidgetAppearance.save(this,slider.getProgress());dirty=false;status.setText("已保存，桌面组件已更新");}catch(Exception e){status.setText("未能保存，请重试");}}
    @Override public void onPause(){if(dirty)save();super.onPause();}
    final class Wallpaper extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        public void draw(Canvas canvas){Rect b=getBounds();paint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{0xffcbd5c8,0xffa8beb8,0xffb9c8d7},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(new RectF(b),u.dp(WidgetSettingsActivity.this,18),u.dp(WidgetSettingsActivity.this,18),paint);}
        public void setAlpha(int n){paint.setAlpha(n);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
