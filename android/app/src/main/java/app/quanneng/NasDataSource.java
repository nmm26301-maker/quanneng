package app.quanneng;
import android.net.Uri;
import androidx.media3.datasource.*;
import androidx.media3.common.C;
import java.io.*;
import java.net.*;
import java.util.*;
/** NAS 认证限定到选定源，拒绝重定向和跨源分片，支持 Range 与 seek。 */
final class NasDataSource extends BaseDataSource {
 private final URL origin;private final String auth;private HttpURLConnection connection;private InputStream input;private Uri uri;private long remaining;private boolean opened;
 NasDataSource(URL origin,String auth){super(true);this.origin=origin;this.auth=auth;}
 @Override public long open(DataSpec spec)throws IOException{transferInitializing(spec);URL u=NetworkPolicy.url(spec.uri.toString());if(!NetworkPolicy.sameOrigin(origin,u))throw new IOException("NAS 媒体不能跨源发送认证");uri=spec.uri;connection=(HttpURLConnection)u.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setRequestProperty("Authorization",auth);connection.setRequestProperty("Accept-Encoding","identity");if(spec.position>0||spec.length!=C.LENGTH_UNSET)connection.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":spec.position+spec.length-1));try{int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("NAS 返回 HTTP "+status+"，请检查地址或登录");input=connection.getInputStream();long length=connection.getContentLengthLong();if(status==200&&spec.position>0){long skipped=0;byte[] b=new byte[8192];while(skipped<spec.position){int n=input.read(b,0,(int)Math.min(b.length,spec.position-skipped));if(n<0)throw new EOFException();skipped+=n;}if(length>=0)length-=spec.position;}remaining=spec.length==C.LENGTH_UNSET?length:spec.length;opened=true;transferStarted(spec);return remaining;}catch(IOException e){close();throw e;}}
 @Override public int read(byte[] buffer,int offset,int length)throws IOException{if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;int n=input.read(buffer,offset,remaining<0?length:(int)Math.min(length,remaining));if(n<0)return C.RESULT_END_OF_INPUT;if(remaining>0)remaining-=n;bytesTransferred(n);return n;}
 @Override public Uri getUri(){return uri;}
 @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
 @Override public void close()throws IOException{try{if(input!=null)input.close();}finally{input=null;if(connection!=null)connection.disconnect();connection=null;if(opened){opened=false;transferEnded();}}}
}
