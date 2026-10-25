package app.quanneng;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** 本机下载/转换界面；任务产物先保留在应用私有空间，导出使用系统选择器。 */
public final class TransferActivity extends PrivateActivity {
    private static final int CONVERT=81,EXPORT=82;
    private EditText url;
    private Button download,convert,cancel;
    private TextView status,error;
    private ProgressBar progress;
    private LinearLayout history;
    private File exporting;
    private String historyKey="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService copying=Executors.newSingleThreadExecutor();
    private final Runnable refresh=new Runnable(){public void run(){if(privateReady())render();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null&&state.getString("exporting")!=null)exporting=new File(state.getString("exporting"));
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PlayerUi.BG);scroll.setFillViewport(true);setContentView(scroll);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(20),dp(20),dp(32));scroll.addView(root);
        LinearLayout top=new LinearLayout(this);root.addView(top);ImageButton back=PlayerUi.icon(this,"back","返回",false);top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->finish());TextView heading=PlayerUi.text(this,"下载与转换",23,PlayerUi.TEXT);heading.setGravity(Gravity.CENTER_VERTICAL);top.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));Button lock=PlayerUi.chip(this,"锁定");top.addView(lock);lock.setOnClickListener(v->lockPrivateApp());
        text(root,"从链接到本地，把视频留在眼前。",14,PlayerUi.MUTED,16);
        text(root,"站点视频下载",18,PlayerUi.TEXT,24);text(root,"粘贴 yt-dlp 支持的视频页面链接。",13,PlayerUi.MUTED,4);
        url=new EditText(this);url.setId(1600);url.setSingleLine(false);url.setMaxLines(3);url.setHint("https://example.com/watch/…");url.setTextColor(PlayerUi.TEXT);url.setHintTextColor(PlayerUi.MUTED);url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setContentDescription("要下载的视频页面网址");root.addView(url,new LinearLayout.LayoutParams(-1,dp(88)));
        download=button(root,"下载到私人空间",true);download.setId(1601);download.setOnClickListener(v->{String value=url.getText().toString().trim();if(!MainActivity.isHttps(value)){error.setText("请填写完整的 HTTPS 视频页面链接");return;}start("download",value);});
        text(root,"普通视频转换",18,PlayerUi.TEXT,28);text(root,"选择可访问的视频，转换为 H.264 / AAC 的 MP4。",13,PlayerUi.MUTED,4);
        convert=button(root,"选择视频 · 转为 MP4",false);convert.setId(1602);convert.setOnClickListener(v->chooseVideo());
        text(root,"任务进度",18,PlayerUi.TEXT,28);status=text(root,"尚无进行中的任务",14,PlayerUi.MUTED,8);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setId(1603);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(28)));cancel=button(root,"取消当前任务",false);cancel.setOnClickListener(v->TransferService.cancel());
        error=text(root,"",13,0xffff9ba7,10);text(root,"已完成",18,PlayerUi.TEXT,28);text(root,"保留在全能本机私人空间，可播放或另存到手机。",13,PlayerUi.MUTED,4);history=new LinearLayout(this);history.setOrientation(1);root.addView(history);
        handler.post(refresh);
    }
    private void chooseVideo(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","application/octet-stream"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),CONVERT);}catch(ActivityNotFoundException failure){error.setText("系统未找到文件选择器");}}
    private void start(String mode,String value){if(!privateReady())return;error.setText("");if(!TransferService.start(this,mode,value))error.setText(TransferService.failure.isEmpty()?"已有任务正在处理，请等待完成":TransferService.failure);render();}
    private void render(){
        boolean busy=TransferService.running;download.setEnabled(!busy);convert.setEnabled(!busy);cancel.setVisibility(busy?View.VISIBLE:View.GONE);progress.setIndeterminate(busy&&TransferService.percent<0);progress.setProgress(Math.max(0,TransferService.percent));status.setText(TransferService.phase);error.setText(TransferService.failure);
        List<File> files=TransferService.completed(this);String key=files.toString();if(key.equals(historyKey))return;historyKey=key;history.removeAllViews();
        if(files.isEmpty()){text(history,"下载或转换完成的视频会显示在这里。",13,PlayerUi.MUTED,16);return;}
        for(File file:files){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(PlayerUi.surface(this,PlayerUi.SURFACE,16));LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(-1,-2);box.topMargin=dp(12);history.addView(card,box);
            String date=new SimpleDateFormat("MM-dd HH:mm",Locale.ROOT).format(new Date(file.lastModified()));text(card,(file.getName().startsWith("result")?"转换视频":"下载视频")+" · "+date,15,PlayerUi.TEXT,0);text(card,String.format(Locale.ROOT,"%.1f MB · %s",file.length()/1048576.0,file.getName()),12,PlayerUi.MUTED,4);
            LinearLayout row=new LinearLayout(this);card.addView(row);for(String action:new String[]{"播放","保存到手机","删除"}){Button button=PlayerUi.chip(this,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(10),dp(3),0);row.addView(button,p);button.setOnClickListener(v->{if(!privateReady())return;if("播放".equals(action))startActivity(new Intent(this,SystemPlayerActivity.class).setData(MediaContentProvider.uri(file)));else if("保存到手机".equals(action))export(file);else new PrivateDialogBuilder(this).setTitle("删除这个视频？").setMessage("删除后无法恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{TransferService.deleteFolder(file.getParentFile());historyKey="";render();}).show();});}
        }
    }
    private void export(File file){exporting=file;String ext=file.getName().substring(file.getName().lastIndexOf('.'));try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime(file)).putExtra(Intent.EXTRA_TITLE,"全能-"+file.getParentFile().getName().substring(0,8)+ext),EXPORT);}catch(ActivityNotFoundException failure){error.setText("系统未找到保存位置选择器");}}
    private String mime(File file){String name=file.getName();return name.endsWith(".webm")?"video/webm":name.endsWith(".mkv")?"video/x-matroska":"video/mp4";}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;whenUnlocked(()->{
        Uri uri=data.getData();if(request==CONVERT){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}start("convert",uri.toString());}
        else if(request==EXPORT&&exporting!=null){File input=exporting;exporting=null;status.setText("正在保存到所选位置…");copying.execute(()->{boolean good=false;try(InputStream source=new FileInputStream(input);OutputStream output=getContentResolver().openOutputStream(uri,"w")){if(output==null)throw new IOException();byte[] block=new byte[65536];int n;while((n=source.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException();output.write(block,0,n);}output.flush();good=true;}catch(IOException|SecurityException ignored){}final boolean saved=good;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;error.setText(saved?"":"保存失败，请检查文件权限或手机空间");Toast.makeText(this,saved?"视频已保存到选择的位置":"保存失败",Toast.LENGTH_LONG).show();});});}
    });}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(exporting!=null)state.putString("exporting",exporting.getAbsolutePath());}
    private TextView text(LinearLayout box,String value,int size,int color,int top){TextView view=PlayerUi.text(this,value,size,color);view.setPadding(0,dp(top),0,dp(6));box.addView(view);return view;}
    private Button button(LinearLayout box,String label,boolean primary){Button button=PlayerUi.chip(this,label);if(primary){button.setTextColor(PlayerUi.BG);button.setBackground(PlayerUi.feedback(this,PlayerUi.ACCENT,12));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);box.addView(button,p);return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onPrivateUnlocked(){historyKey="";if(history!=null)render();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);copying.shutdownNow();super.onDestroy();}
}
