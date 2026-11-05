package app.quanneng;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Inspect bounded response bytes, since signed/CDN endpoints often have no extension or a wrong MIME. */
public final class MediaProbe {
    private MediaProbe(){}
    public static String mime(byte[] bytes,int length,String responseType,String resolvedUrl,String declared){
        String text=new String(bytes,0,Math.max(0,length),StandardCharsets.UTF_8).replace("\ufeff","").trim();
        if(text.startsWith("#EXTM3U"))return "application/vnd.apple.mpegurl";
        if(text.matches("(?s)^(?:<\\?xml.*?\\?>\\s*)?(?:<!--.*?-->\\s*)*<(?:[A-Za-z0-9_]+:)?MPD(?:\\s|>).*"))return "application/dash+xml";
        if(text.toLowerCase(Locale.ROOT).matches("(?s)^(?:<!doctype\\s+html|<html|<head|<body).*"))return "text/html";
        // Bytes win over a misleading .m3u8 suffix or server header.
        if(length>=8&&bytes[4]=='f'&&bytes[5]=='t'&&bytes[6]=='y'&&bytes[7]=='p')return "video/mp4";
        if(length>=4&&(bytes[0]&255)==0x1a&&(bytes[1]&255)==0x45&&(bytes[2]&255)==0xdf&&(bytes[3]&255)==0xa3)return "video/webm";
        String observed=MediaCandidatePolicy.mime(resolvedUrl,responseType);
        return observed.isEmpty()?declared:observed;
    }
}
