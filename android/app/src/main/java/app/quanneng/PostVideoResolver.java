package app.quanneng;

import android.content.Context;
import android.net.Uri;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import kotlin.Unit;
import java.io.IOException;

/** 只在用户点播或手动识别后，按帖子链接解析一个可直接播放的音画合一地址。 */
final class PostVideoResolver {
    private PostVideoResolver(){}
    static String parse(String output)throws IOException{
        String chosen="";
        if(output==null||output.length()>200000)throw new IOException("未得到可播放的单条视频地址");
        for(String line:output.split("\\R")){
            String value=line.trim();
            if(!value.startsWith("https://")||!MainActivity.isHttps(value)||value.length()>8192)continue;
            if(!chosen.isEmpty()&&!chosen.equals(value))throw new IOException("帖子包含多路视频，请先打开单条帖子");
            chosen=value;
        }
        if(chosen.isEmpty())throw new IOException("此帖子未提供可直接播放的视频");
        return chosen;
    }
    static String resolve(Context context,String page,TransferRuntime.Token token)throws Exception{
        if(!MainActivity.isHttps(page)||page.length()>8192)throw new IOException("请先打开单条视频帖子");
        Uri uri=Uri.parse(page);String host=uri.getHost();
        if(("x.com".equalsIgnoreCase(host)||"twitter.com".equalsIgnoreCase(host))&&
            !uri.getPath().matches("(?i)^/[^/]+/status/[0-9]+/?$"))throw new IOException("请先打开这条 X 帖子，再点识别");
        TransferRuntime.initialize(context);token.check();
        YoutubeDLRequest request=new YoutubeDLRequest(page);
        request.addOption("--ignore-config");request.addOption("--no-playlist");request.addOption("--get-url");
        request.addOption("-f","b[ext=mp4]/b");request.addOption("--socket-timeout",12);
        request.addOption("--retries",1);request.addOption("--no-warnings");
        String output=YoutubeDL.getInstance().execute(request,token.id,(percent,eta,line)->{if(token.cancelled.get())YoutubeDL.getInstance().destroyProcessById(token.id);return Unit.INSTANCE;}).getOut();
        token.check();return parse(output);
    }
}
