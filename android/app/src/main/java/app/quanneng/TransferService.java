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
    private static TaskStore.Job activeJob;
    private static boolean recovered;
    static synchronized void recover(Context c){if(!recovered&&!running){TaskStore.recover(c);recovered=true;}}
    static boolean start(Context context,String mode,String value){recover(context);try{TaskStore.add(context,mode,value);return kick(context);}catch(IOException error){failure=error.getMessage();return false;}}
    static boolean kick(Context c){synchronized(MUTEX){if(running)return true;running=true;failure="";phase="正在准备视频组件…";percent=-1;}try{c.startForegroundService(new Intent(c,TransferService.class));return true;}catch(RuntimeException error){running=false;failure="系统未能启动任务，请保持应用在前台重试";return false;}}
    static void cancel(){pause();}
    static void pause(){TransferRuntime.Token current=token;if(current!=null){phase="正在暂停…";new Thread(current::cancel,"quanneng-pause").start();}}
    static void update(Context c,TaskStore.Job job,String state){synchronized(MUTEX){TaskStore.Job latest=null;for(TaskStore.Job item:TaskStore.list(c))if(item.id.equals(job.id))latest=item;if(latest==null||"done".equals(latest.state))return;if(activeJob!=null&&activeJob.id.equals(job.id)){if("paused".equals(state))pause();return;}job.state=state;job.error="";try{TaskStore.save(c,job);}catch(IOException e){failure=e.getMessage();return;}}if("queued".equals(state))kick(c);}
    static File root(Context context){File folder=new File(context.getFilesDir(),"video-jobs");folder.mkdirs();return folder;}
    static List<File> completed(Context context){
        ArrayList<File> result=new ArrayList<>();File[] jobs=root(context).listFiles();if(jobs==null)return result;
        for(File folder:jobs){File marker=new File(folder,".complete");if(!marker.isFile())continue;try{String name=new String(java.nio.file.Files.readAllBytes(marker.toPath()),"UTF-8").trim();File file=new File(folder,name);if(file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile())&&file.isFile()&&file.length()>0)result.add(file);}catch(IOException ignored){}}
        result.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));return result;
    }
    @Override public void onCreate(){super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("video-tasks","视频处理任务",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(){return new Notification.Builder(this,"video-tasks").setSmallIcon(R.drawable.ic_launcher).setContentTitle("全能").setContentText("正在处理视频，返回应用查看进度").setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,TransferActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(9,notification());worker.execute(()->{try{while(!Thread.currentThread().isInterrupted()){
            TaskStore.Job job=null;synchronized(MUTEX){for(TaskStore.Job item:TaskStore.list(this))if("queued".equals(item.state)){job=item;break;}if(job==null){running=false;activeJob=null;token=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);return;}activeJob=job;token=new TransferRuntime.Token();ownedToken=token;job.state="running";TaskStore.save(this,job);}execute(job,ownedToken);
        }}catch(Exception e){failure="任务队列异常，请重新打开后继续";synchronized(MUTEX){running=false;activeJob=null;token=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}}});return START_NOT_STICKY;
    }
    private void execute(TaskStore.Job job,TransferRuntime.Token current){
        File folder=TaskStore.folder(this,job);folder.mkdirs();failure="";percent=-1;TransferRuntime.Progress report=(p,s)->{percent=p;phase=s;job.percent=p;};
        try{File result;
            if("download".equals(job.mode))result=TransferRuntime.download(this,job.value,folder,current,report);
            else {org.json.JSONObject options=job.value.startsWith("{")?new org.json.JSONObject(job.value):new org.json.JSONObject().put("uri",job.value);File source=new File(folder,"input.bin");phase="正在读取所选视频…";
                try(InputStream input=getContentResolver().openInputStream(Uri.parse(options.getString("uri")));OutputStream output=new FileOutputStream(source)){if(input==null)throw new IOException("来源无法读取");byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1){current.check();output.write(block,0,n);}}
                result=TransferRuntime.tool(this,source,folder,job.mode,options,current,report);source.delete();}
            current.check();java.nio.file.Files.write(new File(folder,".complete").toPath(),result.getName().getBytes("UTF-8"));percent=100;job.percent=100;job.state="done";job.error="";phase="已完成，可播放或保存到手机";
        }catch(Exception error){job.state=current.cancelled.get()?"paused":"failed";job.error=current.cancelled.get()?"已暂停，可继续下载；转换任务会重新开始":"download".equals(job.mode)?"下载失败：来源不支持、需要登录、受限或网络中断":"处理失败：请检查源文件、时间范围和手机空间";failure=job.error;phase=failure;new File(folder,"input.bin").delete();}
        finally{try{TaskStore.save(this,job);}catch(IOException e){failure=e.getMessage();}synchronized(MUTEX){activeJob=null;token=null;}}
    }
    static void deleteFolder(File folder){File[] files=folder.listFiles();if(files!=null)for(File file:files){if(file.isDirectory())deleteFolder(file);else file.delete();}folder.delete();}
    @Override public void onTimeout(int startId,int type){failure="系统已暂停长时间任务，请回到应用重试";cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){synchronized(MUTEX){if(running&&token==ownedToken&&token!=null){TransferRuntime.Token previous=token;new Thread(previous::cancel,"quanneng-stop").start();running=false;token=null;}}worker.shutdownNow();super.onDestroy();}
}
