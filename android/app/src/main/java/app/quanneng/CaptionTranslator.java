package app.quanneng;

import android.content.Context;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.CancellationSignal;
import android.view.translation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Translate a supplied VTT using an installed Android on-device translation service. */
final class CaptionTranslator {
    private CaptionTranslator(){}
    static File translate(Context context,File source,String from,String to,TransferRuntime.Token token)throws Exception {
        if(Build.VERSION.SDK_INT<31)throw new IOException("原视频只有外语字幕；设备需要 Android 12 及以上的系统翻译服务");
        TranslationManager manager=context.getSystemService(TranslationManager.class);
        if(manager==null)throw new IOException("系统没有提供字幕翻译服务");
        TranslationSpec input=new TranslationSpec(new ULocale(from),TranslationSpec.DATA_FORMAT_TEXT);
        TranslationSpec output=new TranslationSpec(new ULocale(to),TranslationSpec.DATA_FORMAT_TEXT);
        boolean ready=false,downloadable=false;
        for(TranslationCapability c:manager.getOnDeviceTranslationCapabilities(TranslationSpec.DATA_FORMAT_TEXT,TranslationSpec.DATA_FORMAT_TEXT)){
            if(!c.getSourceSpec().getLocale().getLanguage().equals(from)||!c.getTargetSpec().getLocale().getLanguage().equals(to))continue;
            ready|=c.getState()==TranslationCapability.STATE_ON_DEVICE;
            downloadable|=c.getState()==TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD||c.getState()==TranslationCapability.STATE_DOWNLOADING;
        }
        if(!ready)throw new IOException(downloadable?"系统翻译语言包尚未下载，请在系统翻译设置中下载后重试":"系统不支持这组语言的字幕翻译");
        List<String> stamps=new ArrayList<>(),lines=new ArrayList<>();
        String raw;try(InputStream in=new FileInputStream(source)){raw=SubtitleStore.read(in);}
        for(String block:raw.split("\\n\\s*\\n")){
            String[] parts=block.split("\\n");int at=-1;
            for(int i=0;i<parts.length;i++)if(parts[i].contains(" --> ")){at=i;break;}
            if(at<0||at+1>=parts.length)continue;
            StringBuilder cue=new StringBuilder();for(int i=at+1;i<parts.length;i++){if(cue.length()>0)cue.append(' ');cue.append(parts[i].replaceAll("<[^>]*>","").trim());}
            if(cue.length()==0||cue.length()>1500)continue;
            stamps.add(parts[at]);lines.add(cue.toString());
            if(stamps.size()>3000)throw new IOException("字幕片段过多，无法完成翻译");
        }
        if(stamps.isEmpty())throw new IOException("原字幕中没有可翻译的文字");
        TranslationContext settings=new TranslationContext.Builder(input,output).build();
        CountDownLatch created=new CountDownLatch(1);final Translator[] holder=new Translator[1];
        manager.createOnDeviceTranslator(settings,Runnable::run,translator->{holder[0]=translator;created.countDown();});
        if(!created.await(15,TimeUnit.SECONDS)||holder[0]==null)throw new IOException("系统翻译服务启动失败");
        Translator translator=holder[0];
        try {
            StringBuilder result=new StringBuilder("WEBVTT\n\n");
            for(int at=0;at<lines.size();at+=12){
                token.check();int end=Math.min(lines.size(),at+12);List<TranslationRequestValue> values=new ArrayList<>();
                for(int i=at;i<end;i++)values.add(TranslationRequestValue.forText(lines.get(i)));
                TranslationRequest request=new TranslationRequest.Builder().setFlags(TranslationRequest.FLAG_TRANSLATION_RESULT).setTranslationRequestValues(values).build();
                CountDownLatch finished=new CountDownLatch(1);final TranslationResponse[] response=new TranslationResponse[1];
                translator.translate(request,new CancellationSignal(),Runnable::run,value->{response[0]=value;finished.countDown();});
                if(!finished.await(25,TimeUnit.SECONDS)||response[0]==null||response[0].getTranslationStatus()!=TranslationResponse.TRANSLATION_STATUS_SUCCESS)throw new IOException("系统字幕翻译失败");
                for(int i=at;i<end;i++){
                    TranslationResponseValue translated=response[0].getTranslationResponseValues().get(i-at);
                    if(translated==null||translated.getText()==null||translated.getText().length()==0)throw new IOException("系统没有返回完整的翻译字幕");
                    result.append(stamps.get(i)).append('\n').append(translated.getText().toString().replace('\n',' ')).append("\n\n");
                }
            }
            token.check();File out=new File(SubtitleStore.root(context),System.currentTimeMillis()+"-"+to+"-翻译字幕.vtt");
            Files.write(out.toPath(),SubtitleStore.shift(result.toString(),0).getBytes(StandardCharsets.UTF_8));return out;
        }finally{translator.destroy();}
    }
}
