package app.quanneng;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Whisper.cpp speech recognition from the actual video audio, with bounded temporary audio. */
final class SpeechSubtitle {
    private static final String MODEL="ggml-base-q5_1.bin";
    private static final String SHA="422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898";
    private static final int CHUNK_SECONDS=60;
    private SpeechSubtitle(){}
    static File transcribe(Context context,Uri video,Map<String,String> headers,int durationMs,TransferRuntime.Token token,TransferRuntime.Progress progress)throws Exception {
        if(durationMs<=0||durationMs>3*60*60*1000)throw new IOException("语音识别需要时长在 3 小时以内的视频");
        File binary=new File(context.getApplicationInfo().nativeLibraryDir,"libwhispercli.so");
        if(!binary.isFile())throw new IOException("安装包缺少语音识别引擎");
        File model=model(context,token,progress),work=new File(context.getCacheDir(),"speech-captions/"+UUID.randomUUID());
        if(!work.mkdirs())throw new IOException("无法创建语音识别临时目录");
        try{
            String source=source(context,video,work,token);StringBuilder captions=new StringBuilder("WEBVTT\n\n");
            int seconds=(durationMs+999)/1000;
            for(int start=0;start<seconds;start+=CHUNK_SECONDS){
                token.check();int length=Math.min(CHUNK_SECONDS,seconds-start);
                progress.update(start*100/seconds,"提取音轨 · "+(start/60+1)+" / "+((seconds+59)/60));
                File wav=new File(work,"audio.wav");ArrayList<String> args=new ArrayList<>(Arrays.asList("-nostdin","-y","-protocol_whitelist","file,pipe,http,https,tcp,tls,crypto"));
                if(video.getScheme().startsWith("http")){StringBuilder h=new StringBuilder();for(Map.Entry<String,String> e:PlaybackHeaders.sanitize(headers).entrySet())h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");if(h.length()>0)args.addAll(Arrays.asList("-headers",h.toString()));}
                args.addAll(Arrays.asList("-ss",String.valueOf(start),"-i",source,"-t",String.valueOf(length),"-vn","-ar","16000","-ac","1","-c:a","pcm_s16le",wav.getAbsolutePath()));
                TransferRuntime.runFFmpeg(context,args,token,(pct,phase)->{});
                if(!wav.isFile()||wav.length()<48)throw new IOException("视频没有可识别的音轨");
                File output=new File(work,"speech");
                progress.update(start*100/seconds,"本机识别语音 · "+(start/60+1)+" / "+((seconds+59)/60));
                run(Arrays.asList(binary.getAbsolutePath(),"-m",model.getAbsolutePath(),"-f",wav.getAbsolutePath(),"-l","auto","-tr","-ovtt","-of",output.getAbsolutePath(),"-t","4","-ng","-np"),token);
                File vtt=new File(work,"speech.vtt");if(!vtt.isFile())throw new IOException("语音识别没有生成字幕");
                String segment;try(InputStream in=new FileInputStream(vtt)){segment=SubtitleStore.read(in);}
                if(SubtitleStore.TIMES.matcher(segment).find()){String shifted=SubtitleStore.shift(segment,start*1000L);captions.append(shifted.replaceFirst("^WEBVTT\\s*","")).append("\n\n");}
                wav.delete();vtt.delete();
            }
            token.check();File result=new File(SubtitleStore.root(context),System.currentTimeMillis()+"-en-语音字幕.vtt");
            Files.write(result.toPath(),SubtitleStore.shift(captions.toString(),0).getBytes(StandardCharsets.UTF_8));
            progress.update(100,"语音识别完成");return result;
        }finally{TransferService.deleteFolder(work);}
    }
    private static String source(Context context,Uri uri,File work,TransferRuntime.Token token)throws Exception {
        if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))return uri.toString();
        if(!"content".equals(uri.getScheme()))throw new IOException("此视频来源无法提取音轨");
        File copied=new File(work,"source.video");long bytes=0;
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(copied)){
            if(in==null)throw new IOException("无法读取本地视频");byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1){token.check();bytes+=n;if(bytes>2L*1024*1024*1024)throw new IOException("本地视频超过 2 GB");out.write(buffer,0,n);}
        }
        return copied.getAbsolutePath();
    }
    private static File model(Context context,TransferRuntime.Token token,TransferRuntime.Progress progress)throws Exception {
        File root=new File(context.getNoBackupFilesDir(),"speech-model");root.mkdirs();File installed=new File(root,MODEL);
        if(installed.isFile()&&SHA.equals(sha(installed,token)))return installed;
        File temporary=new File(root,MODEL+".partial");progress.update(-1,"准备本机语音模型…");
        try(InputStream in=context.getAssets().open("models/"+MODEL);OutputStream out=new FileOutputStream(temporary)){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){token.check();out.write(b,0,n);}
        }
        if(!SHA.equals(sha(temporary,token)))throw new IOException("语音模型校验失败，请重新安装应用");
        if(!temporary.renameTo(installed))throw new IOException("无法安装语音模型");return installed;
    }
    private static String sha(File file,TransferRuntime.Token token)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){token.check();digest.update(b,0,n);}
        }
        StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    private static void run(List<String> args,TransferRuntime.Token token)throws Exception {
        Process process=new ProcessBuilder(args).redirectErrorStream(true).start();token.nativeProcess=process;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){while(in.readLine()!=null)token.check();}
        finally{if(token.cancelled.get())process.destroyForcibly();}
        int result=process.waitFor();token.nativeProcess=null;token.check();if(result!=0)throw new IOException("本机语音识别失败，请尝试较短的视频");
    }
}
