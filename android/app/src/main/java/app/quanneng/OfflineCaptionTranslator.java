package app.quanneng;

import android.content.Context;
import io.github.yinvoker.foxlet.Foxlet;
import io.github.yinvoker.foxlet.FoxletConfig;
import io.github.yinvoker.foxlet.InstalledModel;
import io.github.yinvoker.foxlet.LanguagePair;
import io.github.yinvoker.foxlet.PreparePolicy;
import io.github.yinvoker.foxlet.TextFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.Unit;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;

/** English captions to Chinese with a locally installed, hash-verified Mozilla model. */
final class OfflineCaptionTranslator {
    private OfflineCaptionTranslator() {}
    private interface Suspending<T> {Object invoke(Continuation<? super T> continuation);}
    @SuppressWarnings("unchecked")
    private static <T> T await(Suspending<T> operation) throws InterruptedException {
        return (T)BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (Function2<CoroutineScope,Continuation<? super T>,Object>)(scope, continuation)->operation.invoke(continuation));
    }
    static File translate(Context context,File source,TransferRuntime.Token token,TransferRuntime.Progress progress)throws Exception {
        String raw;try(FileInputStream in=new FileInputStream(source)){raw=SubtitleStore.read(in);}
        List<String> stamps=new ArrayList<>(),lines=new ArrayList<>();
        for(String block:raw.split("\\n\\s*\\n")){
            String[] parts=block.split("\\n");int at=-1;
            for(int i=0;i<parts.length;i++)if(parts[i].contains(" --> ")){at=i;break;}
            if(at<0||at+1>=parts.length)continue;
            StringBuilder cue=new StringBuilder();
            for(int i=at+1;i<parts.length;i++){if(cue.length()>0)cue.append(' ');cue.append(parts[i].replaceAll("<[^>]*>","").trim());}
            if(cue.length()==0||cue.length()>1500)continue;
            stamps.add(parts[at]);lines.add(cue.toString());
            if(lines.size()>3000)throw new IOException("字幕片段过多");
        }
        if(lines.isEmpty())throw new IOException("语音识别没有返回可翻译的内容");
        token.check();progress.update(0,"正在准备英译中语言包（首次约 52 MB）");
        Foxlet engine=await(c->Foxlet.Companion.create(context.getApplicationContext(),new FoxletConfig(),c));
        try {
            LanguagePair pair=new LanguagePair("en","zh-Hans");
            Function1<io.github.yinvoker.foxlet.DownloadProgress,Unit> onDownload=p->{
                if(token.cancelled.get())throw new java.util.concurrent.CancellationException("任务已取消");
                long total=p.getTotalBytes();int percent=total>0?(int)Math.min(100,p.getDownloadedBytes()*100/total):0;
                progress.update(percent,"正在下载英译中语言包 · "+percent+"%");return Unit.INSTANCE;
            };
            InstalledModel model=await(c->engine.getModels().prepare(pair,PreparePolicy.LocalOrBundled,null,onDownload,c));
            StringBuilder vtt=new StringBuilder("WEBVTT\n\n");
            for(int at=0;at<lines.size();at+=12){
                token.check();int end=Math.min(at+12,lines.size());final int start=at;
                List<String> batch=await(c->engine.getTranslator().translate(lines.subList(start,end),model,TextFormat.Plain,c));
                if(batch==null||batch.size()!=end-at)throw new IOException("翻译字幕数量不完整");
                for(int i=at;i<end;i++){
                    String text=batch.get(i-at);
                    if(text==null||text.trim().isEmpty())throw new IOException("翻译字幕为空");
                    vtt.append(stamps.get(i)).append('\n').append(text.replace('\n',' ')).append("\n\n");
                }
                progress.update(end*100/lines.size(),"正在翻译视频语音 · "+end+"/"+lines.size());
            }
            token.check();File out=new File(SubtitleStore.root(context),System.currentTimeMillis()+"-zh-语音翻译.vtt");
            Files.write(out.toPath(),SubtitleStore.shift(vtt.toString(),0).getBytes(StandardCharsets.UTF_8));return out;
        }finally{await(c->engine.shutdown(c));}
    }
}
