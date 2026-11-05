package app.quanneng;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
/** 主机回归只读媒体端点的范围、会话、来源和撤销边界。 */
public final class MediaRangeCheck {
 private static void check(boolean b){if(!b)throw new AssertionError();}
 private static NanoHTTPD.IHTTPSession session(String path,String peer,String range,NanoHTTPD.Method method){Map<String,String> headers=new HashMap<>();if(range!=null)headers.put("range",range);return (NanoHTTPD.IHTTPSession)Proxy.newProxyInstance(MediaRangeCheck.class.getClassLoader(),new Class<?>[]{NanoHTTPD.IHTTPSession.class},(p,m,a)->{switch(m.getName()){case "getUri":return path;case "getRemoteIpAddress":return peer;case "getMethod":return method;case "getHeaders":return headers;default:return null;}});}
 public static void main(String[] args)throws Exception{
  check(Arrays.equals(MediaShareServer.range(null,100),new long[]{0,99}));check(Arrays.equals(MediaShareServer.range("bytes=10-19",100),new long[]{10,19}));check(Arrays.equals(MediaShareServer.range("bytes=98-",100),new long[]{98,99}));check(Arrays.equals(MediaShareServer.range("bytes=-8",100),new long[]{92,99}));check(Arrays.equals(MediaShareServer.range("bytes=10-999",100),new long[]{10,99}));
  for(String invalid:new String[]{"bytes=100-","bytes=2-1","bytes=0-1,3-5","bytes=-0","bytes=-1-5","bytes=NaN-","bytes=9223372036854775808-"})check(MediaShareServer.range(invalid,100)==null);
  check(!NetworkPolicy.privateHost("010.0.0.1"));check(!NetworkPolicy.privateHost("192.168.001.2"));
  File file=File.createTempFile("quanneng-range-",".mp4");try(FileOutputStream out=new FileOutputStream(file)){for(int i=0;i<100;i++)out.write(i);}
  MediaShareServer server=new MediaShareServer("127.0.0.1",file,"private-token","mp4","video/mp4");AtomicLong time=new AtomicLong(9);server.expireAt(10,time::get);String path=server.path();
  try(NanoHTTPD.Response response=server.serve(session(path,"192.168.1.2","bytes=7-8",NanoHTTPD.Method.GET))){check(response.getStatus().getRequestStatus()==206);check(response.getData().read()==7);}
  try(NanoHTTPD.Response response=server.serve(session(path,"8.8.8.8",null,NanoHTTPD.Method.GET))){check(response.getStatus().getRequestStatus()==403);}
  try(NanoHTTPD.Response response=server.serve(session(path+"bad","192.168.1.2",null,NanoHTTPD.Method.GET))){check(response.getStatus().getRequestStatus()==404);}
  try(NanoHTTPD.Response response=server.serve(session(path,"192.168.1.2",null,NanoHTTPD.Method.POST))){check(response.getStatus().getRequestStatus()==405);}
  time.set(10);try(NanoHTTPD.Response response=server.serve(session(path,"192.168.1.2",null,NanoHTTPD.Method.GET))){check(response.getStatus().getRequestStatus()==410);}
  server.stop();file.delete();System.out.println("MEDIA_RANGE_OK: bounded ranges, token, LAN source, method, expiration and stop");
 }
}
