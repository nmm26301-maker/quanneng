package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** 在 Android 原生 ABI 上执行真实 yt-dlp 下载和 FFmpeg H.264 转换，再从私人 provider 播放。 */
public final class TransferSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Class<?> runtime,tokenClass,progressClass;
    private Object token,progress;
    @Override public void onStart(){PrivateTestAccess.install(this);Bundle result=new Bundle();try{
        Activity screen=startActivitySync(new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.TransferActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));PrivateTestAccess.awaitUnlocked(this,screen);
        runtime=Class.forName("app.quanneng.TransferRuntime");tokenClass=Class.forName("app.quanneng.TransferRuntime$Token");progressClass=Class.forName("app.quanneng.TransferRuntime$Progress");Constructor<?> ctor=tokenClass.getDeclaredConstructor();ctor.setAccessible(true);token=ctor.newInstance();progress=Proxy.newProxyInstance(progressClass.getClassLoader(),new Class<?>[]{progressClass},(proxy,m,a)->null);
        call("initialize",new Class<?>[]{Context.class},getTargetContext());report("Bundled Python, yt-dlp and FFmpeg initialized on Android");
        String version=(String)call("runFFmpeg",new Class<?>[]{Context.class,List.class,tokenClass,progressClass},getTargetContext(),Arrays.asList("-version"),token,progress);
        if(!version.contains("ffmpeg version")||version.contains("--enable-nonfree"))throw new AssertionError("FFmpeg version missing or nonfree build configuration present");
        Class<?> service=Class.forName("app.quanneng.TransferService");
        Set<String> before=completed();
        runOnMainSync(()->{((android.widget.EditText)screen.findViewById(1600)).setText("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4");screen.findViewById(1601).performClick();});
        SystemClock.sleep(600);getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
        awaitJob(service);File downloaded=added(before);if(downloaded.length()<100000)throw new AssertionError("yt-dlp did not create real CC0 bytes");report("Foreground-service yt-dlp download completed while application was locked in background");
        Intent back=new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.TransferActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);runOnMainSync(()->getTargetContext().startActivity(back));PrivateTestAccess.awaitUnlocked(this,screen);final Activity activeScreen=PrivateTestAccess.current;
        Set<String> afterDownload=completed();Method start=service.getDeclaredMethod("start",Context.class,String.class,String.class);start.setAccessible(true);
        String inputUri="content://app.quanneng.next.media/"+downloaded.getParentFile().getName()+"/"+downloaded.getName();
        final java.util.concurrent.atomic.AtomicReference<Boolean> began=new java.util.concurrent.atomic.AtomicReference<>(false);runOnMainSync(()->{try{began.set((Boolean)start.invoke(null,getTargetContext(),"convert",inputUri));}catch(Exception error){throw new RuntimeException(error);}});
        if(!began.get())throw new AssertionError("Conversion service did not start");awaitJob(service);File output=added(afterDownload);File folder=output.getParentFile();
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();retriever.setDataSource(output.getAbsolutePath());String duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null||Long.parseLong(duration)<1000||retriever.getFrameAtTime()==null)throw new AssertionError("Converted MP4 is not a decodable video");retriever.release();report("FFmpeg produced independently decodable H.264 MP4");
        try(OutputStream marker=new FileOutputStream(new File(folder,".complete"))){marker.write(output.getName().getBytes("UTF-8"));}
        ActivityMonitor monitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);runOnMainSync(()->activeScreen.startActivity(new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.SystemPlayerActivity")).setData(Uri.parse("content://app.quanneng.next.media/"+folder.getName()+"/"+output.getName()))));Activity player=waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);if(player==null)throw new AssertionError("Completed video did not open in native player");PrivateTestAccess.awaitUnlocked(this,player);
        long deadline=SystemClock.elapsedRealtime()+20000;boolean playing=false;while(SystemClock.elapsedRealtime()<deadline){java.util.concurrent.atomic.AtomicReference<Boolean> ready=new java.util.concurrent.atomic.AtomicReference<>(false);runOnMainSync(()->{try{Object view=player.findViewById(1000);int p=((Number)view.getClass().getMethod("getCurrentPosition").invoke(view)).intValue();int d=((Number)view.getClass().getMethod("getDuration").invoke(view)).intValue();ready.set(p>100&&d>1000);}catch(Exception ignored){}});if(ready.get()){playing=true;break;}SystemClock.sleep(100);}if(!playing)throw new AssertionError("Converted private content URI did not advance");
        runOnMainSync(player::finish);runOnMainSync(activeScreen::finish);if(activeScreen!=screen)runOnMainSync(screen::finish);SystemClock.sleep(400);if(PrivateTestAccess.unlocked())throw new AssertionError("Closing the root activity left the private session unlocked");
        try(InputStream forbidden=getTargetContext().getContentResolver().openInputStream(Uri.parse("content://app.quanneng.next.media/"+folder.getName()+"/"+output.getName()))){throw new AssertionError("Private video readable after application locked");}catch(FileNotFoundException|SecurityException expected){}
        result.putString("stream","Real download, conversion, private content playback and locked provider access verified\nQUANNENG_TRANSFER_OK\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){Throwable reason=error instanceof InvocationTargetException?((InvocationTargetException)error).getTargetException():error;result.putString("stream","QUANNENG_TRANSFER_FAILED: "+reason.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
    private Set<String> completed()throws Exception{Class<?> service=Class.forName("app.quanneng.TransferService");Method method=service.getDeclaredMethod("completed",Context.class);method.setAccessible(true);Set<String> result=new HashSet<>();for(Object file:(List<?>)method.invoke(null,getTargetContext()))result.add(((File)file).getAbsolutePath());return result;}
    private File added(Set<String> before)throws Exception{for(String path:completed())if(!before.contains(path))return new File(path);throw new AssertionError("Service published no completed file");}
    private void awaitJob(Class<?> service)throws Exception{Field running=service.getDeclaredField("running"),failure=service.getDeclaredField("failure");running.setAccessible(true);failure.setAccessible(true);long deadline=SystemClock.elapsedRealtime()+180000;while(SystemClock.elapsedRealtime()<deadline){if(!(Boolean)running.get(null)){String error=(String)failure.get(null);if(!error.isEmpty())throw new AssertionError(error);return;}SystemClock.sleep(200);}throw new AssertionError("Foreground transfer task timed out");}
    private Object call(String name,Class<?>[] types,Object... args)throws Exception{Method method=runtime.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(null,args);}
    private void report(String text){Bundle message=new Bundle();message.putString("stream",text+"\n");sendStatus(0,message);}
}
