package app.quanneng.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import android.security.keystore.*;
import android.util.Base64;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Prepare, reinstall the new APK without removing the old identity, then verify both states. */
public final class CoexistenceSmoke extends Instrumentation {
 private String phase;
 public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","verify");start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();if(!"app.quanneng.next".equals(c.getPackageName()))throw new AssertionError("New application identity incorrect");
  String legacy;try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("content query --uri content://app.quanneng.media/");InputStream in=new FileInputStream(fd.getFileDescriptor());ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);legacy=out.toString("UTF-8").trim();}
  if(!legacy.contains("marker=")||!legacy.contains("uid=")||legacy.contains("uid="+android.os.Process.myUid()))throw new AssertionError("Legacy identity or separate UID missing: "+legacy);
  SharedPreferences prefs=c.getSharedPreferences("upgrade-probe",0);KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="quanneng-next-upgrade-probe";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  if("prepare".equals(phase)){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());SecretKey key=gen.generateKey();String marker=java.util.UUID.randomUUID().toString();cipher.init(Cipher.ENCRYPT_MODE,key);byte[] encrypted=cipher.doFinal(marker.getBytes("UTF-8"));prefs.edit().putString("marker",marker).putString("legacy",legacy).putInt("uid",android.os.Process.myUid()).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit();}
  else{if(!store.containsAlias(alias)||prefs.getInt("uid",-1)!=android.os.Process.myUid()||!legacy.equals(prefs.getString("legacy","")))throw new AssertionError("Reinstall changed an identity or legacy data");SecretKey key=(SecretKey)store.getKey(alias,null);cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));String clear=new String(cipher.doFinal(Base64.decode(prefs.getString("cipher",""),Base64.NO_WRAP)),"UTF-8");if(!clear.equals(prefs.getString("marker","missing")))throw new AssertionError("New private data or encryption key was lost");}
  result.putString("stream","QUANNENG_COEXIST_"+phase.toUpperCase(java.util.Locale.ROOT)+"_OK\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("stream","QUANNENG_COEXIST_FAILED: "+trace+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
