package app.quanneng;
import android.os.Bundle;
import android.widget.*;
import java.util.*;
/** DLNA 设备选择与播放控制，不发送网页 Cookie 或 NAS 密码。 */
public final class CastActivity extends FeatureActivity {
 private EditText url,device;private LinearLayout devices;private Button discover;private DlnaClient.Device selected;private LocalSharePanel sharing;
 @Override public void onCreate(Bundle b){super.onCreate(b);page("电视投屏 · DLNA");text(body,"手机与电视连同一 Wi-Fi，并开启电视 DLNA。使用电视能直接访问的视频直链；本地文件可开启临时共享后投送，登录网页和加密视频仍需原平台。",14);url=input("视频直链","https://…/video.mp4");String current=getIntent().getStringExtra("url");url.setText(current!=null&&current.startsWith("content:")?"":current);sharing=new LocalSharePanel(this,current,value->{url.setText(value);message.setText("本地视频已就绪，请选择电视并点投送");});device=input("手动设备描述地址（可选）","http://192.168.1.2:1234/description.xml");button(body,"连接手动设备",()->work("读取设备…",()->DlnaClient.describe(device.getText().toString().trim()),d->{if(d==null)message.setText("未发现 AVTransport 服务");else choose(d);}));discover=button(body,"搜索同一 Wi-Fi 的电视",()->{discover.setEnabled(false);work("搜索设备，约 4 秒…",()->DlnaClient.discover(this),found->{discover.setEnabled(true);devices.removeAllViews();if(found.isEmpty())message.setText("没有发现设备，请确认同一 Wi-Fi，或填写手动描述地址");for(DlnaClient.Device d:found)button(devices,d.name,()->choose(d));});});devices=section();button(body,"投送视频",()->{if(selected==null){message.setText("请先选择设备");return;}String value=url.getText().toString().trim();new PrivateDialogBuilder(this).setTitle("投送到 "+selected.name+"？").setMessage("电视将接收并访问当前视频链接。").setNegativeButton("取消",null).setPositiveButton("投送",(d,w)->work("发送播放请求…",()->{DlnaClient.cast(selected,value);return true;},ok->message.setText("电视已接受播放请求"))).show();});for(String action:new String[]{"Play","Pause","Stop"})button(body,action.equals("Play")?"播放":action.equals("Pause")?"暂停":"停止投屏",()->{if(selected==null){message.setText("请先连接设备");return;}work("发送控制…",()->{DlnaClient.action(selected,action,action.equals("Play")?"<Speed>1</Speed>":"");return true;},ok->message.setText("设备已接受控制"));});}
 private void choose(DlnaClient.Device d){selected=d;message.setText("已选择："+d.name);}
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);whenUnlocked(()->{if(sharing!=null)sharing.result(request,result,data);});}
 @Override protected void onDestroy(){if(sharing!=null)sharing.close();super.onDestroy();}
 @Override protected void onResume(){super.onResume();if(discover!=null)discover.setEnabled(true);}
}
