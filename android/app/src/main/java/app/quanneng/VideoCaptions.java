package app.quanneng;

import android.content.Context;
import android.net.Uri;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import kotlin.Unit;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Captions supplied by the source site, including its generated/translated tracks. */
final class VideoCaptions {
    static final String[] LANGUAGES={"zh","en","ja","ko","es","fr","de","ru","pt","ar","hi","it"};
    static final String[] LABELS={"中文","English","日本語","한국어","Español","Français","Deutsch","Русский","Português","العربية","हिन्दी","Italiano"};
    static final class Track {final File file;final String language;Track(File f,String l){file=f;language=l;}}
    private VideoCaptions(){}
    static String sourcePage(String page,String fallback){if(MainActivity.isHttps(page))return page;return MainActivity.isHttps(fallback)?fallback:null;}
    static Track fetch(Context context,String page,String target,TransferRuntime.Token token)throws Exception {
        if(!MainActivity.isHttps(page)||page.length()>8192)throw new IOException("缺少可查询字幕的视频网页链接");
        if(!Arrays.asList(LANGUAGES).contains(target))throw new IOException("不支持的目标语言");
        File folder=new File(context.getCacheDir(),"video-captions/"+UUID.randomUUID());
        if(!folder.mkdirs())throw new IOException("无法创建字幕临时目录");
        try {
            TransferRuntime.initialize(context);token.check();
            // Ask the site for the desired language first. On YouTube this includes auto-translation.
            File found=download(page,target+".*",folder,token);
            String language=target;
            if(found==null){
                StringBuilder originals=new StringBuilder();for(String lang:LANGUAGES)if(!lang.equals(target)){if(originals.length()>0)originals.append(',');originals.append(lang).append("(?:[-_].*)?");}
                found=download(page,originals.toString(),folder,token);
                if(found!=null)language=trackLanguage(found.getName());
            }
            if(found==null)throw new IOException("这个视频没有可获取的字幕或自动字幕；纯音频无法从网站字幕翻译");
            if(language==null)throw new IOException("无法确定原字幕语言");
            String contents;try(InputStream input=new FileInputStream(found)){contents=SubtitleStore.read(input);}
            File saved=new File(SubtitleStore.root(context),System.currentTimeMillis()+"-"+language+"-字幕.vtt");
            Files.write(saved.toPath(),SubtitleStore.shift(contents,0).getBytes(StandardCharsets.UTF_8));return new Track(saved,language);
        }finally{TransferService.deleteFolder(folder);}
    }
    private static File download(String page,String langs,File folder,TransferRuntime.Token token)throws Exception {
        YoutubeDLRequest request=new YoutubeDLRequest(page);
        request.addOption("--ignore-config");request.addOption("--no-playlist");request.addOption("--skip-download");
        request.addOption("--write-subs");request.addOption("--write-auto-subs");request.addOption("--sub-langs",langs);
        request.addOption("--sub-format","vtt");request.addOption("--socket-timeout",20);request.addOption("--retries",1);request.addOption("--no-warnings");
        request.addOption("-o",new File(folder,"subtitle.%(ext)s").getAbsolutePath());
        try{YoutubeDL.getInstance().execute(request,token.id,(percent,eta,line)->{if(token.cancelled.get())YoutubeDL.getInstance().destroyProcessById(token.id);return Unit.INSTANCE;});}
        catch(Exception error){token.check();return null;}
        token.check();File[] choices=folder.listFiles((dir,name)->name.startsWith("subtitle.")&&name.endsWith(".vtt"));
        if(choices==null||choices.length==0)return null;
        Arrays.sort(choices,Comparator.comparing(File::getName));return choices[0];
    }
    static String trackLanguage(String name){
        if(!name.startsWith("subtitle.")||!name.endsWith(".vtt"))return null;
        String code=name.substring(9,name.length()-4).toLowerCase(Locale.ROOT);
        for(String lang:LANGUAGES)if(code.equals(lang)||code.startsWith(lang+"-")||code.startsWith(lang+"_"))return lang;
        return null;
    }
}
