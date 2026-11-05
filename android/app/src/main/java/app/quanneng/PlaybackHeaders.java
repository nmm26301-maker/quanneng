package app.quanneng;

import java.net.URI;
import java.util.*;

/** 按最小范围传递视频请求上下文，不保留或记录 Cookie。 */
public final class PlaybackHeaders {
    private PlaybackHeaders() {}
    public static HashMap<String,String> sanitize(Map<String,String> source) {
        HashMap<String,String> result=new HashMap<>();
        if(source==null)return result;
        for(Map.Entry<String,String> entry:source.entrySet()){
            String key=entry.getKey(),value=entry.getValue();
            if(key==null||value==null||value.length()>16384||value.indexOf('\r')>=0||value.indexOf('\n')>=0)continue;
            for(String allowed:new String[]{"Referer","Origin","User-Agent","Cookie","Authorization"}){
                if(allowed.equalsIgnoreCase(key)){result.put(allowed,value);break;}
            }
        }
        return result;
    }
    public static String referrer(String page,String media) {
        try{
            URI from=new URI(page),to=new URI(media);
            if(!"https".equalsIgnoreCase(from.getScheme())||from.getHost()==null||from.getUserInfo()!=null)return "";
            String origin=from.getScheme()+"://"+from.getRawAuthority();
            boolean same=from.getHost().equalsIgnoreCase(to.getHost())&&from.getPort()==to.getPort();
            return same?page.split("#",2)[0]:origin+"/";
        }catch(Exception error){return "";}
    }
}
