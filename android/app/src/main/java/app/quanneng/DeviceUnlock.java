package app.quanneng;

import android.app.*;
import android.content.*;
import android.hardware.biometrics.*;
import android.os.*;

/** Only the platform's authentication callback/result may authorize this request. */
final class DeviceUnlock {
    static final int REQUEST=18640;
    private final PrivateActivity owner;
    private CancellationSignal cancellation;
    private long requestId,epoch;
    private boolean active,credential,pending;
    DeviceUnlock(PrivateActivity activity){owner=activity;}
    static boolean available(android.content.Context c){KeyguardManager k=c.getSystemService(KeyguardManager.class);return k!=null&&k.isDeviceSecure();}
    boolean credentialActive(){return active&&credential;}
    void start(){
        if(active||!available(owner))return;
        active=true;pending=false;credential=false;epoch=PrivateSession.generation();final long id=++requestId;
        owner.systemUnlockMessage("请完成系统验证");
        if(Build.VERSION.SDK_INT<28){launchCredential(id);return;}
        try{
            BiometricPrompt.Builder builder=new BiometricPrompt.Builder(owner).setTitle("解锁全能").setSubtitle("使用系统指纹或锁屏密码");
            if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else if(Build.VERSION.SDK_INT>=29)builder.setDeviceCredentialAllowed(true);
            else builder.setNegativeButton("使用锁屏密码",owner.getMainExecutor(),(dialog,which)->launchCredential(id));
            cancellation=new CancellationSignal();builder.build().authenticate(cancellation,owner.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){if(valid(id)){pending=true;owner.completeSystemUnlock();}}
                @Override public void onAuthenticationFailed(){if(valid(id))owner.systemUnlockMessage("未识别，请重试指纹或使用锁屏密码");}
                @Override public void onAuthenticationError(int code,CharSequence message){if(!valid(id)||credential)return;
                    if(code==BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED||code==BiometricPrompt.BIOMETRIC_ERROR_CANCELED){active=false;owner.systemUnlockMessage("验证已取消，点击下方按钮重试");}
                    else launchCredential(id);
                }
            });
        }catch(RuntimeException failure){launchCredential(id);}
    }
    private boolean valid(long id){return active&&id==requestId&&epoch==PrivateSession.generation()&&!owner.isFinishing()&&!owner.isDestroyed();}
    private void launchCredential(long id){
        if(!valid(id))return;
        KeyguardManager k=owner.getSystemService(KeyguardManager.class);
        Intent intent=k==null?null:k.createConfirmDeviceCredentialIntent("解锁全能","验证手机的锁屏密码、图案或 PIN");
        if(intent==null){active=false;owner.systemUnlockMessage("请先在系统设置中设置锁屏密码");return;}
        credential=true;
        try{owner.startActivityForResult(intent,REQUEST);}catch(RuntimeException unavailable){active=false;credential=false;owner.systemUnlockMessage("系统验证暂不可用，请重试");}
    }
    void result(int code){if(!active||!credential)return;credential=false;if(code==Activity.RESULT_OK&&valid(requestId)){pending=true;owner.completeSystemUnlock();}else{active=false;pending=false;owner.systemUnlockMessage("验证已取消，点击下方按钮重试");}}
    boolean accept(){
        if(!pending||!valid(requestId)||!owner.canAcceptPassword(epoch))return false;
        PowerManager power=owner.getSystemService(PowerManager.class);KeyguardManager keyguard=owner.getSystemService(KeyguardManager.class);
        if(power==null||!power.isInteractive()||keyguard==null||keyguard.isDeviceLocked())return false;
        if(!PrivateSession.unlock(epoch))return false;pending=false;active=false;return true;
    }
    void stop(){++requestId;active=false;pending=false;credential=false;if(cancellation!=null){cancellation.cancel();cancellation=null;}}
    void stopped(){if(!credential&&!pending)stop();}
}
