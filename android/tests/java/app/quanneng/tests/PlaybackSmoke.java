package app.quanneng.tests;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** 在真实 Android 媒体栈验证 HTTPS 播放、进度推进、循环及 content:// 文件。 */
public final class PlaybackSmoke extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        PrivateTestAccess.install(this);Bundle result=new Bundle();
        try{
            Activity hls=open(Intent.ACTION_SEND,Uri.parse("https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8"));awaitPlayable(hls);report("GSY + Media3 HLS video decoded and position advanced");runOnMainSync(hls::finish);SystemClock.sleep(300);
            Activity dash=open(Intent.ACTION_SEND,Uri.parse("https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"));awaitPlayable(dash);report("GSY + Media3 DASH video decoded and position advanced");runOnMainSync(dash::finish);SystemClock.sleep(300);
            Activity network=open(Intent.ACTION_SEND,Uri.parse("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"));
            awaitPlayable(network);report("HTTPS video decoded and position advanced");
            exerciseLoop(network);report("Native AB interval repeated successfully");
            exerciseFullscreen(network);report("Fullscreen, preserved pause, control visibility and system back verified");
            runOnMainSync(network::finish);SystemClock.sleep(500);
            Activity local=open(Intent.ACTION_VIEW,Uri.parse("content://app.quanneng.tests.fixture/seek-fixture.mp4"));
            awaitPlayable(local);report("Local content URI decoded and position advanced");exerciseJumps(local);report("All six fullscreen jumps, portrait action, boundaries and repeated taps verified");runOnMainSync(local::finish);
            result.putString("stream","QUANNENG_SMOKE_OK\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","QUANNENG_SMOKE_FAILED: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private Activity open(String action,Uri uri)throws Exception{
        ActivityMonitor monitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);
        Intent intent=new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(Intent.ACTION_SEND.equals(action))intent.setType("text/plain").putExtra(Intent.EXTRA_TEXT,uri.toString());else intent.setDataAndType(uri,"video/mp4");
        runOnMainSync(()->getTargetContext().startActivity(intent));Activity player=waitForMonitorWithTimeout(monitor,45000);removeMonitor(monitor);
        if(player==null)throw new AssertionError("Main app did not route media into native player");PrivateTestAccess.awaitUnlocked(this,player);return player;
    }
    private long[] state(Activity activity){AtomicReference<long[]> result=new AtomicReference<>();runOnMainSync(()->{PlaybackView video=new PlaybackView(activity.findViewById(1000));try{Object mp=field(activity,"media");result.set(new long[]{video.getDuration(),video.getCurrentPosition(),mp==null?0:((Number)mp.getClass().getMethod("getVideoWidth").invoke(mp)).longValue(),mp==null?0:((Number)mp.getClass().getMethod("getVideoHeight").invoke(mp)).longValue()});}catch(Exception error){result.set(new long[]{0,0,0,0});}});return result.get();}
    private void awaitPlayable(Activity activity)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(SystemClock.elapsedRealtime()<deadline){long[] s=state(activity);if(s[0]>1000&&s[1]>150&&s[2]>0&&s[3]>0)return;SystemClock.sleep(150);}
        throw new AssertionError("Video never produced dimensions and advancing playback: "+java.util.Arrays.toString(state(activity)));
    }
    private void exerciseLoop(Activity activity)throws Exception{
        runOnMainSync(()->{PlaybackView v=new PlaybackView(activity.findViewById(1000));v.seekTo(300);v.start();});SystemClock.sleep(450);
        runOnMainSync(()->activity.findViewById(1001).performClick());SystemClock.sleep(1100);
        runOnMainSync(()->activity.findViewById(1002).performClick());runOnMainSync(()->activity.findViewById(1003).performClick());SystemClock.sleep(2500);
        AtomicReference<Boolean> good=new AtomicReference<>(false);
        runOnMainSync(()->{try{long a=(Long)field(activity,"pointA"),b=(Long)field(activity,"pointB");boolean enabled=(Boolean)field(activity,"looping");PlaybackView v=new PlaybackView(activity.findViewById(1000));int p=v.getCurrentPosition();good.set(enabled&&b>a&&v.isPlaying()&&p>=a-250&&p<=b+350);}catch(Exception error){throw new RuntimeException(error);}});
        if(!good.get())throw new AssertionError("AB loop did not keep playback inside selected range");
    }
    private void exerciseFullscreen(Activity activity)throws Exception{
        runOnMainSync(()->activity.findViewById(1004).performClick());SystemClock.sleep(250);
        long paused=state(activity)[1];SystemClock.sleep(300);if(Math.abs(state(activity)[1]-paused)>100)throw new AssertionError("Pause button did not pause");
        snapshot("PORTRAIT");runOnMainSync(()->activity.findViewById(1005).performClick());awaitLayout(activity,true);
        AtomicReference<Boolean> pausedStill=new AtomicReference<>(false);runOnMainSync(()->{PlaybackView v=new PlaybackView(activity.findViewById(1000));pausedStill.set(!v.isPlaying());});
        if(!pausedStill.get()||Math.abs(state(activity)[1]-paused)>500)throw new AssertionError("Fullscreen lost paused playback position");
        SystemClock.sleep(1200);waitForIdleSync();snapshot("LANDSCAPE");report("Fullscreen layout and pause position preserved");runOnMainSync(()->activity.findViewById(1004).performClick());SystemClock.sleep(4500);
        AtomicReference<Boolean> hidden=new AtomicReference<>(false);runOnMainSync(()->hidden.set(activity.findViewById(1006).getVisibility()==View.GONE));if(!hidden.get())throw new AssertionError("Fullscreen controls did not auto-hide");
        report("Fullscreen controls auto-hidden");tapVideo(activity);AtomicReference<Boolean> visible=new AtomicReference<>(false);runOnMainSync(()->visible.set(activity.findViewById(1006).getVisibility()==View.VISIBLE));if(!visible.get()){snapshot("AFTER_TAP");throw new AssertionError("Video tap did not restore controls");}
        report("Actual touchscreen restored controls");long backTime=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new KeyEvent(backTime,backTime,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true);getUiAutomation().injectInputEvent(new KeyEvent(backTime,backTime+50,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true);awaitLayout(activity,false);if(activity.isFinishing())throw new AssertionError("System back closed player instead of exiting fullscreen");
        awaitPlayable(activity);
    }
    private void exerciseJumps(Activity activity)throws Exception{
        if(state(activity)[0]<1299000)throw new AssertionError("Long seek fixture missing");
        setPosition(activity,650000);runOnMainSync(()->{View b=activity.findViewById(1104);b.requestRectangleOnScreen(new android.graphics.Rect(0,0,b.getWidth(),b.getHeight()),true);});tapControl(activity,1104);awaitPosition(activity,710000);
        runOnMainSync(()->activity.findViewById(1005).performClick());awaitLayout(activity,true);SystemClock.sleep(700);
        int[] deltas={-600,-60,-10,10,60,600};for(int i=0;i<deltas.length;i++){
            setPosition(activity,650000);tapControl(activity,1200+i);AtomicReference<Boolean> feedback=new AtomicReference<>(false);runOnMainSync(()->feedback.set(activity.findViewById(1300).getVisibility()==View.VISIBLE));if(!feedback.get())throw new AssertionError("Jump feedback missing");awaitPosition(activity,650000+deltas[i]*1000);
            if(i==2)snapshot("JUMP_CONTROLS");
        }
        setPosition(activity,500000);tapControl(activity,1203);tapControl(activity,1203);tapControl(activity,1203);awaitPosition(activity,530000);
        setPosition(activity,50000);tapControl(activity,1200);awaitPosition(activity,0);
        setPosition(activity,1250000);tapControl(activity,1205);awaitPosition(activity,1300000);
        runOnMainSync(()->activity.findViewById(1005).performClick());awaitLayout(activity,false);
    }
    private void setPosition(Activity activity,int position)throws Exception{runOnMainSync(()->{PlaybackView v=new PlaybackView(activity.findViewById(1000));v.pause();v.seekTo(position);});awaitPosition(activity,position);SystemClock.sleep(500);}
    private void awaitPosition(Activity activity,int target)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+8000;
        while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> good=new AtomicReference<>(false);runOnMainSync(()->{PlaybackView v=new PlaybackView(activity.findViewById(1000));good.set(!v.isPlaying()&&Math.abs(v.getCurrentPosition()-target)<=1200);});if(good.get())return;SystemClock.sleep(100);}
        throw new AssertionError("Paused seek did not reach "+target+": "+java.util.Arrays.toString(state(activity)));
    }
    private void tapVideo(Activity activity)throws Exception{tapControl(activity,1010);}
    private void tapControl(Activity activity,int id)throws Exception{
        AtomicReference<int[]> point=new AtomicReference<>();runOnMainSync(()->{View target=activity.findViewById(id);if(id>=1100&&id<1300){android.graphics.Rect bounds=new android.graphics.Rect();if(!target.getGlobalVisibleRect(bounds)||bounds.width()<target.getWidth()||bounds.height()<target.getHeight())throw new AssertionError("Jump target clipped: "+id);float density=activity.getResources().getDisplayMetrics().density;if(target.getWidth()<47*density||target.getHeight()<47*density)throw new AssertionError("Jump target smaller than 48dp: "+id);}int[] xy=new int[2];target.getLocationOnScreen(xy);point.set(new int[]{xy[0]+target.getWidth()/2,xy[1]+target.getHeight()/2});});
        int[] p=point.get();report("Touchscreen target: "+p[0]+","+p[1]);
        // Android shell input uses the platform touchscreen source and display mapping.
        try(ParcelFileDescriptor command=getUiAutomation().executeShellCommand("input tap "+p[0]+" "+p[1]);java.io.FileInputStream output=new java.io.FileInputStream(command.getFileDescriptor())){byte[] buffer=new byte[256];while(output.read(buffer)!=-1){}}
        SystemClock.sleep(200);waitForIdleSync();
    }
    private void awaitLayout(Activity activity,boolean full)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> ok=new AtomicReference<>(false);runOnMainSync(()->{try{View root=activity.getWindow().getDecorView(),button=activity.findViewById(1005),details=activity.findViewById(1008);boolean actual=(Boolean)field(activity,"fullscreen");int[] xy=new int[2];button.getLocationOnScreen(xy);float density=activity.getResources().getDisplayMetrics().density;ok.set(root.hasWindowFocus()&&actual==full&&(root.getWidth()>root.getHeight())==full&&details.getVisibility()==(full?View.GONE:View.VISIBLE)&&button.getWidth()>=47*density&&button.getHeight()>=47*density&&xy[0]>=0&&xy[0]+button.getWidth()<=root.getWidth());}catch(Exception e){}});if(ok.get())return;SystemClock.sleep(100);}
        throw new AssertionError("Player orientation, full-width layout, or 48dp fullscreen target incorrect: "+full);
    }
    private void snapshot(String label)throws Exception{
        Bitmap original=getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("Cannot capture player UI");
        int width=Math.min(600,original.getWidth());Bitmap scaled=Bitmap.createScaledBitmap(original,width,original.getHeight()*width/original.getWidth(),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,85,bytes);report("QUANNENG_UI_"+label+":"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));if(scaled!=original)scaled.recycle();original.recycle();
    }
    private static final class PlaybackView {
        private final View view;
        PlaybackView(View value){view=value;}
        Object call(String method,Class<?>[] types,Object... args){try{return view.getClass().getMethod(method,types).invoke(view,args);}catch(Exception e){throw new RuntimeException(e);}}
        int getDuration(){return ((Number)call("getDuration",new Class<?>[0])).intValue();}
        int getCurrentPosition(){return ((Number)call("getCurrentPosition",new Class<?>[0])).intValue();}
        boolean isPlaying(){return (Boolean)call("isPlaying",new Class<?>[0]);}
        void start(){call("start",new Class<?>[0]);}
        void pause(){call("pause",new Class<?>[0]);}
        void seekTo(int position){call("seekTo",new Class<?>[]{int.class},position);}
    }
    private Object field(Activity activity,String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void report(String text){Bundle data=new Bundle();data.putString("stream",text+"\n");sendStatus(0,data);}
}
