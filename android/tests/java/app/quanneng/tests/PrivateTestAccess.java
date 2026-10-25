package app.quanneng.tests;

import android.app.*;
import android.os.*;
import android.widget.*;
import java.lang.reflect.*;

/** 仅独立测试 APK 使用的固定测试密码；正式 APK 不含测试密码或免密分支。 */
final class PrivateTestAccess {
    static volatile Activity current;
    static final String PASSWORD="FixtureOnly_47!";
    static Object field(Object value,String name)throws Exception{for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Dialog gate(Activity activity)throws Exception{Object changing=field(activity,"changeGate");if(changing instanceof Dialog&&((Dialog)changing).isShowing())return (Dialog)changing;Object value=field(activity,"gate");return value instanceof Dialog?(Dialog)value:null;}
    static boolean unlocked()throws Exception{Class<?> type=Class.forName("app.quanneng.PrivateSession");Method m=type.getDeclaredMethod("isUnlocked");m.setAccessible(true);return (Boolean)m.invoke(null);}
    static void invoke(Activity activity,String name)throws Exception{Method m=activity.getClass().getSuperclass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    static void install(Instrumentation test){Application app=(Application)test.getTargetContext().getApplicationContext();app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity activity){current=activity;new Handler(Looper.getMainLooper()).postDelayed(()->{try{Dialog d=gate(activity);if(d!=null&&d.isShowing()){EditText confirmation=d.findViewById(1501);if(confirmation!=null)throw new AssertionError("SecuritySmoke must configure fixture password first");((EditText)d.findViewById(1500)).setText(PASSWORD);d.findViewById(1502).performClick();}}catch(Exception e){throw new RuntimeException(e);}},100);}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    });}
    static void awaitUnlocked(Instrumentation test,Activity activity)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){if(unlocked())return;SystemClock.sleep(100);}throw new AssertionError("Test fixture password did not unlock application");}
}
