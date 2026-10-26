package app.quanneng;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
/** 无账号接力，通过系统分享或粘贴把直链及进度发送到另一台设备。 */
public final class HandoffActivity extends FeatureActivity {
 private EditText record;
 @Override public void onCreate(Bundle b){super.onCreate(b);page("跨设备接力");text(body,"在播放页点“分享接力”，发送给另一台安装全能的设备。接力包含视频链接和进度；本地文件需自行传到另一台设备，NAS 需接收设备也能访问。",14);record=input("粘贴全能接力记录",Handoff.PREFIX+"{…}");record.setSingleLine(false);record.setMinLines(4);record.setText(getIntent().getStringExtra("record"));button(body,"继续播放接力视频",this::resume);button(body,"粘贴上次本机接力",()->record.setText(getSharedPreferences("handoff",0).getString("last","")));}
 private void resume(){try{JSONObject o=Handoff.decode(record.getText().toString().trim());getSharedPreferences("handoff",0).edit().putString("last",record.getText().toString().trim()).apply();startActivity(new Intent(this,SystemPlayerActivity.class).setData(Uri.parse(o.getString("url"))).putExtra("position",(int)o.getLong("position")));}catch(Exception e){message.setText(e.getMessage());}}
 static void share(PrivateActivity activity,String url,long position){try{String text=Handoff.encode(url,position);new PrivateDialogBuilder(activity).setTitle("分享视频链接和播放进度？").setMessage("接收方将能查看完整链接；不会分享 Cookie、账号密码或文件。链接有时效时需重新获取。").setNegativeButton("取消",null).setPositiveButton("分享",(d,w)->{activity.getSharedPreferences("handoff",0).edit().putString("last",text).apply();activity.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"发送接力记录"));}).show();}catch(Exception e){Toast.makeText(activity,"本地文件不能通过链接接力，请先传输原文件",Toast.LENGTH_LONG).show();}}
}
