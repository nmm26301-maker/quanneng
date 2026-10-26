package app.quanneng;

import android.content.Context;
import com.yausername.youtubedl_android.*;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 使用发布版 yt-dlp / FFmpeg 运行时；不接受原始命令、解密参数或 shell 片段。 */
final class TransferRuntime {
    interface Progress {void update(int percent,String phase);}
    static final class Token {
        final String id=UUID.randomUUID().toString();final AtomicBoolean cancelled=new AtomicBoolean();volatile Process nativeProcess;
        void cancel(){cancelled.set(true);YoutubeDL.getInstance().destroyProcessById(id);Process process=nativeProcess;if(process!=null)process.destroyForcibly();}
        void check()throws IOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new IOException("任务已取消");}
    }
    static synchronized void initialize(Context context)throws Exception{YoutubeDL.getInstance().init(context);FFmpeg.getInstance().init(context);}
    static File download(Context context,String url,File folder,Token token,Progress progress)throws Exception{
        if(!MainActivity.isHttps(url)||url.length()>8192)throw new IllegalArgumentException("请填写完整 HTTPS 视频页面地址");
        initialize(context);token.check();folder.mkdirs();
        YoutubeDLRequest request=new YoutubeDLRequest(url);
        request.addOption("--continue");request.addOption("--ignore-config");request.addOption("--no-playlist");request.addOption("--progress");request.addOption("--newline");request.addOption("--no-mtime");
        request.addOption("--socket-timeout",20);request.addOption("--retries",3);request.addOption("--fragment-retries",3);request.addOption("--concurrent-fragments",4);
        request.addOption("-f","bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b");request.addOption("--merge-output-format","mp4");
        request.addOption("-o",new File(folder,"video.%(ext)s").getAbsolutePath());
        request.addOption("--no-warnings");request.addOption("--no-allow-unplayable-formats");
        progress.update(-1,"正在识别视频站点…");
        YoutubeDL.getInstance().execute(request,token.id,(percent,eta,line)->{if(token.cancelled.get())YoutubeDL.getInstance().destroyProcessById(token.id);progress.update(Math.max(0,Math.min(99,Math.round(percent))),"正在下载视频…");return Unit.INSTANCE;});
        token.check();File[] files=folder.listFiles((dir,name)->name.matches("video\\.(mp4|mkv|webm|mov|m4v|flv|avi|ts)"));
        if(files==null||files.length==0)throw new IOException("站点未返回可保存的视频文件");
        Arrays.sort(files,Comparator.comparing(File::getName));File result=files[0];if(result.length()==0)throw new IOException("下载文件为空");return result;
    }
    static File convert(Context context,File input,File folder,Token token,Progress progress)throws Exception{
        initialize(context);token.check();folder.mkdirs();File output=new File(folder,"result.mp4");
        progress.update(-1,"正在转换为兼容 MP4…");
        runFFmpeg(context,Arrays.asList("-nostdin","-y","-protocol_whitelist","file,pipe","-i",input.getAbsolutePath(),"-map","0:v:0","-map","0:a:0?","-c:v","libx264","-preset","veryfast","-crf","23","-c:a","aac","-b:a","160k","-movflags","+faststart","-progress","pipe:1","-nostats",output.getAbsolutePath()),token,progress);
        if(!output.isFile()||output.length()==0)throw new IOException("未生成视频，请确认文件是可读取的普通视频");return output;
    }
    static File tool(Context c,File input,File folder,String mode,org.json.JSONObject o,Token token,Progress progress)throws Exception{
        if("convert".equals(mode))return convert(c,input,folder,token,progress);
        File out=new File(folder,"result."+("audio".equals(mode)?"m4a":"snapshot".equals(mode)?"jpg":"mp4"));List<String> a=new ArrayList<>(Arrays.asList("-nostdin","-y","-protocol_whitelist","file,pipe"));double start=o.optDouble("start",0),end=o.optDouble("end",0);
        if(!Double.isFinite(start)||start<0||start>604800||!Double.isFinite(end)||end<0||end>604800)throw new IOException("时间超出范围");
        if("trim".equals(mode)||"snapshot".equals(mode))a.addAll(Arrays.asList("-ss",String.valueOf(start)));a.addAll(Arrays.asList("-i",input.getAbsolutePath()));
        if("audio".equals(mode))a.addAll(Arrays.asList("-vn","-c:a","aac","-b:a","160k"));
        else if("snapshot".equals(mode))a.addAll(Arrays.asList("-frames:v","1","-q:v","2"));
        else if("mute".equals(mode))a.addAll(Arrays.asList("-map","0:v:0","-c:v","copy","-an","-movflags","+faststart"));
        else if("compress".equals(mode)||"trim".equals(mode)){if("trim".equals(mode)){if(end<=start)throw new IOException("终点需要大于起点");a.addAll(Arrays.asList("-t",String.valueOf(end-start)));}a.addAll(Arrays.asList("-map","0:v:0","-map","0:a:0?","-c:v","libx264","-preset","veryfast","-crf","compress".equals(mode)?"30":"23","-vf","scale=trunc(iw/2)*2:trunc(ih/2)*2","-c:a","aac","-movflags","+faststart"));}
        else throw new IOException("未知操作");
        a.addAll(Arrays.asList("-progress","pipe:1","-nostats",out.getAbsolutePath()));progress.update(-1,"正在处理视频…");runFFmpeg(c,a,token,progress);if(!out.isFile()||out.length()==0)throw new IOException("未生成文件");return out;
    }
    static String runFFmpeg(Context context,List<String> arguments,Token token,Progress progress)throws Exception{
        initialize(context);ArrayList<String> command=new ArrayList<>();command.add(new File(context.getApplicationInfo().nativeLibraryDir,"libffmpeg.so").getAbsolutePath());command.addAll(arguments);
        ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true);File base=new File(context.getNoBackupFilesDir(),"youtubedl-android/packages");
        builder.environment().put("LD_LIBRARY_PATH",new File(base,"ffmpeg/usr/lib").getAbsolutePath()+":"+new File(base,"python/usr/lib").getAbsolutePath());builder.environment().put("TMPDIR",context.getCacheDir().getAbsolutePath());
        Process process=builder.start();token.nativeProcess=process;StringBuilder tail=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){String line;while((line=reader.readLine())!=null){token.check();if(tail.length()>12000)tail.delete(0,6000);tail.append(line).append('\n');if(line.startsWith("out_time="))progress.update(-1,"正在转换 · "+line.substring(9));}}
        finally{if(token.cancelled.get())process.destroyForcibly();}
        int exit=process.waitFor();token.nativeProcess=null;token.check();if(exit!=0)throw new IOException("转换失败，请确认视频未加密且文件完整");return tail.toString();
    }
}
