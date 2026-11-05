package app.quanneng.tests;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** 真实播放器验证选集、边界、续播、播完自动切集与关闭连播。 */
public final class EpisodeSmoke extends Instrumentation {
 private Activity player;
 private final ArrayList<String> urls=new ArrayList<>();
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){PrivateTestAccess.install(this);Bundle result=new Bundle();try{
  for(int i=1;i<=3;i++)urls.add("content://app.quanneng.next.tests.fixture/buffer-fixture.mp4?episode="+i);
  ArrayList<String> input=new ArrayList<>(urls);input.add(urls.get(0));input.add("file:///private/invalid.mp4");
  ActivityMonitor monitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);Intent intent=new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.SystemPlayerActivity")).setData(Uri.parse(urls.get(0))).putStringArrayListExtra("queue",input).putStringArrayListExtra("queueTitles",new ArrayList<>(Arrays.asList("开篇","线索","结局","重复","无效"))).putExtra("autoNext",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
  onUi(()->getTargetContext().startActivity(intent));player=waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);check(player!=null,"Player missing");PrivateTestAccess.awaitUnlocked(this,player);await(0);
  onUi(()->{try{check(((ArrayList<?>)PrivateTestAccess.field(player,"queue")).size()==3,"Queue accepted duplicate or unsafe entries");check(!player.findViewById(1821).isEnabled(),"Previous enabled on first episode");player.findViewById(1810).performClick();AlertDialog dialog=(AlertDialog)PrivateTestAccess.field(player,"episodeDialog");check(dialog.getListView().getCheckedItemPosition()==0,"Current episode not marked");dialog.getListView().performItemClick(null,1,1);}catch(Exception e){throw new RuntimeException(e);}});await(1);
  onUi(()->{try{check(((TextView)PrivateTestAccess.field(player,"title")).getText().toString().equals("线索"),"Episode title lost");player.findViewById(1821).performClick();}catch(Exception e){throw new RuntimeException(e);}});await(0);
  onUi(()->player.findViewById(1823).performClick());await(1);complete();await(2);
  onUi(()->{try{check(!player.findViewById(1823).isEnabled(),"Next enabled on last episode");player.findViewById(1810).performClick();((AlertDialog)PrivateTestAccess.field(player,"episodeDialog")).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();onUi(()->{try{check(!player.getIntent().getBooleanExtra("autoNext",true),"Auto next not disabled");player.findViewById(1821).performClick();}catch(Exception e){throw new RuntimeException(e);}});await(1);complete();SystemClock.sleep(1700);
  onUi(()->{try{check(PrivateTestAccess.field(player,"current").toString().equals(urls.get(1)),"Disabled autoplay advanced episodes");}catch(Exception e){throw new RuntimeException(e);}});
  result.putString("stream","Selection, marked current episode, names, previous/next boundaries, actual end autoplay and disabled autoplay verified\nQUANNENG_EPISODES_OK\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","QUANNENG_EPISODES_FAILED: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{if(player!=null)onUi(player::finish);getTargetContext().getSharedPreferences("episodes",0).edit().putBoolean("autoNext",true).commit();}}
 private void onUi(Runnable task){AtomicReference<Throwable> failure=new AtomicReference<>();super.runOnMainSync(()->{try{task.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError("UI verification failed",failure.get());}
 private void complete(){onUi(()->{try{Object v=player.findViewById(1000);int duration=(Integer)v.getClass().getMethod("getDuration").invoke(v);v.getClass().getMethod("seekTo",int.class).invoke(v,duration-500);v.getClass().getMethod("start").invoke(v);}catch(Exception e){throw new RuntimeException(e);}});}
 private void await(int index)throws Exception{long limit=SystemClock.elapsedRealtime()+18000;AtomicBoolean ready=new AtomicBoolean();while(SystemClock.elapsedRealtime()<limit){onUi(()->{try{Object v=player.findViewById(1000);ready.set(PrivateTestAccess.field(player,"current").toString().equals(urls.get(index))&&(Boolean)PrivateTestAccess.field(player,"ready")&&(Integer)v.getClass().getMethod("getCurrentPosition").invoke(v)>150);}catch(Exception ignored){}});if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError("Episode "+index+" did not play");}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
