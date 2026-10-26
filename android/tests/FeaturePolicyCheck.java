package app.quanneng;
import java.io.*;
/** 可在主机运行的协议与字幕边界回归。 */
public final class FeaturePolicyCheck {
 private static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  for(String s:new String[]{"http://10.0.2.2/a","http://172.16.0.1/a","http://192.168.1.1/a","https://example.com/v.mp4"})NetworkPolicy.url(s);
  for(String s:new String[]{"file:///x","javascript:a","http://127.0.0.1/a","http://169.254.169.254/x","http://172.32.0.1/a","http://192.168.999.1/a","https://u:p@example.com/x","http://example.com/x"}){try{NetworkPolicy.url(s);throw new AssertionError(s);}catch(IOException expected){}}
  check(NetworkPolicy.sameOrigin(new java.net.URL("https://example.com:443/a"),new java.net.URL("https://example.com/b")));
  check(!NetworkPolicy.sameOrigin(new java.net.URL("https://example.com/a"),new java.net.URL("https://other.example/a")));
  String raw="1\n00:00:01,000 --> 00:00:02,500\n字幕\n";String shifted=SubtitleStore.shift(raw,750);check(shifted.contains("00:00:01.750 --> 00:00:03.250"));check(shifted.startsWith("WEBVTT"));check(SubtitleStore.shift(raw,-500).contains("00:00:00.500"));
  try{SubtitleStore.shift("broken",0);throw new AssertionError();}catch(IOException expected){}
  String record=Handoff.encode("https://example.com/v.mp4",123456);check(Handoff.decode(record).getLong("position")==123456);
  for(String s:new String[]{"bad","QUANNENG/1\n{\"version\":1,\"url\":\"https://example.com/a\",\"position\":-1}","QUANNENG/1\n{\"version\":1,\"url\":\"https://example.com/a\",\"position\":0.5}"}){try{Handoff.decode(s);throw new AssertionError(s);}catch(IllegalArgumentException expected){}catch(org.json.JSONException expected){}}
  NetworkIO.xml("<root><item>ok</item></root>".getBytes("UTF-8"));try{NetworkIO.xml("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><x>&a;</x>".getBytes("UTF-8"));throw new AssertionError();}catch(org.xml.sax.SAXException expected){}
  System.out.println("FEATURE_POLICY_OK: URL boundaries, same-origin credentials, subtitle offsets, strict handoff and XML hardening");
 }
}
