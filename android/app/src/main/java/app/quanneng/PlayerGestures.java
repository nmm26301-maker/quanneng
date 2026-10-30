package app.quanneng;

import android.app.Activity;
import android.media.AudioManager;
import android.view.*;
import android.view.accessibility.AccessibilityManager;

/** One finger controls playback; two fingers zoom around their midpoint. */
final class PlayerGestures implements View.OnTouchListener {
 interface Target {
  boolean ready();int position();int duration();void seek(int ms);void toggle();void tap();void feedback(String value);
  float speed();void speed(float value);float zoom();void zoomAt(float value,float x,float y);void pan(float dx,float dy);
 }
 private final Activity activity;private final Target target;private final GestureDetector taps;private final AudioManager audio;
 private float x,y,lastX,lastY,lastSpan,lastFocusX,lastFocusY,brightness,oldSpeed;private int pos,volume,axis,width,height;private boolean held,moved,multi;
 PlayerGestures(Activity a,Target t){
  activity=a;target=t;audio=(AudioManager)a.getSystemService(Activity.AUDIO_SERVICE);
  taps=new GestureDetector(a,new GestureDetector.SimpleOnGestureListener(){
   @Override public boolean onDown(MotionEvent e){return true;}
   @Override public boolean onSingleTapUp(MotionEvent e){if(multi)return false;target.tap();return true;}
   @Override public boolean onDoubleTap(MotionEvent e){if(multi)return false;if(target.ready()){float fraction=e.getX()/Math.max(1,width);if(fraction<.35f)target.seek(Math.max(0,target.position()-10000));else if(fraction>.65f)target.seek(Math.min(target.duration(),target.position()+10000));else target.toggle();}return true;}
   @Override public void onLongPress(MotionEvent e){if(target.ready()&&!moved&&!multi){held=true;oldSpeed=target.speed();target.speed(2);target.feedback("临时 2 倍速 · 松手恢复");}}
  });
 }
 private void releaseSpeed(){if(held){target.speed(oldSpeed);held=false;}}
 @Override public boolean onTouch(View v,MotionEvent e){
  AccessibilityManager access=(AccessibilityManager)activity.getSystemService(Activity.ACCESSIBILITY_SERVICE);
  boolean enabled=activity.getSharedPreferences("player-gestures",0).getBoolean("enabled",true)&&(access==null||!access.isTouchExplorationEnabled());
  if(!enabled)return false;
  width=v.getWidth();height=v.getHeight();int action=e.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN){x=lastX=e.getX();y=lastY=e.getY();pos=target.position();volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);brightness=activity.getWindow().getAttributes().screenBrightness;if(brightness<0)brightness=.5f;axis=0;moved=false;held=false;multi=false;}
  if(e.getPointerCount()>1||action==MotionEvent.ACTION_POINTER_DOWN){multi=true;axis=0;releaseSpeed();}
  if(e.getPointerCount()>=2){
   float dx=e.getX(1)-e.getX(0),dy=e.getY(1)-e.getY(0),span=(float)Math.hypot(dx,dy);
   float focusX=(e.getX(0)+e.getX(1))/2f,focusY=(e.getY(0)+e.getY(1))/2f;
   if(action==MotionEvent.ACTION_MOVE&&target.ready()&&lastSpan>0){
    target.zoomAt(target.zoom()*span/lastSpan,focusX,focusY);
    target.pan(focusX-lastFocusX,focusY-lastFocusY);
   }
   lastSpan=span;lastFocusX=focusX;lastFocusY=focusY;
  }else if(action==MotionEvent.ACTION_POINTER_UP){lastSpan=0;}
  if(!multi)taps.onTouchEvent(e);
  if(action==MotionEvent.ACTION_MOVE&&target.ready()&&!held&&!multi){
   float dx=e.getX()-x,dy=e.getY()-y;
   if(target.zoom()>1f){target.pan(e.getX()-lastX,e.getY()-lastY);moved=true;}
   else{
    if(axis==0&&Math.max(Math.abs(dx),Math.abs(dy))>24*activity.getResources().getDisplayMetrics().density){axis=Math.abs(dx)>Math.abs(dy)?1:2;moved=true;}
    if(axis==1){int at=(int)Math.max(0,Math.min(target.duration(),pos+Math.round(dx/(6*activity.getResources().getDisplayMetrics().density))*1000L));target.feedback("松手定位 · "+SubtitleStore.stamp(at));}
    else if(axis==2){if(x<width/2f){WindowManager.LayoutParams p=activity.getWindow().getAttributes();p.screenBrightness=Math.max(.03f,Math.min(1,brightness-dy/Math.max(1,height)));activity.getWindow().setAttributes(p);target.feedback("亮度 "+Math.round(p.screenBrightness*100)+"%");}else{int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);int next=Math.max(0,Math.min(max,Math.round(volume-dy/Math.max(1,height)*max)));try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);target.feedback("音量 "+Math.round(next*100f/Math.max(1,max))+"%");}catch(SecurityException ignored){target.feedback("请使用系统音量键调整");}}}
   }
   lastX=e.getX();lastY=e.getY();
  }
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){releaseSpeed();if(action==MotionEvent.ACTION_UP&&!multi&&axis==1&&target.ready())target.seek((int)Math.max(0,Math.min(target.duration(),pos+Math.round((e.getX()-x)/(6*activity.getResources().getDisplayMetrics().density))*1000L)));if(multi&&target.ready())target.feedback("画面缩放 "+String.format(java.util.Locale.ROOT,"%.1f×",target.zoom()));axis=0;lastSpan=0;}
  return true;
 }
}
