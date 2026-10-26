package app.quanneng;
import org.json.*;
/** 可复制的接力记录，仅包含链接、播放毫秒和版本，不包含凭据。 */
final class Handoff {
 static final String PREFIX="QUANNENG/1\n";
 static String encode(String url,long position)throws Exception{NetworkPolicy.url(url);if(position<0||position>604800000L)throw new IllegalArgumentException("播放位置超出范围");return PREFIX+new JSONObject().put("version",1).put("url",url).put("position",position).toString();}
 static JSONObject decode(String raw)throws Exception{if(raw==null||raw.length()>10000||!raw.startsWith(PREFIX))throw new IllegalArgumentException("请粘贴完整的全能接力记录");JSONObject o=new JSONObject(raw.substring(PREFIX.length()));if(o.optInt("version")!=1||!(o.opt("url") instanceof String))throw new IllegalArgumentException("接力版本不支持");String url=o.getString("url");NetworkPolicy.url(url);long pos=o.getLong("position");if(o.getDouble("position")!=pos||pos<0||pos>604800000L)throw new IllegalArgumentException("播放位置无效");return new JSONObject().put("url",url).put("position",pos);}
}
