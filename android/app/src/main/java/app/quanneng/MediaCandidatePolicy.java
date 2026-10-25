package app.quanneng;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** 借鉴 mrowser 的分类与清单优先设计；纯函数，避免把 fMP4 片段当作完整视频。 */
public final class MediaCandidatePolicy {
    private MediaCandidatePolicy(){}
    private static final Pattern FRAGMENT=Pattern.compile("(?:^|[-_.])(init|dashinit|seg(?:ment)?|chunk|frag(?:ment)?)(?:[-_.]|[0-9]|$)");
    public static String role(String url){
        String path;try{path=new URI(url).getPath();if(path==null)return "other";path=path.toLowerCase(Locale.ROOT);}catch(Exception error){return "other";}
        if(path.endsWith(".m3u8"))return "hls";
        if(path.endsWith(".mpd"))return "dash";
        if(path.endsWith(".vtt")||path.endsWith(".srt"))return "subtitle";
        if(path.matches(".*\\.(ts|m4s|cmfv|cmfa)$"))return "segment";
        if(path.matches(".*\\.(mp4|m4v|webm|mkv|mov)$")){
            String name=path.substring(path.lastIndexOf('/')+1,path.lastIndexOf('.'));
            if(FRAGMENT.matcher(name).find()||name.matches("[0-9]+"))return "segment";
            return "file";
        }
        return "other";
    }
    public static boolean playable(String url){String role=role(url);return "hls".equals(role)||"dash".equals(role)||"file".equals(role);}
    public static String mime(String url,String observed){
        String type=observed==null?"":observed.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        if(type.equals("application/vnd.apple.mpegurl")||type.equals("application/x-mpegurl"))return "application/vnd.apple.mpegurl";
        if(type.equals("application/dash+xml"))return type;
        if(type.matches("video/(mp4|webm|quicktime|x-m4v|x-matroska)"))return type;
        if("hls".equals(role(url)))return "application/vnd.apple.mpegurl";
        if("dash".equals(role(url)))return "application/dash+xml";
        return "";
    }
    public static int priority(String url,String observed){
        String type=mime(url,observed);
        if(type.equals("application/vnd.apple.mpegurl"))return url.toLowerCase(Locale.ROOT).contains("master")?0:1;
        return type.equals("application/dash+xml")?2:3;
    }
}
