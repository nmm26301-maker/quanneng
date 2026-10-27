package app.quanneng.legacy;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
/** CI-only legacy identity with persistent private data and a device-bound encryption key. */
public final class MarkerProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="legacy-coexist-marker";
  if(!store.containsAlias(alias)){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
  SharedPreferences prefs=getContext().getSharedPreferences("legacy-state",0);SecretKey key=(SecretKey)store.getKey(alias,null);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  if(!prefs.contains("cipher")){String marker=java.util.UUID.randomUUID().toString();cipher.init(Cipher.ENCRYPT_MODE,key);byte[] encrypted=cipher.doFinal(marker.getBytes("UTF-8"));prefs.edit().putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit();}
  cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));String marker=new String(cipher.doFinal(Base64.decode(prefs.getString("cipher",""),Base64.NO_WRAP)),"UTF-8");MatrixCursor result=new MatrixCursor(new String[]{"marker","uid"});result.addRow(new Object[]{marker,android.os.Process.myUid()});return result;
 }catch(Exception e){throw new IllegalStateException("Legacy marker could not be decrypted",e);}}
 public String getType(Uri u){return "vnd.android.cursor.item/legacy-marker";}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
