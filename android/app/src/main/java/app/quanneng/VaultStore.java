package app.quanneng;
import android.content.*;
import android.net.Uri;
import android.security.keystore.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.security.*;
import java.util.*;
import java.nio.ByteBuffer;
/** 分块 AES-256-GCM：序号及终止帧认证，限制内存占用并检测篡改、调序和截断。 */
final class VaultStore {
 private static final int BLOCK=262144;
 static File root(Context c){File f=new File(c.getNoBackupFilesDir(),"encrypted-vault");f.mkdirs();return f;}
 static File cache(Context c){File f=new File(c.getCacheDir(),"vault-open");f.mkdirs();return f;}
 static synchronized SecretKey key()throws Exception{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);if(k.containsAlias("quanneng-vault-v1"))return (SecretKey)k.getKey("quanneng-vault-v1",null);KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("quanneng-vault-v1",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 private static byte[] crypt(SecretKey key,int mode,byte[] id,int index,int flag,byte[] bytes)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");if(mode==Cipher.ENCRYPT_MODE){cipher.init(mode,key);byte[] iv=cipher.getIV();cipher.updateAAD(ByteBuffer.allocate(21).put(id).putInt(index).put((byte)flag).array());byte[] data=cipher.doFinal(bytes);byte[] all=new byte[iv.length+data.length];System.arraycopy(iv,0,all,0,12);System.arraycopy(data,0,all,12,data.length);return all;}if(bytes.length<28)throw new IOException("密文帧不完整");cipher.init(mode,key,new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));cipher.updateAAD(ByteBuffer.allocate(21).put(id).putInt(index).put((byte)flag).array());return cipher.doFinal(bytes,12,bytes.length-12);}
 private static void frame(DataOutputStream out,SecretKey key,byte[] id,int seq,int flag,byte[] data)throws Exception{byte[] encrypted=crypt(key,Cipher.ENCRYPT_MODE,id,seq,flag,data);out.writeByte(flag);out.writeInt(encrypted.length);out.write(encrypted);}
 static File add(Context c,Uri source)throws Exception{File output=new File(root(c),UUID.randomUUID()+".qvault"),part=new File(output+".part");SecretKey key=key();byte[] id=new byte[16];new SecureRandom().nextBytes(id);try(InputStream in=c.getContentResolver().openInputStream(source);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(part)))){if(in==null)throw new IOException("源文件无法读取");out.write(new byte[]{'Q','V',2});out.write(id);frame(out,key,id,0,2,LibraryActivity.name(c,source).getBytes("UTF-8"));byte[] block=new byte[BLOCK];int seq=1,n;while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("已取消");frame(out,key,id,seq++,0,Arrays.copyOf(block,n));}frame(out,key,id,seq,1,new byte[0]);out.flush();if(!part.renameTo(output))throw new IOException("保存失败");return output;}finally{part.delete();}}
 static File open(Context c,File encrypted)throws Exception{File part=new File(cache(c),UUID.randomUUID()+".part");boolean good=false;try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(encrypted)));OutputStream out=new BufferedOutputStream(new FileOutputStream(part))){if(in.readByte()!='Q'||in.readByte()!='V'||in.readByte()!=2)throw new IOException("保险箱文件格式不正确");byte[] id=new byte[16];in.readFully(id);SecretKey key=key();int seq=0;while(true){if(Thread.currentThread().isInterrupted())throw new IOException("已取消");int flag=in.readUnsignedByte(),length=in.readInt();if(length<28||length>BLOCK+28||seq==0&&flag!=2||seq>0&&flag!=0&&flag!=1)throw new IOException("密文帧无效");byte[] data=new byte[length];in.readFully(data);byte[] clear=crypt(key,Cipher.DECRYPT_MODE,id,seq++,flag,data);if(flag==1){if(clear.length!=0||in.read()!=-1)throw new IOException("密文尾部无效");break;}if(flag==0)out.write(clear);}out.flush();good=true;}finally{if(!good)part.delete();}if(!PrivateSession.isUnlocked()){part.delete();throw new IOException("应用已锁定，请解锁后重试");}File result=new File(cache(c),UUID.randomUUID()+".mp4");if(!part.renameTo(result)){part.delete();throw new IOException("临时文件保存失败");}return result;}
 static void purge(Context c){File[] f=cache(c).listFiles();if(f!=null)for(File x:f)x.delete();}
}
