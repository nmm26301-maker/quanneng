import app.quanneng.PlaybackHeaders;
import java.util.*;
public class PlaybackHeadersCheck {
 public static void main(String[] args){
  Map<String,String> input=new HashMap<>();input.put("referer","https://source.example/watch?v=1");input.put("Cookie","own=1");input.put("Host","evil.example");input.put("User-Agent","bad\r\nInjected: true");
  Map<String,String> clean=PlaybackHeaders.sanitize(input);
  if(clean.size()!=2||!"own=1".equals(clean.get("Cookie"))||!clean.containsKey("Referer"))throw new AssertionError(clean);
  if(!"https://source.example/".equals(PlaybackHeaders.referrer("https://source.example/watch?private=1#fragment","https://cdn.example/video")))throw new AssertionError("cross-origin referrer leaked private path");
  if(!"https://source.example/watch?q=%E4%B8%AD".equals(PlaybackHeaders.referrer("https://source.example/watch?q=%E4%B8%AD#part","https://source.example/video")))throw new AssertionError("same-origin encoding changed");
  if(!PlaybackHeaders.referrer("javascript:alert(1)","https://cdn.example/video").isEmpty())throw new AssertionError("invalid source allowed");
  System.out.println("Playback header context tests passed");
 }
}
