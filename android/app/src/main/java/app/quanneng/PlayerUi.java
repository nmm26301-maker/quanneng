package app.quanneng;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.*;
import android.animation.ValueAnimator;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;

/** 原生播放器的颜色、触摸反馈和矢量图标；无额外运行时依赖。 */
final class PlayerUi {
    static final int BG=Color.rgb(8,12,16),SURFACE=Color.rgb(18,25,29),BORDER=Color.rgb(41,53,57),TEXT=Color.rgb(242,246,240),MUTED=Color.rgb(169,183,184),ACCENT=Color.rgb(184,237,150);
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static Drawable feedback(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF),surface(c,color,radius),surface(c,Color.WHITE,radius));}
    static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");return t;}
    static Button chip(Context c,String value){Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setPadding(dp(c,8),0,dp(c,8),0);b.setBackground(feedback(c,SURFACE,12));return b;}
    static ImageButton icon(Context c,String name,String label,boolean primary){ImageButton b=new ImageButton(c);b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setMinimumWidth(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,13),dp(c,13),dp(c,13),dp(c,13));b.setBackground(feedback(c,primary?ACCENT:0x00212B2E,primary?28:14));setIcon(b,name,label,primary);return b;}
    static void setIcon(ImageButton b,String name,String label,boolean primary){b.setImageDrawable(new Glyph(name,primary?BG:TEXT));b.setContentDescription(label);b.setTooltipText(label);}
    static boolean motionEnabled(){return ValueAnimator.areAnimatorsEnabled();}
    static Button jumpButton(Context c,int seconds,boolean compact){
        Button b=chip(c,"");int amount=Math.abs(seconds)>=60?Math.abs(seconds)/60:Math.abs(seconds);String number=String.valueOf(amount),unit=Math.abs(seconds)>=60?(compact?"分":"分钟"):"秒";
        SpannableString label=new SpannableString(number+(compact?"":"\n")+unit);label.setSpan(new RelativeSizeSpan(compact?1.14f:1.3f),0,number.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);label.setSpan(new StyleSpan(Typeface.BOLD),0,number.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);label.setSpan(new RelativeSizeSpan(.86f),label.length()-unit.length(),label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(label);b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);b.setMaxLines(compact?1:2);b.setPadding(dp(c,8),0,dp(c,8),0);b.setStateListAnimator(null);b.setElevation(0);
        StateListDrawable states=new StateListDrawable();GradientDrawable active=surface(c,0xFF24382C,14);active.setStroke(dp(c,1),ACCENT);states.addState(new int[]{android.R.attr.state_pressed},active);GradientDrawable focus=surface(c,0xFF24382C,14);focus.setStroke(dp(c,2),ACCENT);states.addState(new int[]{android.R.attr.state_focused},focus);
        GradientDrawable base=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF1A272B,0xFF152024});base.setCornerRadius(dp(c,14));base.setStroke(dp(c,1),0xFF3B4D4E);states.addState(new int[]{},base);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40B8ED96),states,surface(c,Color.WHITE,14)));
        Glyph arrow=new Glyph("jump_"+(seconds<0?"back":"forward")+(Math.abs(seconds)==600?"_double":""),seconds<0?MUTED:ACCENT);arrow.setBounds(0,0,dp(c,compact?16:18),dp(c,compact?16:18));b.setCompoundDrawablesRelative(arrow,null,null,null);b.setCompoundDrawablePadding(dp(c,4));String direction=seconds<0?"后退":"前进";b.setContentDescription(direction+amount+(Math.abs(seconds)>=60?"分钟":"秒"));b.setTooltipText(b.getContentDescription());return b;
    }
    static boolean pressMotion(View view,MotionEvent event){
        int action=event.getActionMasked();if(action!=MotionEvent.ACTION_DOWN&&action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL)return false;
        view.animate().cancel();boolean down=action==MotionEvent.ACTION_DOWN;
        if(motionEnabled())view.animate().scaleX(down?.96f:1).scaleY(down?.96f:1).translationY(down?dp(view.getContext(),1):0).setDuration(down?90:160).setInterpolator(new DecelerateInterpolator()).start();
        else{view.setScaleX(1);view.setScaleY(1);view.setTranslationY(0);}return false;
    }
    private static final class Glyph extends Drawable {
        private final String name;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(String value,int color){name=value;p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);p.setStyle(Paint.Style.STROKE);Path path=new Path();
            if(name.equals("play")){p.setStyle(Paint.Style.FILL);path.moveTo(8,4);path.lineTo(20,12);path.lineTo(8,20);path.close();canvas.drawPath(path,p);}
            else if(name.equals("pause")){p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(6,4,10,20,1,1,p);canvas.drawRoundRect(14,4,18,20,1,1,p);}
            else if(name.equals("back")){path.moveTo(14,5);path.lineTo(7,12);path.lineTo(14,19);canvas.drawPath(path,p);canvas.drawLine(7,12,21,12,p);}
            else if(name.equals("fullscreen")||name.equals("collapse")){boolean collapse=name.equals("collapse");for(int i=0;i<4;i++){canvas.save();canvas.rotate(i*90,12,12);path.reset();if(collapse){path.moveTo(3,8);path.lineTo(8,8);path.lineTo(8,3);}else{path.moveTo(3,8);path.lineTo(3,3);path.lineTo(8,3);}canvas.drawPath(path,p);canvas.restore();}}
            else if(name.equals("rewind")||name.equals("forward")){boolean forward=name.equals("forward");if(forward){canvas.translate(24,0);canvas.scale(-1,1);}canvas.drawArc(4,4,21,21,-140,290,false,p);path.moveTo(3,3);path.lineTo(3,10);path.lineTo(10,10);canvas.drawPath(path,p);if(forward){canvas.scale(-1,1);canvas.translate(-24,0);}p.setStyle(Paint.Style.FILL);p.setTextSize(9);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);canvas.drawText("10",12,16,p);}
            else if(name.startsWith("jump_")){boolean back=name.contains("back");if(!back){canvas.translate(24,0);canvas.scale(-1,1);}int count=name.endsWith("double")?2:1;for(int i=0;i<count;i++){float x=count==1?15:12+i*7;path.reset();path.moveTo(x,5);path.lineTo(x-7,12);path.lineTo(x,19);canvas.drawPath(path,p);}}
            else {canvas.drawRoundRect(3,5,21,19,3,3,p);canvas.drawLine(6,11,10,11,p);canvas.drawLine(14,11,18,11,p);canvas.drawLine(6,15,18,15,p);}
            canvas.restore();
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
    }
}
