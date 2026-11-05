package app.quanneng;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.concurrent.*;

/** 用户显式开启的临时局域网共享；只发布选定普通视频的副本，30 分钟自动销毁。 */
public final class LanShareService extends Service {
    static volatile String url="",phase="尚未开启本地共享";
    static volatile long expires;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaShareServer server;
    private volatile long generation;
    private File folder;
    private PowerManager.WakeLock wake;private android.net.wifi.WifiManager.WifiLock wifi;
    static boolean allowed(Uri source){return source!=null&&"content".equals(source.getScheme())&&!(MediaContentProvider.AUTHORITY.equals(source.getAuthority())&&source.getPathSegments().contains("vault-open"));}
    static boolean request(Context context,Uri source){
        if(!PrivateSession.isUnlocked()||!allowed(source))return false;
        url="";phase="正在准备本地视频…";
        try{context.startForegroundService(new Intent(context,LanShareService.class).setData(source));return true;}
        catch(RuntimeException e){phase="无法启动共享，请保持全能在前台重试";return false;}
    }
    static void end(Context c){c.stopService(new Intent(c,LanShareService.class));}
    static File root(Context c){File f=new File(c.getCacheDir(),"lan-share");f.mkdirs();return f;}
    static void cleanAbandoned(Context c){TransferService.deleteFolder(root(c));}
    static String address(Context c)throws IOException{
        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network n=cm.getActiveNetwork();NetworkCapabilities cap=cm.getNetworkCapabilities(n);
        if(cap==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))throw new IOException("请连接同一 Wi-Fi 或有线局域网");
        LinkProperties link=cm.getLinkProperties(n);if(link!=null)for(LinkAddress a:link.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&NetworkPolicy.privateHost(a.getAddress().getHostAddress()))return a.getAddress().getHostAddress();
        throw new IOException("未取得可用的局域网 IPv4 地址");
    }
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("lan-share","临时视频共享",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(){PendingIntent stop=PendingIntent.getService(this,7,new Intent(this,LanShareService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,"lan-share").setSmallIcon(R.drawable.ic_launcher).setContentTitle("全能 · 临时局域网共享").setContentText("30 分钟内有效，可随时停止共享").setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).addAction(new Notification.Action.Builder(R.drawable.ic_launcher,"停止共享",stop).build()).setContentIntent(PendingIntent.getActivity(this,7,new Intent(this,HandoffActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        Uri source=intent.getData();if(!allowed(source)){phase="不能共享这个文件";stopSelf();return START_NOT_STICKY;}
        startForeground(17,notification());final long ticket=++generation;handler.removeCallbacksAndMessages(null);closeSession();
        worker.execute(()->{
            File staging=new File(root(this),java.util.UUID.randomUUID().toString());staging.mkdirs();
            try{String host=address(this),name=LibraryActivity.name(this,source);String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT):"mp4";if(!ext.matches("mp4|mkv|webm|mov|m4v|avi|ts"))ext="mp4";
                File file=new File(staging,"video."+ext);phase="正在复制视频到临时共享空间…";
                try(InputStream in=getContentResolver().openInputStream(source);FileOutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("来源无法读取");byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){if(ticket!=generation||Thread.currentThread().isInterrupted())throw new IOException("共享已取消");out.write(bytes,0,n);}out.getFD().sync();}
                if(file.length()==0)throw new IOException("视频文件为空");byte[] secret=new byte[24];new SecureRandom().nextBytes(secret);String token=android.util.Base64.encodeToString(secret,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);String mime=ext.equals("mkv")?"video/x-matroska":ext.equals("webm")?"video/webm":ext.equals("ts")?"video/mp2t":"video/mp4";
                MediaShareServer ready=new MediaShareServer(host,file,token,ext,mime);long stopAt=SystemClock.elapsedRealtime()+30*60*1000;ready.expireAt(stopAt,SystemClock::elapsedRealtime);ready.start(10000,false);
                synchronized(this){if(ticket!=generation){ready.stop();TransferService.deleteFolder(staging);return;}server=ready;folder=staging;holdNetwork();expires=stopAt;url="http://"+host+":"+ready.getListeningPort()+ready.path();phase="本地视频已可投屏或接力，30 分钟内有效";}
                handler.postDelayed(()->{if(ticket==generation)stopSelf();},30*60*1000);
            }catch(Exception e){TransferService.deleteFolder(staging);if(ticket==generation){phase="共享失败："+(e.getMessage()==null?"请检查网络与手机空间":e.getMessage());stopSelf(startId);}}
        });return START_NOT_STICKY;
    }
    private void holdNetwork(){try{wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"quanneng:media-share");wake.acquire(30*60*1000L);android.net.wifi.WifiManager manager=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(manager!=null){wifi=manager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"quanneng-media-share");wifi.acquire();}}catch(SecurityException ignored){}}
    private synchronized void closeSession(){if(wake!=null&&wake.isHeld())wake.release();wake=null;if(wifi!=null&&wifi.isHeld())wifi.release();wifi=null;url="";expires=0;if(server!=null){server.stop();server=null;}if(folder!=null){TransferService.deleteFolder(folder);folder=null;}}
    @Override public void onTaskRemoved(Intent i){stopSelf();}
    @Override public void onTimeout(int id,int type){stopSelf();}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){++generation;handler.removeCallbacksAndMessages(null);closeSession();worker.shutdownNow();if(!phase.startsWith("共享失败"))phase="本地共享已停止";stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
