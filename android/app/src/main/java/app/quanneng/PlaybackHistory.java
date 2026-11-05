package app.quanneng;
import android.content.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** 本机按媒体标识保存进度与字幕选择，键不包含来源网址或登录参数。 */
final class PlaybackHistory {
    private static String key(String value){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static void save(Context c,String uri,int position,int duration){if(duration<=0||position<0||uri.contains("/vault-open/"))return;c.getSharedPreferences("playback-history",0).edit().putInt(key(uri)+"-position",duration-position<2500?0:Math.min(position,duration)).apply();}
    static int position(Context c,String uri){return Math.max(0,c.getSharedPreferences("playback-history",0).getInt(key(uri)+"-position",0));}
    static String subtitle(Context c,String uri){return c.getSharedPreferences("playback-history",0).getString(key(uri)+"-subtitle","");}
    static void subtitle(Context c,String uri,String name){c.getSharedPreferences("playback-history",0).edit().putString(key(uri)+"-subtitle",name).apply();}
}
