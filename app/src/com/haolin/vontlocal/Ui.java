package com.haolin.vontlocal;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Small native design system shared by screens, dialogs and custom icons. */
final class Ui {
    static int dp(Context c,float n){return (int)(c.getResources().getDisplayMetrics().density*n+.5f);}
    static String appearance(Context c){return c.getSharedPreferences("appearance-v1",0).getString("theme","system");}
    static boolean dark(Context c){String s=appearance(c);return "dark".equals(s)||"system".equals(s)&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    static void prepare(Activity a){a.setTheme(dark(a)?R.style.QiguangDark:R.style.QiguangLight);}
    final Context c;final boolean dark;final int bg,surface,text,muted,accent,soft,line;
    Ui(Context c){this.c=c;dark=dark(c);bg=color(dark?"#151A1B":"#F7F3EC");surface=color(dark?"#22292A":"#FFFDFA");text=color(dark?"#F4EEE5":"#292E2C");muted=color(dark?"#BDC4BF":"#69716B");accent=color(dark?"#F1BE79":"#975B1D");soft=color(dark?"#383027":"#F4E5CF");line=color(dark?"#3C4543":"#E7E1D7");}
    static int color(String s){return Color.parseColor(s);}
    void window(Activity a){a.getWindow().setStatusBarColor(bg);a.getWindow().setNavigationBarColor(bg);a.getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    LinearLayout column(){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(c);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView label(String s,int sp,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setLineSpacing(dp(c,2),1);return t;}
    TextView text(LinearLayout p,String s,int sp){TextView t=label(s,sp,text);t.setPadding(0,dp(c,5),0,dp(c,5));p.addView(t);return t;}
    TextView hint(LinearLayout p,String s){TextView t=text(p,s,13);t.setTextColor(muted);return t;}
    void title(LinearLayout p,String s){TextView t=text(p,s,21);t.setTypeface(Typeface.create("sans-serif-medium",0));t.setPadding(0,dp(c,20),0,dp(c,10));}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    Drawable touch(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(dark?0x22ffffff:0x15975b1d),shape(color,radius),shape(Color.WHITE,radius));}
    LinearLayout card(LinearLayout parent){LinearLayout card=column();card.setPadding(dp(c,18),dp(c,14),dp(c,18),dp(c,14));card.setBackground(shape(surface,22));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,12);parent.addView(card,lp);return card;}
    void gap(LinearLayout p,int height){View v=new View(c);p.addView(v,new LinearLayout.LayoutParams(1,dp(c,height)));}
    Button button(LinearLayout p,String s,Runnable click,boolean primary){Button b=new Button(c);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(primary?(dark?bg:Color.WHITE):text);b.setBackground(touch(primary?accent:soft,16));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setMinWidth(0);b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));b.setStateListAnimator(null);b.setOnClickListener(v->click.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(p.getOrientation()==0?0:-1,-2,p.getOrientation()==0?1:0);lp.setMargins(dp(c,2),dp(c,4),dp(c,2),dp(c,6));p.addView(b,lp);return b;}
    ImageButton iconButton(LinearLayout p,String kind,String desc,Runnable click){ImageButton b=new ImageButton(c);b.setImageDrawable(new Mark(kind,accent));b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setBackground(touch(soft,16));b.setContentDescription(desc);b.setOnClickListener(v->click.run());p.addView(b,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return b;}
    void toolbar(Activity a,LinearLayout parent,String title,String subtitle){LinearLayout r=row();r.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));iconButton(r,"back","返回",()->a.onBackPressed());LinearLayout labels=column();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(c,14);r.addView(labels,lp);TextView t=text(labels,title,23);t.setTypeface(Typeface.create("sans-serif-medium",0));if(subtitle!=null&&!subtitle.isEmpty())hint(labels,subtitle);parent.addView(r);}
    LinearLayout root(Activity a){window(a);LinearLayout r=column();r.setBackgroundColor(bg);a.setContentView(r);return r;}
    LinearLayout scrollBody(LinearLayout root){ScrollView sv=new ScrollView(c);sv.setFillViewport(false);sv.setVerticalScrollBarEnabled(false);sv.setClipToPadding(false);sv.setPadding(dp(c,20),0,dp(c,20),dp(c,20));LinearLayout b=column();sv.addView(b);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));return b;}
    LinearLayout tabs(LinearLayout root,String[] names,String selected,java.util.function.Consumer<String> change){LinearLayout row=row();row.setPadding(dp(c,16),dp(c,2),dp(c,16),dp(c,10));for(String n:names){Button b=button(row,n,()->change.accept(n),n.equals(selected));b.setTextSize(13);b.setPadding(0,dp(c,8),0,dp(c,8));}root.addView(row);return row;}
    void styleTree(View view){
        if(view instanceof Button){Button b=(Button)view;b.setAllCaps(false);b.setTextColor(text);b.setTextSize(14);b.setStateListAnimator(null);b.setBackground(touch(soft,15));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setMinWidth(0);b.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));if(b.getTag() instanceof Integer){int rgb=(Integer)b.getTag();b.setBackground(touch(0xff000000|rgb,15));b.setTextColor(((rgb>>16&255)*299+(rgb>>8&255)*587+(rgb&255)*114)>128000?Color.BLACK:Color.WHITE);}}
        else if(view instanceof EditText){((EditText)view).setTextColor(text);((EditText)view).setHintTextColor(muted);}
        else if(view instanceof TextView)((TextView)view).setTextColor(text);
        if(view instanceof SeekBar){SeekBar b=(SeekBar)view;b.setProgressTintList(ColorStateList.valueOf(accent));b.setThumbTintList(ColorStateList.valueOf(accent));b.setMinimumHeight(dp(c,48));}
        if(view instanceof android.widget.Switch){((android.widget.Switch)view).setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)styleTree(((ViewGroup)view).getChildAt(i));
    }
    static final class Mark extends Drawable{
        final Paint p=new Paint(3);final String kind;final int tint;
        Mark(String kind,int tint){this.kind=kind;this.tint=tint;}
        public void draw(Canvas canvas){canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/48f,b.height()/48f);p.setColor(tint);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(kind){
            case "work":canvas.drawLine(11,39,37,39,p);canvas.drawLine(25,38,25,24,p);canvas.drawLine(25,24,16,15,p);canvas.drawLine(16,15,28,8,p);path(canvas,29,7,39,18,23,25,20,20);canvas.drawLine(31,28,31,32,p);canvas.drawLine(38,24,41,28,p);break;
            case "wind_down":canvas.drawLine(6,33,42,33,p);canvas.drawArc(14,20,34,40,180,180,false,p);canvas.drawLine(24,10,24,15,p);canvas.drawLine(8,19,12,22,p);canvas.drawLine(36,22,40,19,p);canvas.drawLine(13,40,35,40,p);break;
            case "bed":canvas.drawLine(7,25,7,40,p);canvas.drawLine(7,34,41,34,p);canvas.drawLine(41,29,41,40,p);canvas.drawRoundRect(8,24,40,34,3,3,p);canvas.drawRoundRect(11,21,21,27,2,2,p);p.setStyle(Paint.Style.FILL);Path moon=new Path();moon.moveTo(32,5);moon.cubicTo(20,8,25,23,36,20);moon.cubicTo(30,20,27,13,32,5);canvas.drawPath(moon,p);break;
            case "power":canvas.drawArc(9,10,39,40,-55,290,false,p);canvas.drawLine(24,5,24,23,p);break;
            case "back":canvas.drawLine(29,10,15,24,p);canvas.drawLine(15,24,29,38,p);break;
            case "edit":path(canvas,11,31,30,12,36,18,17,37,9,39,11,31);canvas.drawLine(27,15,33,21,p);break;
            case "settings":canvas.drawCircle(24,24,13,p);canvas.drawCircle(24,24,4,p);for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,24,24);canvas.drawLine(24,5,24,9,p);canvas.restore();}break;
            case "refresh":canvas.drawArc(9,9,39,39,50,290,false,p);canvas.drawLine(38,9,38,20,p);canvas.drawLine(38,20,27,18,p);break;
            case "vont":canvas.drawRoundRect(10,8,18,39,4,4,p);canvas.drawRoundRect(30,8,38,39,4,4,p);canvas.drawLine(24,14,24,32,p);break;
            case "screenbar":canvas.drawRoundRect(6,9,42,15,3,3,p);canvas.drawLine(11,21,11,34,p);canvas.drawLine(24,21,24,39,p);canvas.drawLine(37,21,37,34,p);break;
            default:canvas.drawCircle(24,24,15,p);canvas.drawLine(24,17,24,25,p);canvas.drawPoint(24,32,p);
            }canvas.restore();
        }
        void path(Canvas c,float... v){Path path=new Path();path.moveTo(v[0],v[1]);for(int i=2;i<v.length;i+=2)path.lineTo(v[i],v[i+1]);c.drawPath(path,p);}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
