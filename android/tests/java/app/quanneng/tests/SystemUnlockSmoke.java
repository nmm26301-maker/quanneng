package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/** Runs last on the ephemeral emulator after its real system PIN has been configured. */
public final class SystemUnlockSmoke extends Instrumentation {
    private static final String PIN="482947";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().getSharedPreferences("private-lock-v1",0).edit().clear().commit();
        Activity activity=startActivitySync(new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitCredential();if(PrivateTestAccess.unlocked())throw new AssertionError("System gate unlocked before verification");report("Real system credential shown without creating an app password");
        for(int attempt=0;attempt<3;attempt++){getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);SystemClock.sleep(450);java.util.concurrent.atomic.AtomicBoolean gateFocused=new java.util.concurrent.atomic.AtomicBoolean();runOnMainSync(()->{try{Dialog d=PrivateTestAccess.gate(activity);gateFocused.set(d!=null&&d.isShowing()&&d.getWindow().getDecorView().hasWindowFocus());}catch(Exception ignored){}});if(gateFocused.get())break;}
        if(PrivateTestAccess.unlocked())throw new AssertionError("Cancel unlocked the app");
        runOnMainSync(()->{try{Dialog gate=PrivateTestAccess.gate(activity);if(gate==null||gate.findViewById(1500)!=null)throw new AssertionError("App password unexpectedly required");gate.findViewById(1506).performClick();}catch(Exception e){throw new RuntimeException(e);}});
        enterPin();PrivateTestAccess.awaitUnlocked(this,activity);report("Cancelled system request stays locked; retry with real PIN unlocks");
        if(getTargetContext().getSharedPreferences("private-lock-v1",0).contains("verifier"))throw new AssertionError("System authentication created an app password");
        homeAndReturn(activity,false);if(!PrivateTestAccess.unlocked())throw new AssertionError("Short return repeated authentication");
        homeAndReturn(activity,true);awaitCredential();if(PrivateTestAccess.unlocked())throw new AssertionError("Expired session bypassed authentication");enterPin();PrivateTestAccess.awaitUnlocked(this,activity);report("Short return reused session; expired background session required system verification");
        runOnMainSync(()->{try{PrivateTestAccess.invoke(activity,"lockPrivateApp");}catch(Exception e){throw new RuntimeException(e);}});awaitCredential();if(PrivateTestAccess.unlocked())throw new AssertionError("Manual lock retained authorization");enterPin();PrivateTestAccess.awaitUnlocked(this,activity);
        shell("input keyevent 223");long offDeadline=SystemClock.elapsedRealtime()+8000;while(PrivateTestAccess.unlocked()&&SystemClock.elapsedRealtime()<offDeadline)SystemClock.sleep(100);if(PrivateTestAccess.unlocked())throw new AssertionError("Screen lock did not revoke session; interactive="+getTargetContext().getSystemService(android.os.PowerManager.class).isInteractive());shell("input keyevent 224");report("Screen-off revoked the authorized session");runOnMainSync(activity::finish);result.putString("stream","QUANNENG_SYSTEM_UNLOCK_OK\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));result.putString("stream","QUANNENG_SYSTEM_UNLOCK_FAILED: "+trace+"\n");finish(Activity.RESULT_CANCELED,result);}}
    private void homeAndReturn(Activity activity,boolean expire)throws Exception{
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);SystemClock.sleep(500);
        if(expire){Class<?> session=Class.forName("app.quanneng.PrivateSession");Field at=session.getDeclaredField("backgroundUntil");at.setAccessible(true);at.setLong(null,1);}
        runOnMainSync(()->getTargetContext().startActivity(new Intent().setComponent(new ComponentName("app.quanneng","app.quanneng.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));SystemClock.sleep(500);
    }
    private AccessibilityNodeInfo credential(){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;List<AccessibilityNodeInfo> fields=root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/lockPassword");if(!fields.isEmpty())return fields.get(0);return findEditable(root);}
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isEditable()&&("com.android.systemui".equals(String.valueOf(node.getPackageName()))||"com.android.settings".equals(String.valueOf(node.getPackageName()))))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findEditable(node.getChild(i));if(found!=null)return found;}return null;}
    private void awaitCredential()throws Exception{long limit=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<limit){if(credential()!=null)return;SystemClock.sleep(150);}throw new AssertionError("Real Android credential field missing: "+getUiAutomation().getRootInActiveWindow());}
    private void enterPin()throws Exception{awaitCredential();AccessibilityNodeInfo field=credential();field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,PIN);boolean set=field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text);if(!set)shell("input text "+PIN);shell("input keyevent 66");SystemClock.sleep(600);}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream in=new FileInputStream(fd.getFileDescriptor())){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
    private void report(String message){Bundle data=new Bundle();data.putString("stream",message+"\n");sendStatus(0,data);}
}
