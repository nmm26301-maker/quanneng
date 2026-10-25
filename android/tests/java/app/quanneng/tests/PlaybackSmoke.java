package app.quanneng.tests;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.VideoView;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** 在真实 Android 媒体栈验证 HTTPS 播放、进度推进、循环及 content:// 文件。 */
public final class PlaybackSmoke extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Activity network=open(Intent.ACTION_SEND,Uri.parse("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"));
            awaitPlayable(network);report("HTTPS video decoded and position advanced");
            exerciseLoop(network);report("Native AB interval repeated successfully");
            runOnMainSync(network::finish);SystemClock.sleep(500);
            Activity local=open(Intent.ACTION_VIEW,Uri.parse("content://app.quanneng.tests.fixture/flower.mp4"));
            awaitPlayable(local);report("Local content URI decoded and position advanced");runOnMainSync(local::finish);
            result.putString("stream","QUANNENG_SMOKE_OK\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","QUANNENG_SMOKE_FAILED: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private Activity open(String action,Uri uri)throws Exception{
        ActivityMonitor monitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);
        Intent intent=new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(Intent.ACTION_SEND.equals(action))intent.setType("text/plain").putExtra(Intent.EXTRA_TEXT,uri.toString());else intent.setDataAndType(uri,"video/mp4");
        runOnMainSync(()->getTargetContext().startActivity(intent));Activity player=waitForMonitorWithTimeout(monitor,45000);removeMonitor(monitor);
        if(player==null)throw new AssertionError("Main app did not route media into native player");return player;
    }
    private long[] state(Activity activity){AtomicReference<long[]> result=new AtomicReference<>();runOnMainSync(()->{VideoView video=activity.findViewById(1000);try{android.media.MediaPlayer mp=(android.media.MediaPlayer)field(activity,"media");result.set(new long[]{video.getDuration(),video.getCurrentPosition(),mp==null?0:mp.getVideoWidth(),mp==null?0:mp.getVideoHeight()});}catch(Exception error){result.set(new long[]{0,0,0,0});}});return result.get();}
    private void awaitPlayable(Activity activity)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(SystemClock.elapsedRealtime()<deadline){long[] s=state(activity);if(s[0]>1000&&s[1]>150&&s[2]>0&&s[3]>0)return;SystemClock.sleep(150);}
        throw new AssertionError("Video never produced dimensions and advancing playback: "+java.util.Arrays.toString(state(activity)));
    }
    private void exerciseLoop(Activity activity)throws Exception{
        runOnMainSync(()->{VideoView v=activity.findViewById(1000);v.seekTo(300);v.start();});SystemClock.sleep(450);
        runOnMainSync(()->activity.findViewById(1001).performClick());SystemClock.sleep(1100);
        runOnMainSync(()->activity.findViewById(1002).performClick());runOnMainSync(()->activity.findViewById(1003).performClick());SystemClock.sleep(2500);
        AtomicReference<Boolean> good=new AtomicReference<>(false);
        runOnMainSync(()->{try{long a=(Long)field(activity,"pointA"),b=(Long)field(activity,"pointB");boolean enabled=(Boolean)field(activity,"looping");VideoView v=activity.findViewById(1000);int p=v.getCurrentPosition();good.set(enabled&&b>a&&v.isPlaying()&&p>=a-250&&p<=b+350);}catch(Exception error){throw new RuntimeException(error);}});
        if(!good.get())throw new AssertionError("AB loop did not keep playback inside selected range");
    }
    private Object field(Activity activity,String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void report(String text){Bundle data=new Bundle();data.putString("stream",text+"\n");sendStatus(0,data);}
}
