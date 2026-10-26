package app.quanneng;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** 单文件临时媒体端点：固定路径令牌、私网来源、Range/HEAD、有限连接，不提供目录或上传。 */
final class MediaShareServer extends NanoHTTPD {
    private final File media;
    private final String path,mime;
    private volatile boolean valid=true;
    private long until=Long.MAX_VALUE;private java.util.function.LongSupplier clock=System::currentTimeMillis;
    private final Set<ClientHandler> clients=Collections.synchronizedSet(new HashSet<>());
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(4,4,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4));

    MediaShareServer(String host,File file,String token,String extension,String type){
        super(host,0);media=file;path="/"+token+"/video."+extension;mime=type;
        setAsyncRunner(new AsyncRunner(){
            public void closeAll(){synchronized(clients){for(ClientHandler c:clients)c.close();clients.clear();}pool.shutdownNow();}
            public void closed(ClientHandler c){clients.remove(c);}
            public void exec(ClientHandler c){clients.add(c);try{pool.execute(c);}catch(RejectedExecutionException e){clients.remove(c);c.close();}}
        });
    }
    String path(){return path;}
    void expireAt(long deadline,java.util.function.LongSupplier now){until=deadline;clock=now;}
    @Override public Response serve(IHTTPSession session){
        if(!valid||clock.getAsLong()>=until)return error(Response.Status.GONE,"共享已停止");
        if(!NetworkPolicy.privateHost(session.getRemoteIpAddress()))return error(Response.Status.FORBIDDEN,"仅局域网访问");
        if(!MessageDigest.isEqual(path.getBytes(StandardCharsets.UTF_8),session.getUri().getBytes(StandardCharsets.UTF_8)))return error(Response.Status.NOT_FOUND,"无效的共享链接");
        if(session.getMethod()!=Method.GET&&session.getMethod()!=Method.HEAD){Response r=error(Response.Status.METHOD_NOT_ALLOWED,"仅支持读取");r.addHeader("Allow","GET, HEAD");return r;}
        long length=media.length();
        try{
            long[] span=range(session.getHeaders().get("range"),length);
            if(span==null){Response r=error(Response.Status.RANGE_NOT_SATISFIABLE,"无效字节范围");r.addHeader("Content-Range","bytes */"+length);return r;}
            boolean partial=session.getHeaders().containsKey("range");
            FileInputStream in=new FileInputStream(media);in.getChannel().position(span[0]);
            Response r=newFixedLengthResponse(partial?Response.Status.PARTIAL_CONTENT:Response.Status.OK,mime,in,span[1]-span[0]+1);
            r.addHeader("Accept-Ranges","bytes");r.addHeader("Cache-Control","no-store");r.addHeader("X-Content-Type-Options","nosniff");r.addHeader("transferMode.dlna.org","Streaming");
            if(partial)r.addHeader("Content-Range","bytes "+span[0]+"-"+span[1]+"/"+length);
            return r;
        }catch(IOException e){return error(Response.Status.INTERNAL_ERROR,"文件无法读取");}
    }
    private Response error(Response.Status code,String message){Response r=newFixedLengthResponse(code,"text/plain; charset=utf-8",message);r.addHeader("Cache-Control","no-store");return r;}
    static long[] range(String header,long length){
        if(length<=0)return null;
        if(header==null)return new long[]{0,length-1};
        if(header.length()>100||!header.matches("bytes=(?:[0-9]+-[0-9]*|-[0-9]+)"))return null;
        String[] parts=header.substring(6).split("-",-1);
        try{long start,end;
            if(parts[0].isEmpty()){long suffix=Long.parseLong(parts[1]);if(suffix<=0)return null;start=Math.max(0,length-suffix);end=length-1;}
            else{start=Long.parseLong(parts[0]);end=parts[1].isEmpty()?length-1:Math.min(length-1,Long.parseLong(parts[1]));}
            return start>=length||start>end?null:new long[]{start,end};
        }catch(NumberFormatException e){return null;}
    }
    @Override public void stop(){valid=false;super.stop();}
}
