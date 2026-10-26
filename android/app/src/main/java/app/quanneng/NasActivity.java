package app.quanneng;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.net.*;
import java.util.*;
import org.w3c.dom.*;
/** WebDAV 目录浏览及带会话认证的原生播放；不持久化账号密码。 */
public final class NasActivity extends FeatureActivity {
 private EditText address,user,password;private LinearLayout entries;private final Deque<String> back=new ArrayDeque<>();private String auth="";private URL origin;
 @Override public void onCreate(Bundle b){super.onCreate(b);page("NAS / 局域网");text(body,"WebDAV 支持 HTTPS，局域网数字 IP 支持 HTTP。账号密码仅保留在本次页面内存。SMB 请通过系统文件提供器授权目录。",14);address=input("WebDAV 地址","https://nas.example.com/dav/");address.setText(getPreferences(0).getString("url",""));user=input("用户名（可选）","");password=input("密码（可选）","");password.setInputType(129);button(body,"连接 / 刷新",()->connect(address.getText().toString().trim(),true));button(body,"返回上一级",()->{if(!back.isEmpty())connect(back.pop(),false);else message.setText("已经到连接起点");});button(body,"通过系统目录访问 SMB / NAS",()->startActivity(new Intent(this,LibraryActivity.class)));entries=new LinearLayout(this);entries.setOrientation(1);body.addView(entries);}
 private void connect(String value,boolean reset){
  final URL url;try{url=NetworkPolicy.url(value);if(reset){origin=url;back.clear();String login=user.getText().toString();auth=login.isEmpty()?"":"Basic "+android.util.Base64.encodeToString((login+":"+password.getText()).getBytes("UTF-8"),android.util.Base64.NO_WRAP);}if(!NetworkPolicy.sameOrigin(origin,url))throw new java.io.IOException("目录链接超出当前服务器");}catch(Exception e){message.setText(e.getMessage());return;}
  final String credentials=auth;work("正在读取目录…",()->NetworkIO.xml(NetworkIO.request(url,"PROPFIND",credentials,"application/xml; charset=utf-8",null,"<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getcontenttype/></d:prop></d:propfind>".getBytes("UTF-8"))),doc->{
   address.setText(url.toString());getPreferences(0).edit().putString("url",url.toString()).apply();entries.removeAllViews();NodeList nodes=doc.getElementsByTagNameNS("DAV:","response");int count=0;
   for(int i=0;i<Math.min(nodes.getLength(),1000);i++){try{Element e=(Element)nodes.item(i);String href=NetworkIO.value(e,"href");URL link=new URL(url,href);NetworkPolicy.url(link.toString());if(!NetworkPolicy.sameOrigin(origin,link))continue;String p=link.getPath().replaceAll("/+$","");if(p.equals(url.getPath().replaceAll("/+$","")))continue;boolean directory=e.getElementsByTagNameNS("DAV:","collection").getLength()>0;String title=NetworkIO.value(e,"displayname");if(title.isEmpty())title=Uri.decode(link.getPath().replaceAll("/+$","").substring(link.getPath().replaceAll("/+$","").lastIndexOf('/')+1));if(!directory&&!title.matches("(?i).*\\.(mp4|mkv|mov|webm|avi|m3u8|mpd|m4v|m4a|mp3)$"))continue;count++;final String name=title;button(entries,(directory?"目录 · ":"播放 · ")+name,()->{if(directory){back.push(url.toString());connect(link.toString(),false);}else{Intent intent=new Intent(this,SystemPlayerActivity.class).setData(Uri.parse(link.toString()));if(!credentials.isEmpty())intent.putExtra("nasAuth",credentials);startActivity(intent);}});}catch(Exception ignored){}}
   message.setText("找到 "+count+" 个目录或媒体文件"+ (nodes.getLength()>1000?"（仅显示前 1000 项）":""));
  });
 }
 @Override protected void onPrivateLocked(){if(password!=null){password.setText("");auth="";entries.removeAllViews();}}
}
