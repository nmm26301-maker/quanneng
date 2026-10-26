package app.quanneng;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.util.*;
/** 密文文件管理，不保存源名称或明文目录索引。 */
public final class VaultActivity extends FeatureActivity {
 private LinearLayout list;private Button importing;
 @Override public void onCreate(Bundle b){super.onCreate(b);page("私人加密保险箱");text(body,"文件使用本机密钥加密保存。播放时临时解密，退出或离开应用后清理。原文件由你自行管理；卸载或清除应用数据后无法恢复密文。",14);importing=button(body,"选择视频 · 加密存入",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*"),93));list=new LinearLayout(this);list.setOrientation(1);body.addView(list);whenUnlocked(this::render);}
 private void render(){list.removeAllViews();File[] files=VaultStore.root(this).listFiles((d,n)->n.endsWith(".qvault"));if(files==null||files.length==0){text(list,"保险箱为空",16);return;}Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=0;i<files.length;i++){File f=files[i];text(list,"私人视频 "+(i+1)+String.format(Locale.ROOT," · %.1f MB",f.length()/1048576.0),16);button(list,"解密播放",()->{importing.setEnabled(false);work("验证并解密视频…",()->VaultStore.open(this,f),opened->{importing.setEnabled(true);startActivity(new Intent(this,SystemPlayerActivity.class).setData(new Uri.Builder().scheme("content").authority("app.quanneng.media").appendPath("vault-open").appendPath(opened.getName()).build()).putExtra("vaultFile",f.getName()));});});button(list,"删除密文",()->new PrivateDialogBuilder(this).setTitle("永久删除这个加密视频？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{f.delete();render();}).show());}}
 @Override protected void onResume(){super.onResume();if(importing!=null)importing.setEnabled(true);}
 @Override protected void onActivityResult(int r,int result,Intent d){super.onActivityResult(r,result,d);if(r==93&&result==RESULT_OK&&d!=null&&d.getData()!=null)whenUnlocked(()->{importing.setEnabled(false);work("正在加密，完成前请保持此页…",()->VaultStore.add(this,d.getData()),f->{importing.setEnabled(true);message.setText("已加密存入，原文件未删除");render();});});}
}
