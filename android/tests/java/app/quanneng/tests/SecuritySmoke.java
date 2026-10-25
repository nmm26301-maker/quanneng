package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

/** 验证首次设置、后台重锁、错误密码、限速、旧密码修改和分享入口保护。 */
public final class SecuritySmoke extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Activity app=startActivitySync(new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitGate(app,true);if(PrivateTestAccess.unlocked())throw new AssertionError("Fresh install was unlocked");
        type(app,"too-short","not-matching",null);submit(app);SystemClock.sleep(250);if(PrivateTestAccess.unlocked())throw new AssertionError("Weak or mismatched setup passed");
        type(app,PrivateTestAccess.PASSWORD,PrivateTestAccess.PASSWORD,null);submit(app);awaitUnlocked(app);
        String saved=getTargetContext().getSharedPreferences("private-lock-v1",0).getAll().toString();if(saved.contains(PrivateTestAccess.PASSWORD)||!saved.contains("verifier"))throw new AssertionError("Password persisted in plaintext or verifier missing");
        if((app.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0)throw new AssertionError("Private content not protected from system screenshots");
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);SystemClock.sleep(1000);
        Intent returnIntent=new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);runOnMainSync(()->getTargetContext().startActivity(returnIntent));awaitGate(app,false);
        type(app,"incorrect-password",null,null);submit(app);awaitError(app);if(PrivateTestAccess.unlocked())throw new AssertionError("Wrong password unlocked app");
        Class<?> store=Class.forName("app.quanneng.PasswordStore");Method verify=store.getDeclaredMethod("verify",Context.class,char[].class);verify.setAccessible(true);
        for(int i=0;i<4;i++)verify.invoke(null,getTargetContext(),"still-wrong".toCharArray());
        if(getTargetContext().getSharedPreferences("private-lock-v1",0).getLong("blockedUntil",0)<=System.currentTimeMillis())throw new AssertionError("Failure cooldown missing");
        if((Boolean)verify.invoke(null,getTargetContext(),PrivateTestAccess.PASSWORD.toCharArray()))throw new AssertionError("Cooldown was bypassed");
        // Reset the artificial cooldown only in this signed instrumentation fixture, not app code.
        getTargetContext().getSharedPreferences("private-lock-v1",0).edit().putLong("blockedUntil",0).putInt("failures",0).commit();
        type(app,PrivateTestAccess.PASSWORD,null,null);submit(app);awaitUnlocked(app);
        change(app,PrivateTestAccess.PASSWORD,"FixtureOnly_92!");runOnMainSync(()->{try{PrivateTestAccess.invoke(app,"lockPrivateApp");}catch(Exception e){throw new RuntimeException(e);}});awaitGate(app,false);
        type(app,PrivateTestAccess.PASSWORD,null,null);submit(app);awaitError(app);if(PrivateTestAccess.unlocked())throw new AssertionError("Previous password still works");
        type(app,"FixtureOnly_92!",null,null);submit(app);awaitUnlocked(app);change(app,"FixtureOnly_92!",PrivateTestAccess.PASSWORD);
        captureGate(app);runOnMainSync(app::finish);SystemClock.sleep(300);
        ActivityMonitor nativePlayer=addMonitor("app.quanneng.SystemPlayerActivity",null,false);
        Activity shared=startActivitySync(new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitGate(shared,false);if(waitForMonitorWithTimeout(nativePlayer,800)!=null)throw new AssertionError("Share intent bypassed password");type(shared,PrivateTestAccess.PASSWORD,null,null);submit(shared);Activity player=waitForMonitorWithTimeout(nativePlayer,15000);removeMonitor(nativePlayer);if(player==null)throw new AssertionError("Authorized share did not resume after unlock");runOnMainSync(player::finish);runOnMainSync(shared::finish);
        result.putString("stream","Private setup, secure storage, cooldown, background lock, password change and share routing verified\nQUANNENG_SECURITY_OK\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","QUANNENG_SECURITY_FAILED: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
    private void awaitGate(Activity activity,boolean setup)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> ready=new AtomicReference<>(false);runOnMainSync(()->{try{Dialog d=PrivateTestAccess.gate(activity);ready.set(d!=null&&d.isShowing()&&d.findViewById(1500)!=null&&(!setup||d.findViewById(1501)!=null));}catch(Exception ignored){}});if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError("Password gate missing");}
    private void type(Activity activity,String value,String repeat,String old){runOnMainSync(()->{try{Dialog d=PrivateTestAccess.gate(activity);((EditText)d.findViewById(1500)).setText(value);if(repeat!=null)((EditText)d.findViewById(1501)).setText(repeat);if(old!=null)((EditText)d.findViewById(1503)).setText(old);}catch(Exception e){throw new RuntimeException(e);}});}
    private void submit(Activity activity){runOnMainSync(()->{try{PrivateTestAccess.gate(activity).findViewById(1502).performClick();}catch(Exception e){throw new RuntimeException(e);}});}
    private void awaitUnlocked(Activity a)throws Exception{PrivateTestAccess.awaitUnlocked(this,a);SystemClock.sleep(150);}
    private void awaitError(Activity a)throws Exception{long deadline=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> ready=new AtomicReference<>(false);runOnMainSync(()->{try{Dialog d=PrivateTestAccess.gate(a);ready.set(d!=null&&((TextView)d.findViewById(1504)).getText().length()>0&&d.findViewById(1502).isEnabled());}catch(Exception ignored){}});if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError("Incorrect password has no feedback");}
    private void change(Activity a,String old,String next)throws Exception{runOnMainSync(()->{try{PrivateTestAccess.invoke(a,"changePrivatePassword");}catch(Exception e){throw new RuntimeException(e);}});type(a,next,next,old);submit(a);long deadline=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> gone=new AtomicReference<>(false);runOnMainSync(()->{try{Dialog d=PrivateTestAccess.gate(a);gone.set(d==null||!d.isShowing());}catch(Exception ignored){}});if(gone.get())return;SystemClock.sleep(100);}throw new AssertionError("Password change failed");}
    private void captureGate(Activity activity)throws Exception{runOnMainSync(()->{try{PrivateTestAccess.invoke(activity,"lockPrivateApp");}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(400);runOnMainSync(()->{try{Dialog d=PrivateTestAccess.gate(activity);View view=d.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));Bitmap scaled=Bitmap.createScaledBitmap(bitmap,420,bitmap.getHeight()*420/bitmap.getWidth(),true);java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,80,data);Bundle out=new Bundle();out.putString("stream","QUANNENG_UI_PRIVATE:"+android.util.Base64.encodeToString(data.toByteArray(),android.util.Base64.NO_WRAP)+"\n");sendStatus(0,out);scaled.recycle();bitmap.recycle();}catch(Exception error){throw new RuntimeException(error);}});type(activity,PrivateTestAccess.PASSWORD,null,null);submit(activity);awaitUnlocked(activity);}
}
