package app.quanneng;

import android.app.*;
import android.os.Bundle;

/** 使用平台生命周期识别整个应用离开前台；应用内页面切换不重复要求密码。 */
public final class PrivateApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private volatile int started;
    private volatile int resumed;
    boolean visible(){return resumed>0;}
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expire=()->{if(started==0){PrivateSession.lock();VaultStore.purge(this);}};
    @Override public void onCreate(){super.onCreate();PrivateSession.lock();VaultStore.purge(this);LanShareService.cleanAbandoned(this);registerActivityLifecycleCallbacks(this);android.content.BroadcastReceiver screen=new android.content.BroadcastReceiver(){@Override public void onReceive(android.content.Context c,android.content.Intent intent){PrivateSession.lock();VaultStore.purge(c);}};android.content.IntentFilter filter=new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF);if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,android.content.Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);}
    @Override public void onActivityStarted(Activity activity){if(started++==0){handler.removeCallbacks(expire);PrivateSession.foreground();}}
    @Override public void onActivityStopped(Activity activity){started=Math.max(0,started-1);if(started==0&&!activity.isChangingConfigurations()){PrivateSession.background();VaultStore.purge(this);handler.removeCallbacks(expire);handler.postDelayed(expire,PrivateSession.GRACE_MILLIS);}}
    @Override public void onActivityCreated(Activity activity,Bundle state){}
    @Override public void onActivityResumed(Activity activity){resumed++;}
    @Override public void onActivityPaused(Activity activity){resumed=Math.max(0,resumed-1);}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    @Override public void onActivityDestroyed(Activity activity){}
}
