package app.quanneng;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import android.widget.SeekBar;

/** Relative, whole-second dragging; a tap retains the ordinary full-timeline shortcut. */
final class PrecisionSeekBar extends SeekBar {
    private OnSeekBarChangeListener listener;
    private float downX,downY,lastX,seconds;
    private int original;
    private boolean dragging,fine,cancelled;
    PrecisionSeekBar(Context c){super(c);setSplitTrack(false);}
    @Override public void setOnSeekBarChangeListener(OnSeekBarChangeListener value){listener=value;super.setOnSeekBarChangeListener(value);}
    boolean fine(){return fine;}
    boolean cancelled(){return cancelled;}
    @Override public boolean onTouchEvent(MotionEvent e){
        AccessibilityManager access=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(access!=null&&access.isTouchExplorationEnabled())return super.onTouchEvent(e);
        if(!isEnabled())return false;
        int action=e.getActionMasked();float density=getResources().getDisplayMetrics().density;
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=e.getX();downY=e.getY();original=getProgress();seconds=original;dragging=false;fine=false;cancelled=false;setPressed(true);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);if(listener!=null)listener.onStartTrackingTouch(this);return true;}
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()!=1){cancel();return true;}
            if(!isPressed())return true;
            fine=downY-e.getY()>32*density;
            if(!dragging&&Math.abs(e.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragging=true;
            if(dragging){float direction=getLayoutDirection()==LAYOUT_DIRECTION_RTL?-1:1;seconds=Math.max(0,Math.min(getMax(),seconds+direction*(e.getX()-lastX)/(density*(fine?18f:6f))));preview(Math.round(seconds));lastX=e.getX();}return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){cancel();return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN){cancel();return true;}
        if(action==MotionEvent.ACTION_UP){if(!isPressed())return true;if(!dragging){float f=(e.getX()-getPaddingLeft())/Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());if(getLayoutDirection()==LAYOUT_DIRECTION_RTL)f=1-f;preview(Math.max(0,Math.min(getMax(),Math.round(f*getMax()))));performClick();}finish();return true;}
        return true;
    }
    private void preview(int second){setProgress(second);if(listener!=null)listener.onProgressChanged(this,second,true);}
    private void cancel(){if(!isPressed())return;cancelled=true;setProgress(original);finish();}
    private void finish(){setPressed(false);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(listener!=null)listener.onStopTrackingTouch(this);}
    @Override public boolean performClick(){return super.performClick();}
}
