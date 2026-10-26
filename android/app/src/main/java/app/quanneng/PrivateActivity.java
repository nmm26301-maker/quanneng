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
    private final ArrayList<java.lang.ref.WeakReference<Dialog>> transientDialogs=new ArrayList<>();
    final void rememberPrivateDialog(Dialog dialog){transientDialogs.add(new java.lang.ref.WeakReference<>(dialog));}
    @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().getDecorView().setForeground(new ColorDrawable(PlayerUi.BG));getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
    @Override protected void onResume(){super.onResume();privateResumed=true;guard();}
    @Override protected void onPause(){privateResumed=false;if(!PrivateSession.consumeInternalDeparture(this))PrivateSession.lock();if(gate!=null){gate.dismiss();gate=null;}if(changeGate!=null){changeGate.dismiss();changeGate=null;}for(java.lang.ref.WeakReference<Dialog> ref:transientDialogs){Dialog dialog=ref.get();if(dialog!=null)dialog.cancel();}transientDialogs.clear();getWindow().getDecorView().setForeground(new ColorDrawable(PlayerUi.BG));getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);onPrivateLocked();super.onPause();}
    @Override public void startActivityForResult(android.content.Intent intent,int request,Bundle options){android.content.ComponentName component=intent.getComponent();boolean internal=component!=null&&getPackageName().equals(component.getPackageName())&&!getClass().getName().equals(component.getClassName());if(internal)PrivateSession.internalDeparture(this);else PrivateSession.lock();try{super.startActivityForResult(intent,request,options);}catch(RuntimeException error){PrivateSession.consumeInternalDeparture(this);throw error;}}
    @Override public void finish(){if(isTaskRoot())PrivateSession.lock();else PrivateSession.internalDeparture(this);super.finish();}
    protected final boolean privateReady(){return privateResumed&&PrivateSession.isUnlocked();}
    protected final void whenUnlocked(Runnable action){if(privateReady())action.run();else pending.add(action);}
    private void guard(){
        if(isFinishing()||isDestroyed())return;
        if(PrivateSession.isUnlocked()){reveal();return;}
        getWindow().getDecorView().setForeground(new ColorDrawable(PlayerUi.BG));getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);onPrivateLocked();
        if(gate!=null){gate.dismiss();gate=null;}
        gate=new PasswordGate(this,false,()->{gate=null;if(privateReady())reveal();});gate.show();
    }
    private void reveal(){getWindow().getDecorView().setForeground(null);getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);onPrivateUnlocked();ArrayList<Runnable> work=new ArrayList<>(pending);pending.clear();for(int i=0;i<work.size();i++){if(!PrivateSession.isUnlocked()){pending.addAll(work.subList(i,work.size()));break;}work.get(i).run();}}
    protected void onPrivateUnlocked(){}
    protected void onPrivateLocked(){}
    final boolean canAcceptPassword(long epoch){return privateResumed&&!isFinishing()&&epoch==PrivateSession.generation();}
    protected final void changePrivatePassword(){if(privateReady()){changeGate=new PasswordGate(this,true,()->{});changeGate.setOnDismissListener(dialog->changeGate=null);changeGate.show();}}
    protected final void lockPrivateApp(){PrivateSession.lock();guard();}
    @Override public void onBackPressed(){if(!PrivateSession.isUnlocked()){finish();return;}super.onBackPressed();}
    @Override protected void onDestroy(){if(gate!=null)gate.dismiss();if(changeGate!=null)changeGate.dismiss();pending.clear();super.onDestroy();}
}
