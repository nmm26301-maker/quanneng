package app.quanneng;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** 前台视频任务：锁屏/后台继续处理，通知不显示网址、文件名或账号信息。 */
public final class TransferService extends Service {
    static volatile String phase="尚无进行中的任务",failure="";
    static volatile int percent=-1;
    static volatile boolean running;
    private static volatile TransferRuntime.Token token;
    private static final Object MUTEX=new Object();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TransferRuntime.Token ownedToken;
    static boolean start(Context context,String mode,String value){
        synchronized(MUTEX){if(running)return false;running=true;failure="";phase="正在准备视频组件…";percent=-1;token=new TransferRuntime.Token();}
        try{Intent intent=new Intent(context,TransferService.class).putExtra("mode",mode).putExtra("value",value);context.startForegroundService(intent);return true;}catch(RuntimeException error){running=false;failure="系统未能启动视频任务，请保持应用在前台重试";return false;}
    }
    static void cancel(){TransferRuntime.Token current=token;if(current!=null){phase="正在取消…";new Thread(current::cancel,"quanneng-cancel").start();}}
    static File root(Context context){File folder=new File(context.getFilesDir(),"video-jobs");folder.mkdirs();return folder;}
    static List<File> completed(Context context){
        ArrayList<File> result=new ArrayList<>();File[] jobs=root(context).listFiles();if(jobs==null)return result;
        for(File folder:jobs){File marker=new File(folder,".complete");if(!marker.isFile())continue;try{String name=new String(java.nio.file.Files.readAllBytes(marker.toPath()),"UTF-8").trim();File file=new File(folder,name);if(file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile())&&file.isFile()&&file.length()>0)result.add(file);}catch(IOException ignored){}}
        result.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));return result;
    }
    @Override public void onCreate(){super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("video-tasks","视频处理任务",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(){return new Notification.Builder(this,"video-tasks").setSmallIcon(R.drawable.ic_launcher).setContentTitle("全能").setContentText("正在处理视频，返回应用查看进度").setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,TransferActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(9,notification());String mode=intent==null?"":intent.getStringExtra("mode"),value=intent==null?"":intent.getStringExtra("value");final TransferRuntime.Token current=token;
        if(current==null){stopSelf();return START_NOT_STICKY;}ownedToken=current;
        worker.execute(()->{
            File folder=new File(root(this),current.id);folder.mkdirs();boolean success=false;
            try{
                File result;
                if("download".equals(mode))result=TransferRuntime.download(this,value,folder,current,(p,s)->{percent=p;phase=s;});
                else if("convert".equals(mode)){
                    File source=new File(folder,"input.bin");phase="正在读取所选视频…";
                    try(InputStream input=getContentResolver().openInputStream(Uri.parse(value));OutputStream output=new FileOutputStream(source)){if(input==null)throw new IOException("来源无法读取");byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1){current.check();output.write(block,0,n);}}
                    result=TransferRuntime.convert(this,source,folder,current,(p,s)->{percent=p;phase=s;});source.delete();
                }else throw new IOException("不支持的任务类型");
                current.check();java.nio.file.Files.write(new File(folder,".complete").toPath(),result.getName().getBytes("UTF-8"));percent=100;phase="已完成，可播放或保存到手机";success=true;
            }catch(Exception error){failure=current.cancelled.get()?"任务已取消":"download".equals(mode)?"下载失败：此链接可能不受支持、需登录或来源受限。可在原网页尝试播放。":"转换失败：文件无法读取、已损坏或使用了加密格式。";phase=failure;}
            finally{if(!success)deleteFolder(folder);synchronized(MUTEX){if(token==current){running=false;token=null;}}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}
        });return START_NOT_STICKY;
    }
    static void deleteFolder(File folder){File[] files=folder.listFiles();if(files!=null)for(File file:files){if(file.isDirectory())deleteFolder(file);else file.delete();}folder.delete();}
    @Override public void onTimeout(int startId,int type){failure="系统已暂停长时间任务，请回到应用重试";cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){synchronized(MUTEX){if(running&&token==ownedToken&&token!=null){TransferRuntime.Token previous=token;new Thread(previous::cancel,"quanneng-stop").start();running=false;token=null;}}worker.shutdownNow();super.onDestroy();}
}
