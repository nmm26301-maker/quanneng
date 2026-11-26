package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.*;

/** Regression on a decoded local video: actual controls, focal pinch, pan and orientation. */
public final class PlayerDisplaySmoke extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);
    }
    private void ui(Runnable work){runOnMainSync(work);waitForIdleSync();}
    private void report(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    private void orientation(Activity a,int value)throws Exception{
        long end=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<end){if(a.getResources().getConfiguration().orientation==value){waitForIdleSync();return;}SystemClock.sleep(100);}
        throw new AssertionError("Orientation did not reach "+value);
    }
    private void screenshot(Activity a,String name)throws Exception{
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    }
    @Override public void onStart(){
        PrivateTestAccess.install(this);Bundle result=new Bundle();Activity player=null;
        try{
            ActivityMonitor monitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);
            Intent intent=new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.MainActivity")).setAction(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://app.quanneng.next.tests.fixture/seek-fixture.mp4"),"video/mp4").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getTargetContext().startActivity(intent);player=waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);check(player!=null,"Player missing");PrivateTestAccess.awaitUnlocked(this,player);
            final Activity a=player;
            long end=SystemClock.elapsedRealtime()+20000;
            while(SystemClock.elapsedRealtime()<end){AtomicBoolean ready=new AtomicBoolean();ui(()->{try{ready.set((Boolean)PrivateTestAccess.field(a,"ready")&&(Integer)call(a.findViewById(1000),"getCurrentPosition",new Class<?>[]{})>300);}catch(Exception ignored){}});if(ready.get())break;SystemClock.sleep(100);}
            ui(()->{try{check((Boolean)PrivateTestAccess.field(a,"ready"),"Video not decoded");call(a.findViewById(1000),"pause",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});
            ui(()->{check(a.findViewById(1316).performClick(),"Zoom detail entry missing");});
            ui(()->{try{check((Float)call(a.findViewById(1000),"getZoom",new Class<?>[]{})==1.5f,"Zoom button failed");}catch(Exception e){throw new RuntimeException(e);}});
            verifyPinch(a);
            final long epoch=(Long)PrivateTestAccess.field(a,"mediaEpoch");
            ui(()->{try{call(a.findViewById(1000),"setZoom",new Class<?>[]{float.class},2f);}catch(Exception e){throw new RuntimeException(e);}a.findViewById(1005).performClick();});
            orientation(a,Configuration.ORIENTATION_LANDSCAPE);screenshot(a,"player-landscape-zoom");
            verifyLandscapeTouchLock(a);
            ui(()->{check(a.findViewById(1314).isShown(),"Orientation entry hidden in full screen");check(a.findViewById(1315).isShown(),"Zoom entry hidden in full screen");check(a.findViewById(1005).isShown(),"Exit full screen entry hidden");a.findViewById(1314).performClick();});
            orientation(a,Configuration.ORIENTATION_PORTRAIT);screenshot(a,"player-portrait-zoom");
            ui(()->{try{check(a.getRequestedOrientation()==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,"Portrait mode not explicit");check((Float)call(a.findViewById(1000),"getZoom",new Class<?>[]{})==2f,"Rotation lost zoom");check((Long)PrivateTestAccess.field(a,"mediaEpoch")==epoch,"Rotation reloaded video");check(!(Boolean)call(a.findViewById(1000),"isPlaying",new Class<?>[]{}),"Rotation resumed paused video");check(!a.findViewById(2401).isShown(),"Landscape side zones cover portrait controls");a.findViewById(1314).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            orientation(a,Configuration.ORIENTATION_LANDSCAPE);
            ui(()->{a.findViewById(1314).performClick();});orientation(a,Configuration.ORIENTATION_PORTRAIT);
            ui(()->{a.findViewById(1005).performClick();});
            ui(()->{try{check(!(Boolean)PrivateTestAccess.field(a,"fullscreen"),"Exit failed");a.findViewById(1005).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            orientation(a,Configuration.ORIENTATION_PORTRAIT);
            ui(()->{for(int i=0;i<3;i++)a.findViewById(1315).performClick();});
            ui(()->{try{check((Float)call(a.findViewById(1000),"getZoom",new Class<?>[]{})==1f,"Zoom cycle must return to 1x");}catch(Exception e){throw new RuntimeException(e);}});
            screenshot(a,"player-portrait-fit");
            result.putString("stream","QUANNENG_PLAYER_DISPLAY_OK\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){e.printStackTrace();result.putString("stream","QUANNENG_PLAYER_DISPLAY_FAILED: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(player!=null){final Activity a=player;ui(a::finish);}}
    }
    private void verifyLandscapeTouchLock(Activity player)throws Exception{
        ui(()->{
            View left=player.findViewById(2401),right=player.findViewById(2411);
            check(left.isShown()&&right.isShown()&&left.getAlpha()==0f&&right.getAlpha()==0f,"Landscape side targets must be invisible and available");
            View panel=player.findViewById(1006),progress=player.findViewById(1007);
            View speed=(View)readField(player,"speedButton");
            check(screenCenter(progress)[1]<screenCenter(speed)[1],"Speed belongs below the progress bar");
            check(screenCenter(progress)[1]>panel.getRootView().getHeight()*0.7f,"Progress belongs near the bottom");
            check(((android.graphics.drawable.LayerDrawable)((SeekBar)progress).getProgressDrawable()).findDrawableByLayerId(android.R.id.secondaryProgress)!=null,"Buffered track missing");
            player.findViewById(1310).performClick();
            check(!player.findViewById(1312).isShown()&&!panel.isShown(),"Lock displayed the controls before a double tap");
        });
        ui(()->{
            check(screenCenter(player.findViewById(2400))[1]<screenCenter(player.findViewById(2401))[1],"Left top target must sit above the center rail");
            check(screenCenter(player.findViewById(2410))[1]<screenCenter(player.findViewById(2411))[1],"Right top target must sit above the center rail");
            check(screenCenter(player.findViewById(2401))[1]<screenCenter(player.findViewById(2402))[1],"Left jump order changed");
            check(screenCenter(player.findViewById(2411))[1]<screenCenter(player.findViewById(2412))[1],"Right jump order changed");
            try{call(player.findViewById(1000),"seekTo",new Class<?>[]{int.class},650000);}catch(Exception e){throw new RuntimeException(e);}
        });
        awaitPosition(player,650000);
        for(int[] jump:new int[][]{{2400,640000},{2410,650000},{2402,590000},{2412,650000}}){
            int[] target=point(player,jump[0]);
            report("Locked tap at "+target[0]+","+target[1]+" for view "+jump[0]);
            shellInput("tap "+target[0]+" "+target[1]);
            awaitPosition(player,jump[1]);
        }
        ui(()->check(!player.findViewById(1312).isShown(),"A single tap revealed the lock"));
        int[] side=point(player,2411);
        tap(side);SystemClock.sleep(80);tap(side);
        SystemClock.sleep(350);
        ui(()->check(player.findViewById(1312).isShown(),"One double tap on a side target must reveal the lock"));
        awaitPosition(player,650000);
        int[] unlock=point(player,1312);long down=SystemClock.uptimeMillis();
        inject(down,MotionEvent.ACTION_DOWN,unlock);SystemClock.sleep(850);inject(down,MotionEvent.ACTION_UP,unlock);
        ui(()->check(!player.findViewById(1312).isShown()&&player.findViewById(1006).isShown(),"Long press must unlock and restore controls"));
        report("Invisible landscape rails and top endpoints seek; double tap takes priority over seeking; long press unlocks; buffered line and speed placement verified");
    }
    private static Object readField(Activity activity,String name){
        try{return PrivateTestAccess.field(activity,name);}catch(Exception e){throw new RuntimeException(e);}
    }
    private static int[] screenCenter(View view){
        int[] point=new int[2];view.getLocationOnScreen(point);point[0]+=view.getWidth()/2;point[1]+=view.getHeight()/2;return point;
    }
    private int[] point(Activity player,int id){AtomicReference<int[]> point=new AtomicReference<>();ui(()->point.set(screenCenter(player.findViewById(id))));return point.get();}
    private void shellInput(String command)throws java.io.IOException{
        try(ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand("input "+command);
            java.io.FileInputStream output=new java.io.FileInputStream(descriptor.getFileDescriptor())){
            byte[] buffer=new byte[256];while(output.read(buffer)!=-1){}
        }
    }
    private void tap(int[] point){long down=SystemClock.uptimeMillis();inject(down,MotionEvent.ACTION_DOWN,point);SystemClock.sleep(45);inject(down,MotionEvent.ACTION_UP,point);}
    private void inject(long down,int action,int[] point){
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point[0],point[1],0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{check(getUiAutomation().injectInputEvent(event,true),"Android rejected touchscreen input");}finally{event.recycle();}
    }
    private void awaitPosition(Activity player,int expected){
        long end=SystemClock.elapsedRealtime()+6000;
        AtomicInteger actual=new AtomicInteger(-1);
        while(SystemClock.elapsedRealtime()<end){
            ui(()->{try{actual.set((Integer)call(player.findViewById(1000),"getCurrentPosition",new Class<?>[]{}));}catch(Exception e){throw new RuntimeException(e);}});
            if(Math.abs(actual.get()-expected)<1200)return;
            SystemClock.sleep(80);
        }
        throw new AssertionError("Expected position "+expected+" but reached "+actual);
    }
    private void verifyPinch(Activity p)throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{
            android.view.View touch=p.findViewById(1010),video=p.findViewById(1000);check((Boolean)PrivateTestAccess.field(p,"ready"),"Player not ready for pinch");check(touch.getWidth()>0&&touch.getHeight()>0,"Gesture layer has no size");float cx=touch.getWidth()/2f,cy=touch.getHeight()/2f;long now=android.os.SystemClock.uptimeMillis();
            dispatch(touch,now,android.view.MotionEvent.ACTION_DOWN,cx-30,cy);
            dispatchPair(touch,now,android.view.MotionEvent.ACTION_POINTER_DOWN|1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT,cx-30,cy,cx+30,cy);
            dispatchPair(touch,now,android.view.MotionEvent.ACTION_MOVE,cx-100,cy,cx+100,cy);
            dispatchPair(touch,now,android.view.MotionEvent.ACTION_MOVE,cx-130,cy,cx+130,cy);
            dispatchPair(touch,now,android.view.MotionEvent.ACTION_POINTER_UP|1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT,cx-130,cy,cx+130,cy);
            dispatch(touch,now,android.view.MotionEvent.ACTION_UP,cx-130,cy);
            float enlarged=(Float)call(video,"getZoom",new Class<?>[]{});check(enlarged>1.5f,"Two-finger pinch did not enlarge the video: "+enlarged);
            dispatch(touch,now+200,android.view.MotionEvent.ACTION_DOWN,cx,cy);dispatch(touch,now+200,android.view.MotionEvent.ACTION_MOVE,cx+85,cy);dispatch(touch,now+200,android.view.MotionEvent.ACTION_UP,cx+85,cy);
            android.view.SurfaceView surface=(android.view.SurfaceView)PrivateTestAccess.field(video,"surface");check(Math.abs(surface.getTranslationX())>1,"Enlarged picture did not pan under a finger");
            dispatch(touch,now+400,android.view.MotionEvent.ACTION_DOWN,cx-130,cy);
            dispatchPair(touch,now+400,android.view.MotionEvent.ACTION_POINTER_DOWN|1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT,cx-130,cy,cx+130,cy);
            dispatchPair(touch,now+400,android.view.MotionEvent.ACTION_MOVE,cx-30,cy,cx+30,cy);
            dispatchPair(touch,now+400,android.view.MotionEvent.ACTION_POINTER_UP|1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT,cx-30,cy,cx+30,cy);
            dispatch(touch,now+400,android.view.MotionEvent.ACTION_UP,cx-30,cy);
            check((Float)call(video,"getZoom",new Class<?>[]{})<enlarged,"Reverse pinch did not shrink the video");
        }catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError("Pinch/pan gesture failed",failure.get());report("Two-finger focal zoom, one-finger picture pan and reverse pinch verified on decoded video");
    }
    private static int syntheticGestureStep;
    private static void dispatch(android.view.View view,long down,int action,float x,float y){android.view.MotionEvent event=android.view.MotionEvent.obtain(down,down+50+40*syntheticGestureStep++,action,x,y,0);check(view.dispatchTouchEvent(event),"Gesture layer rejected single touch");event.recycle();}
    private static void dispatchPair(android.view.View view,long down,int action,float x1,float y1,float x2,float y2){android.view.MotionEvent.PointerProperties[] ids={new android.view.MotionEvent.PointerProperties(),new android.view.MotionEvent.PointerProperties()};ids[0].id=0;ids[1].id=1;ids[0].toolType=ids[1].toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;android.view.MotionEvent.PointerCoords[] pos={new android.view.MotionEvent.PointerCoords(),new android.view.MotionEvent.PointerCoords()};pos[0].x=x1;pos[0].y=y1;pos[1].x=x2;pos[1].y=y2;pos[0].pressure=pos[1].pressure=1;pos[0].size=pos[1].size=1;android.view.MotionEvent event=android.view.MotionEvent.obtain(down,down+50+40*syntheticGestureStep++,action,2,ids,pos,0,0,1,1,0,0,0,0);check(view.dispatchTouchEvent(event),"Gesture layer rejected two fingers");event.recycle();}
}
