package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real HTTP, production source factory and decoder: signed extensionless / wrong MIME endpoints. */
public final class FormatSmoke extends Instrumentation {
    private ServerSocket server;private ExecutorService workers=Executors.newCachedThreadPool();private String base;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        PrivateTestAccess.install(this);Bundle result=new Bundle();
        try{
            server=new ServerSocket(0);String host=null;
            for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&a.isSiteLocalAddress())host=a.getHostAddress();
            if(host==null)throw new AssertionError("No private interface for local HTTP fixture");base="http://"+host+":"+server.getLocalPort();
            workers.submit(()->{while(!server.isClosed())try{Socket client=server.accept();workers.submit(()->serve(client));}catch(IOException ignored){}});
            for(String path:new String[]{"/watch?kind=hls","/watch?kind=dash","/wrong.m3u8","/no-range"}){
                Activity a=open(path);long deadline=SystemClock.elapsedRealtime()+30000;boolean decoded=false;
                while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> good=new AtomicReference<>(false);runOnMainSync(()->{try{Object media=field(a,"media");Object video=a.findViewById(1000);good.set(media!=null&&((Number)media.getClass().getMethod("getVideoWidth").invoke(media)).intValue()>0&&((Number)video.getClass().getMethod("getCurrentPosition").invoke(video)).intValue()>200);}catch(Exception ignored){}});if(good.get()){decoded=true;break;}SystemClock.sleep(100);}
                runOnMainSync(a::finish);if(!decoded)throw new AssertionError("No decoded frames for "+path);report("Decoded actual HTTP: "+path);
            }
            Activity html=open("/html.mp4");long deadline=SystemClock.elapsedRealtime()+15000;boolean rejected=false;
            while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> good=new AtomicReference<>(false);runOnMainSync(()->{try{good.set((Boolean)field(html,"failed")&&String.valueOf(html.findViewById(1000).getClass().getMethod("failureDetail").invoke(html.findViewById(1000))).contains("网页"));}catch(Exception ignored){}});if(good.get()){rejected=true;break;}SystemClock.sleep(100);}
            runOnMainSync(html::finish);if(!rejected)throw new AssertionError("HTML masquerading as MP4 was not diagnosed");
            result.putString("stream","QUANNENG_FORMAT_OK\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","QUANNENG_FORMAT_FAILED: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{try{if(server!=null)server.close();}catch(IOException ignored){}workers.shutdownNow();}
    }
    private void serve(Socket socket){try(Socket client=socket){
        client.setSoTimeout(8000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));String first=in.readLine();if(first==null)return;String path=first.split(" ")[1],range=null,line;
        while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();
        byte[] data;String type="application/octet-stream";
        if(path.contains("kind=hls")){data=("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:18\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:18.0,\n/episode.ts\n#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8);type="video/mp4";}
        else if(path.equals("/html.mp4")){data="<!doctype html><html><body>Source login page</body></html>".getBytes(StandardCharsets.UTF_8);type="video/mp4";}
        else{
            String asset=path.contains("kind=dash")?"format/episode.mpd":path.equals("/episode.ts")?"format/episode.ts":path.contains(".m4s")?"format/"+path.substring(1):"buffer-fixture.mp4";
            try(InputStream source=getContext().getAssets().open(asset);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[8192];int count;while((count=source.read(b))!=-1)bytes.write(b,0,count);data=bytes.toByteArray();}
        }
        OutputStream out=client.getOutputStream();
        if(path.equals("/no-range")&&"bytes=0-8191".equals(range)){out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));return;}
        int start=0,end=data.length-1;boolean partial=range!=null&&range.startsWith("bytes=");
        if(partial){String[] bounds=range.substring(6).split("-",-1);start=Integer.parseInt(bounds[0]);if(bounds.length>1&&!bounds[1].isEmpty())end=Math.min(end,Integer.parseInt(bounds[1]));}
        if(start> end)throw new IOException("Bad range");String headers="HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+(end-start+1)+"\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+data.length+"\r\n":"")+"Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(data,start,end-start+1);out.flush();
    }catch(Exception ignored){}}
    private Activity open(String path)throws Exception{Intent i=new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.SystemPlayerActivity")).setData(Uri.parse(base+path)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity a=startActivitySync(i);PrivateTestAccess.awaitUnlocked(this,a);return a;}
    private Object field(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void report(String value){Bundle b=new Bundle();b.putString("stream",value+"\n");sendStatus(0,b);}
}
