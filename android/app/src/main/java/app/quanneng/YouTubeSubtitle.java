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

/** Fetch a Chinese caption track or YouTube's available auto-translation for one video. */
final class YouTubeSubtitle {
    private YouTubeSubtitle(){}
    static String videoId(String page){
        if(!MainActivity.isHttps(page))return null;
        Uri uri=Uri.parse(page);String host=uri.getHost();if(host==null)return null;host=host.toLowerCase(Locale.ROOT);
        String id=null;
        if("youtu.be".equals(host)){List<String> paths=uri.getPathSegments();if(paths.size()==1)id=paths.get(0);}
        else if("youtube.com".equals(host)||"www.youtube.com".equals(host)||"m.youtube.com".equals(host)){
            List<String> paths=uri.getPathSegments();if(paths.size()==1&&"watch".equals(paths.get(0)))id=uri.getQueryParameter("v");
            else if(paths.size()==2&&Arrays.asList("shorts","live","embed").contains(paths.get(0)))id=paths.get(1);
        }
        return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?id:null;
    }
    static boolean isYouTube(String page){return videoId(page)!=null;}
    static File fetch(Context context,String page,TransferRuntime.Token token)throws Exception{
        String id=videoId(page);if(id==null)throw new IOException("这不是油管视频链接");
        File folder=new File(context.getCacheDir(),"youtube-captions/"+UUID.randomUUID());
        if(!folder.mkdirs())throw new IOException("无法创建字幕临时目录");
        try{
            TransferRuntime.initialize(context);token.check();
            YoutubeDLRequest request=new YoutubeDLRequest("https://www.youtube.com/watch?v="+id);
            request.addOption("--ignore-config");request.addOption("--no-playlist");request.addOption("--skip-download");
            request.addOption("--write-subs");request.addOption("--write-auto-subs");
            request.addOption("--sub-langs","zh-Hans,zh-Hant,zh");request.addOption("--sub-format","vtt");
            request.addOption("--socket-timeout",20);request.addOption("--retries",2);request.addOption("--no-warnings");
            request.addOption("-o",new File(folder,"subtitle.%(ext)s").getAbsolutePath());
            YoutubeDL.getInstance().execute(request,token.id,(percent,eta,line)->{if(token.cancelled.get())YoutubeDL.getInstance().destroyProcessById(token.id);return Unit.INSTANCE;});
            token.check();File[] choices=folder.listFiles((dir,name)->name.startsWith("subtitle.")&&name.endsWith(".vtt"));
            if(choices==null||choices.length==0)throw new IOException("此视频未提供可获取的中文字幕");
            Arrays.sort(choices,Comparator.comparing(File::getName));
            String text;try(InputStream in=new FileInputStream(choices[0])){text=SubtitleStore.read(in);}
            File saved=new File(SubtitleStore.root(context),System.currentTimeMillis()+"-油管中文字幕.vtt");
            Files.write(saved.toPath(),SubtitleStore.shift(text,0).getBytes(StandardCharsets.UTF_8));return saved;
        }finally{TransferService.deleteFolder(folder);}
    }
}
