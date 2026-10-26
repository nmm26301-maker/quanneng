package app.quanneng;
import android.content.*;
import android.net.*;
import android.os.Bundle;
import android.media.*;
import android.widget.*;
import java.net.*;
import java.util.*;
/** 主动执行的播放诊断，报告隐藏地址查询参数和访问凭据。 */
public final class DiagnosticsActivity extends FeatureActivity {
 private EditText url;private TextView report;private String last="";private Button run;
 @Override public void onCreate(Bundle b){super.onCreate(b);page("播放问题诊断");url=input("视频地址（可留空检测设备）","https://…");url.setText(getIntent().getStringExtra("url"));run=button(body,"开始诊断",()->{run.setEnabled(false);work("正在检查网络、来源响应及解码器…",()->diagnose(url.getText().toString().trim()),s->{run.setEnabled(true);last=s;report.setText(s);});});report=text(body,"",14);report.setTextIsSelectable(true);button(body,"分享诊断报告",()->{if(last.isEmpty()){message.setText("请先执行诊断");return;}startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,last),"分享诊断结果"));});}
 private String diagnose(String value)throws Exception{StringBuilder b=new StringBuilder("全能 v0.3.0 播放诊断\nAndroid "+android.os.Build.VERSION.RELEASE+" / "+android.os.Build.MODEL+"\n");ConnectivityManager cm=getSystemService(ConnectivityManager.class);NetworkCapabilities n=cm.getNetworkCapabilities(cm.getActiveNetwork());b.append("网络：").append(n==null?"未连接":n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":"移动或其他网络").append('\n');if(!value.isEmpty()){URL u=NetworkPolicy.url(value);b.append("来源：").append(u.getProtocol()).append("://").append(u.getHost()).append("（路径、参数已隐藏）\n");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Range","bytes=0-511");long start=System.currentTimeMillis();try{int code=c.getResponseCode();String mime=c.getContentType();b.append("HTTP ").append(code).append(" / ").append(System.currentTimeMillis()-start).append(" ms\n类型：").append(mime).append('\n');if(code==401||code==403)b.append("建议：来源需要授权，返回原网页重新播放。\n");else if(code>=300&&code<400)b.append("建议：来源重定向，请重新获取最终视频地址。\n");else if(code==404||code==410)b.append("建议：链接失效，重新获取地址。\n");else if(mime!=null&&mime.contains("html"))b.append("建议：此地址是网页，请使用网页播放模式。\n");else b.append("建议：若仍无法播放，尝试其他编码或用工具箱转为 MP4。\n");}catch(Exception e){b.append("请求失败：").append(e.getClass().getSimpleName()).append("；检查网络、证书或地址时效。\n");}finally{c.disconnect();}}
  Set<String> codecs=new TreeSet<>();for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())if(!info.isEncoder())for(String type:info.getSupportedTypes())if(type.startsWith("video/"))codecs.add(type);b.append("设备视频解码：").append(codecs).append("\n未发送 Cookie 或账号认证；此结果不等于实际解码成功。");return b.toString();}
 @Override protected void onResume(){super.onResume();if(run!=null)run.setEnabled(true);}
}
