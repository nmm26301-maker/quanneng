package app.quanneng;

import android.app.*;
import android.os.Bundle;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import java.util.*;

/** 密码验证前遮住内容并推迟导入、分享和播放；所有应用页面共用此入口保护。 */
public class PrivateActivity extends Activity {
    private final ArrayList<Runnable> pending=new ArrayList<>();
    private PasswordGate gate;
    private PasswordGate changeGate;
    private boolean privateResumed;
    private final DeviceUnlock systemUnlock=new DeviceUnlock(this);
    private final ArrayList<java.lang.ref.WeakReference<Dialog>> transientDialogs=new ArrayList<>();
    final void rememberPrivateDialog(Dialog dialog){transientDialogs.add(new java.lang.ref.WeakReference<>(dialog));}
    @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().getDecorView().setForeground(new ColorDrawable(PlayerUi.BG));getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
    @Override protected void onResume(){super.onResume();privateResumed=true;if(!systemUnlock.accept())guard();else{dismissGate();reveal();}}
    @Override protected void onPause(){privateResumed=false;android.os.PowerManager power=getSystemService(android.os.PowerManager.class);if(power!=null&&!power.isInteractive())PrivateSession.lock();if(gate!=null&&!systemUnlock.credentialActive()){gate.dismiss();gate=null;}if(changeGate!=null){changeGate.dismiss();changeGate=null;}for(java.lang.ref.WeakReference<Dialog> ref:transientDialogs){Dialog dialog=ref.get();if(dialog!=null)dialog.cancel();}transientDialogs.clear();getWindow().getDecorView().setForeground(new ColorDrawable(PlayerUi.BG));getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);onPrivateLocked();super.onPause();}
    @Override protected void onStop(){systemUnlock.stopped();super.onStop();}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==DeviceUnlock.REQUEST)systemUnlock.result(result);}
    @Override public void finish(){if(isTaskRoot())PrivateSession.lock();super.finish();}
    protected final boolean privateReady(){return privateResumed&&PrivateSession.isUnlocked();}
    protected final void whenUnlocked(Runnable action){if(privateReady())action.run();else pending.add(action);}
    private void guard(){
        if(isFinishing()||isDestroyed())return;
        if(PrivateSession.isUnlocked()){reveal();return;}
        getWindow().getDecorView().setForeground(new ColorDrawable(PlayerUi.BG));getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);onPrivateLocked();
        if(gate!=null&&gate.isShowing())return;
        gate=new PasswordGate(this,false,()->{gate=null;if(privateReady())reveal();});gate.show();
    }
    private void reveal(){getWindow().getDecorView().setForeground(null);getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);onPrivateUnlocked();ArrayList<Runnable> work=new ArrayList<>(pending);pending.clear();for(int i=0;i<work.size();i++){if(!PrivateSession.isUnlocked()){pending.addAll(work.subList(i,work.size()));break;}work.get(i).run();}}
    protected void onPrivateUnlocked(){}
    protected void onPrivateLocked(){}
    final void beginSystemUnlock(){if(privateResumed)systemUnlock.start();}
    final void systemUnlockMessage(String message){if(gate!=null)gate.systemMessage(message);}
    final void completeSystemUnlock(){if(systemUnlock.accept()){dismissGate();reveal();}}
    private void dismissGate(){if(gate!=null){gate.dismiss();gate=null;}}
    final void usePasswordFallback(){systemUnlock.stop();dismissGate();gate=new PasswordGate(this,false,()->{gate=null;if(privateReady())reveal();},false);gate.show();}
    final boolean canAcceptPassword(long epoch){return privateResumed&&!isFinishing()&&epoch==PrivateSession.generation();}
    protected final void changePrivatePassword(){if(privateReady()){changeGate=new PasswordGate(this,PasswordStore.configured(this),()->{},false);changeGate.setOnDismissListener(dialog->changeGate=null);changeGate.show();}}
    protected final void lockPrivateApp(){systemUnlock.stop();LanShareService.end(this);PrivateSession.lock();guard();}
    @Override public void onBackPressed(){if(!PrivateSession.isUnlocked()){finish();return;}super.onBackPressed();}
    @Override protected void onDestroy(){systemUnlock.stop();if(gate!=null)gate.dismiss();if(changeGate!=null)changeGate.dismiss();pending.clear();super.onDestroy();}
}
