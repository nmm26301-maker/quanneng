package app.quanneng;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 盐化派生后用 Android Keystore HMAC；不存储明文或可还原的密码。 */
final class PasswordStore {
    private static final String KEY="quanneng.password.hmac.v1";
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("private-lock-v1",Context.MODE_PRIVATE);}
    static boolean configured(Context c){return prefs(c).contains("verifier");}
    static long blockedMillis(Context c){return Math.max(0,prefs(c).getLong("blockedUntil",0)-System.currentTimeMillis());}
    private static SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).setKeySize(256).build());return generator.generateKey();
    }
    private static byte[] derive(char[] password,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);byte[] derived=null;
        try{derived=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();Mac mac=Mac.getInstance("HmacSHA256");mac.init(key());return mac.doFinal(derived);}finally{spec.clearPassword();if(derived!=null)Arrays.fill(derived,(byte)0);}
    }
    static synchronized boolean verify(Context c,char[] password)throws Exception{
        SharedPreferences p=prefs(c);if(!configured(c)||blockedMillis(c)>0)return false;
        byte[] salt=Base64.decode(p.getString("salt",""),Base64.NO_WRAP),wanted=Base64.decode(p.getString("verifier",""),Base64.NO_WRAP);
        boolean good=MessageDigest.isEqual(wanted,derive(password,salt));SharedPreferences.Editor edit=p.edit();
        if(good)edit.putInt("failures",0).putLong("blockedUntil",0);
        else{int failures=p.getInt("failures",0)+1;edit.putInt("failures",failures);if(failures>=5)edit.putLong("blockedUntil",System.currentTimeMillis()+Math.min(900000,30000L*(1L<<Math.min(5,failures-5))));}
        if(!edit.commit())throw new IllegalStateException("安全配置无法保存");return good;
    }
    static synchronized void create(Context c,char[] password)throws Exception{if(configured(c))throw new IllegalStateException("密码已设置，请先解锁");replace(c,password);}
    static synchronized boolean change(Context c,char[] previous,char[] next)throws Exception{if(!verify(c,previous))return false;replace(c,next);return true;}
    private static void replace(Context c,char[] password)throws Exception{
        if(password.length<8||password.length>64||new String(password).trim().isEmpty())throw new IllegalArgumentException("密码需为 8–64 位");
        byte[] salt=new byte[32];new SecureRandom().nextBytes(salt);byte[] verifier=derive(password,salt);
        if(!prefs(c).edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("verifier",Base64.encodeToString(verifier,Base64.NO_WRAP)).putInt("failures",0).putLong("blockedUntil",0).commit())throw new IllegalStateException("安全配置无法保存");
        Arrays.fill(verifier,(byte)0);
    }
}
