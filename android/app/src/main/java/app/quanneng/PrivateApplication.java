package app.quanneng;

import android.app.*;
import android.os.Bundle;

/** 使用平台生命周期识别整个应用离开前台；应用内页面切换不重复要求密码。 */
public final class PrivateApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private int started;
    @Override public void onCreate(){super.onCreate();PrivateSession.lock();registerActivityLifecycleCallbacks(this);}
    @Override public void onActivityStarted(Activity activity){started++;}
    @Override public void onActivityStopped(Activity activity){started=Math.max(0,started-1);if(started==0&&!activity.isChangingConfigurations())PrivateSession.lock();}
    @Override public void onActivityCreated(Activity activity,Bundle state){}
    @Override public void onActivityResumed(Activity activity){}
    @Override public void onActivityPaused(Activity activity){}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    @Override public void onActivityDestroyed(Activity activity){}
}
