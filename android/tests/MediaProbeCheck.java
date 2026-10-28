import app.quanneng.MediaProbe;
import java.nio.charset.StandardCharsets;
public final class MediaProbeCheck {
 public static void main(String[] args){
  expect("\ufeff #EXTM3U\n#EXT-X-VERSION:3","video/mp4","https://cdn.example/signed?token=1","video/mp4","application/vnd.apple.mpegurl");
  expect("<?xml version='1.0'?>\n<MPD xmlns='urn:mpeg:dash:schema:mpd:2011'>","application/octet-stream","https://cdn.example/stream",null,"application/dash+xml");
  expect("<!DOCTYPE html><html>sign in</html>","video/mp4","https://cdn.example/movie.mp4",null,"text/html");
  expect("0000ftypisom","text/plain","https://cdn.example/stream.m3u8",null,"video/mp4");
  expect("unrecognized","application/x-mpegurl","https://cdn.example/stream",null,"application/vnd.apple.mpegurl");
  System.out.println("Bounded response media detection passed");
 }
 static void expect(String body,String type,String url,String declared,String expected){byte[] b=body.getBytes(StandardCharsets.UTF_8);String actual=MediaProbe.mime(b,b.length,type,url,declared);if(!expected.equals(actual))throw new AssertionError(actual+" != "+expected);}
}
