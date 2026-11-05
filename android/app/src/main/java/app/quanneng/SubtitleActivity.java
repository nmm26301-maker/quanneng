package app.quanneng;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
/** 字幕中心：导入、预览、偏移、字号、删除及返回播放器。 */
public final class SubtitleActivity extends FeatureActivity {
 private LinearLayout list;
 @Override public void onCreate(Bundle b){super.onCreate(b);page("字幕中心");text(body,"导入 UTF-8 的 SRT / VTT。正偏移让字幕晚出现，负偏移提前。",14);button(body,"导入字幕文件",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),91));
  LinearLayout sizes=section();text(sizes,"字幕字号",16);for(int n:new int[]{16,20,24,28,32})button(sizes,n+" sp",()->{getSharedPreferences("subtitle-settings",0).edit().putInt("size",n).apply();message.setText("字号已设为 "+n+" sp");});list=new LinearLayout(this);list.setOrientation(1);body.addView(list);whenUnlocked(this::render);
 }
 private void render(){list.removeAllViews();java.util.List<File> files=SubtitleStore.list(this);if(files.isEmpty())text(list,"尚无字幕，导入后可在播放器中选择。",14);for(File f:files){LinearLayout row=new LinearLayout(this);row.setOrientation(1);list.addView(row);text(row,f.getName(),16);long delay=getSharedPreferences("subtitle-settings",0).getLong(f.getName(),0);text(row,"偏移 "+delay+" 毫秒",14);button(row,"预览 / 设置偏移",()->settings(f));if(getIntent().getBooleanExtra("pick",false))button(row,"使用这份字幕",()->{setResult(RESULT_OK,new Intent().putExtra("subtitle",f.getName()));finish();});button(row,"删除",()->new PrivateDialogBuilder(this).setTitle("删除字幕？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{f.delete();render();}).show());}}
 private void settings(File f){work("读取字幕…",()->{try(InputStream in=new FileInputStream(f)){return SubtitleStore.read(in);}},s->{LinearLayout box=new LinearLayout(this);box.setOrientation(1);EditText offset=new EditText(this);offset.setTextColor(PlayerUi.TEXT);offset.setInputType(4098);offset.setText(String.valueOf(getSharedPreferences("subtitle-settings",0).getLong(f.getName(),0)));offset.setContentDescription("字幕偏移毫秒，正数延迟负数提前");box.addView(offset);text(box,s.substring(0,Math.min(s.length(),700)),14);android.app.AlertDialog d=new PrivateDialogBuilder(this).setTitle("偏移（毫秒）与预览").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{try{long value=Long.parseLong(offset.getText().toString());if(value< -3600000||value>3600000)throw new NumberFormatException();getSharedPreferences("subtitle-settings",0).edit().putLong(f.getName(),value).apply();d.dismiss();render();}catch(NumberFormatException e){offset.setError("请输入 -3600000 到 3600000 的整数");}}));d.show();});}
 @Override protected void onActivityResult(int r,int result,Intent d){super.onActivityResult(r,result,d);if(r==91&&result==RESULT_OK&&d!=null&&d.getData()!=null)whenUnlocked(()->work("正在校验字幕…",()->SubtitleStore.importFile(this,d.getData(),LibraryActivity.name(this,d.getData())),f->render()));}
}
