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
    private LinearLayout history,tasks;
    private String taskKey="",formError="",exportNotice="";
    private ToolRequest pendingTool;
    private boolean exportingNow;
    private EditText startTime,endTime;
    private File exporting;
    private String historyKey="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService copying=Executors.newSingleThreadExecutor();
    private final Runnable refresh=new Runnable(){public void run(){if(privateReady())render();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);TransferService.recover(this);if(state!=null&&state.getString("pendingTool")!=null)try{pendingTool=ToolRequest.restore(state.getString("pendingTool"));}catch(Exception ignored){}if(state!=null&&state.getString("exporting")!=null)exporting=new File(state.getString("exporting"));
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PlayerUi.BG);scroll.setFillViewport(true);setContentView(scroll);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(20),dp(20),dp(32));scroll.addView(root);
        LinearLayout top=new LinearLayout(this);root.addView(top);ImageButton back=PlayerUi.icon(this,"back","返回",false);top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->finish());TextView heading=PlayerUi.text(this,"下载与转换",23,PlayerUi.TEXT);heading.setGravity(Gravity.CENTER_VERTICAL);top.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));Button lock=PlayerUi.chip(this,"锁定");top.addView(lock);lock.setOnClickListener(v->lockPrivateApp());
        text(root,"从链接到本地，把视频留在眼前。",14,PlayerUi.MUTED,16);
        text(root,"站点视频下载",18,PlayerUi.TEXT,24);text(root,"粘贴 yt-dlp 支持的视频页面链接。",13,PlayerUi.MUTED,4);
        url=new EditText(this);url.setId(1600);url.setSingleLine(false);url.setMaxLines(3);url.setHint("https://example.com/watch/…");url.setTextColor(PlayerUi.TEXT);url.setHintTextColor(PlayerUi.MUTED);url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setContentDescription("要下载的视频页面网址");root.addView(url,new LinearLayout.LayoutParams(-1,dp(88)));
        download=button(root,"下载到私人空间",true);download.setId(1601);download.setOnClickListener(v->{String value=url.getText().toString().trim();if(!MainActivity.isHttps(value)){showError("请填写完整的 HTTPS 视频页面链接");return;}start("download",value);});
        text(root,"普通视频转换",18,PlayerUi.TEXT,28);text(root,"选择可访问的视频，转换为 H.264 / AAC 的 MP4。",13,PlayerUi.MUTED,4);
        convert=button(root,"选择视频 · 转为 MP4",false);convert.setId(1602);convert.setOnClickListener(v->chooseTool("convert"));
        text(root,"视频工具箱",18,PlayerUi.TEXT,24);text(root,"裁剪和截图使用下方秒数；所有处理在本机完成。可连续添加任务。",13,PlayerUi.MUTED,4);
        startTime=new EditText(this);startTime.setId(1610);startTime.setTextColor(PlayerUi.TEXT);startTime.setHintTextColor(PlayerUi.MUTED);startTime.setHint("起点 / 截图位置（秒）");startTime.setContentDescription("起点或截图位置，单位秒");startTime.setInputType(8194);root.addView(startTime);endTime=new EditText(this);endTime.setId(1611);endTime.setTextColor(PlayerUi.TEXT);endTime.setHintTextColor(PlayerUi.MUTED);endTime.setHint("裁剪终点（秒）");endTime.setContentDescription("裁剪终点，单位秒");endTime.setInputType(8194);root.addView(endTime);
        String[] modes={"trim","audio","compress","snapshot","mute"},names={"裁剪片段","提取音频 M4A","压缩视频","导出画面 JPG","生成无声视频"};for(int i=0;i<modes.length;i++){final String mode=modes[i];button(root,names[i],false).setOnClickListener(v->chooseTool(mode));}
        text(root,"任务进度",18,PlayerUi.TEXT,28);status=text(root,"尚无进行中的任务",14,PlayerUi.MUTED,8);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setId(1603);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(28)));tasks=new LinearLayout(this);tasks.setOrientation(1);root.addView(tasks);cancel=button(root,"暂停当前任务",false);cancel.setOnClickListener(v->TransferService.cancel());
        error=text(root,"",13,0xffff9ba7,10);text(root,"已完成",18,PlayerUi.TEXT,28);text(root,"保留在全能本机私人空间，可播放或另存到手机。",13,PlayerUi.MUTED,4);history=new LinearLayout(this);history.setOrientation(1);root.addView(history);
        if(state!=null){url.setText(state.getString("url",""));startTime.setText(state.getString("start",""));endTime.setText(state.getString("end",""));formError=state.getString("formError","");}
        String postUrl=getIntent().getStringExtra("postUrl");if(MainActivity.isHttps(postUrl)){url.setText(postUrl);status.setText("已接收帖子链接；点“下载到私人空间”，完成后可直接播放");}
        handler.post(refresh);
    }
    private void showError(String value){formError=value;error.setText(value);}
    private void chooseTool(String mode){try{pendingTool=ToolRequest.capture(mode,startTime.getText().toString(),endTime.getText().toString());showError("");chooseVideo();}catch(IllegalArgumentException e){showError(e.getMessage());}}
    private void chooseVideo(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","application/octet-stream"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),CONVERT);}catch(ActivityNotFoundException failure){showError("系统未找到文件选择器");}}
    private void start(String mode,String value){if(!privateReady())return;showError("");exportNotice="";if(!TransferService.start(this,mode,value))showError(TransferService.failure.isEmpty()?"已有任务正在处理，请等待完成":TransferService.failure);render();}
    private void render(){
        boolean busy=TransferService.running;download.setEnabled(true);convert.setEnabled(true);renderTasks();cancel.setVisibility(busy?View.VISIBLE:View.GONE);progress.setIndeterminate(busy&&TransferService.percent<0);progress.setProgress(Math.max(0,TransferService.percent));status.setText(exportingNow?"正在保存到所选位置…":exportNotice.isEmpty()?TransferService.phase:exportNotice);error.setText(formError.isEmpty()?TransferService.failure:formError);
        List<File> files=TransferService.completed(this);String key=files.toString();if(key.equals(historyKey))return;historyKey=key;history.removeAllViews();
        if(files.isEmpty()){text(history,"下载或转换完成的视频会显示在这里。",13,PlayerUi.MUTED,16);return;}
        for(File file:files){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(PlayerUi.surface(this,PlayerUi.SURFACE,16));LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(-1,-2);box.topMargin=dp(12);history.addView(card,box);
            TaskStore.Job metadata=null;for(TaskStore.Job j:TaskStore.list(this))if(j.id.equals(file.getParentFile().getName())){metadata=j;break;}String title=metadata==null?(file.getName().startsWith("result")?"转换视频":"下载视频"):ToolRequest.label(metadata.mode)+" · "+metadata.title;
            String date=new SimpleDateFormat("MM-dd HH:mm",Locale.ROOT).format(new Date(file.lastModified()));text(card,title+" · "+date,15,PlayerUi.TEXT,0);text(card,String.format(Locale.ROOT,"%.1f MB · %s",file.length()/1048576.0,file.getName()),12,PlayerUi.MUTED,4);
            LinearLayout row=new LinearLayout(this);card.addView(row);for(String action:new String[]{"播放","保存到手机","删除"}){Button button=PlayerUi.chip(this,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(10),dp(3),0);row.addView(button,p);button.setOnClickListener(v->{if(!privateReady())return;if("播放".equals(action))startActivity(new Intent(this,file.getName().endsWith(".jpg")?ImagePreviewActivity.class:SystemPlayerActivity.class).setData(MediaContentProvider.uri(file)));else if("保存到手机".equals(action))export(file);else new PrivateDialogBuilder(this).setTitle("删除这个视频？").setMessage("删除后无法恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{TransferService.deleteFolder(file.getParentFile());historyKey="";render();}).show();});}
        }
    }
    private void renderTasks(){java.util.List<TaskStore.Job> jobs=TaskStore.list(this);String key="";for(TaskStore.Job j:jobs)key+=j.id+j.state+j.error;if(key.equals(taskKey))return;taskKey=key;tasks.removeAllViews();for(TaskStore.Job j:jobs){if("done".equals(j.state)||"cancelled".equals(j.state))continue;text(tasks,ToolRequest.label(j.mode)+" · "+stateName(j.state)+"\n"+j.title,15,PlayerUi.TEXT,12);if(!j.error.isEmpty())text(tasks,j.error,13,PlayerUi.MUTED,2);if("running".equals(j.state)||"queued".equals(j.state))button(tasks,"暂停此任务",false).setOnClickListener(v->TaskStoreUpdate(j,"paused"));else{button(tasks,"继续 / 重试",false).setOnClickListener(v->TaskStoreUpdate(j,"queued"));button(tasks,"移除任务及临时文件",false).setOnClickListener(v->new PrivateDialogBuilder(this).setTitle("移除这个任务？").setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{TransferService.deleteFolder(TaskStore.folder(this,j));taskKey="";render();}).show());}}}
    private String stateName(String s){return "queued".equals(s)?"排队中":"running".equals(s)?"正在处理":"paused".equals(s)?"已暂停":"失败";}
    private void TaskStoreUpdate(TaskStore.Job j,String state){if(privateReady()){TransferService.update(this,j,state);taskKey="";render();}}
    private void export(File file){exporting=file;String ext=file.getName().substring(file.getName().lastIndexOf('.'));try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime(file)).putExtra(Intent.EXTRA_TITLE,exportName(file,ext)),EXPORT);}catch(ActivityNotFoundException failure){showError("系统未找到保存位置选择器");}}
    private String exportName(File f,String ext){String name="全能视频";for(TaskStore.Job j:TaskStore.list(this))if(j.id.equals(f.getParentFile().getName())){name=ToolRequest.label(j.mode)+"-"+j.title;break;}name=name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(name.length()>60)name=name.substring(0,60);return name+ext;}
    private String mime(File file){String name=file.getName();return name.endsWith(".jpg")?"image/jpeg":name.endsWith(".m4a")?"audio/mp4":name.endsWith(".webm")?"video/webm":name.endsWith(".mkv")?"video/x-matroska":"video/mp4";}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==CONVERT)pendingTool=null;return;}whenUnlocked(()->{
        Uri uri=data.getData();if(request==CONVERT){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}ToolRequest chosen=pendingTool;pendingTool=null;if(chosen==null){showError("本次操作已过期，请重新选择工具");return;}try{start(chosen.mode,chosen.options(uri.toString(),LibraryActivity.name(this,uri)).toString());}catch(Exception e){showError("工具参数无效，请重新选择");}}

        else if(request==EXPORT&&exporting!=null){File input=exporting;exporting=null;exportingNow=true;exportNotice="";status.setText("正在保存到所选位置…");copying.execute(()->{boolean good=false;try(InputStream source=new FileInputStream(input);OutputStream output=getContentResolver().openOutputStream(uri,"w")){if(output==null)throw new IOException();byte[] block=new byte[65536];int n;while((n=source.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException();output.write(block,0,n);}output.flush();good=true;}catch(IOException|SecurityException ignored){}final boolean saved=good;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;exportingNow=false;exportNotice=saved?"已保存到手机所选位置":"";showError(saved?"":"保存失败，请检查文件权限或手机空间");Toast.makeText(this,saved?"视频已保存到选择的位置":"保存失败",Toast.LENGTH_LONG).show();});});}
    });}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("url",url.getText().toString());state.putString("start",startTime.getText().toString());state.putString("end",endTime.getText().toString());state.putString("formError",formError);if(pendingTool!=null)try{state.putString("pendingTool",pendingTool.saved());}catch(Exception ignored){}if(exporting!=null)state.putString("exporting",exporting.getAbsolutePath());}
    private TextView text(LinearLayout box,String value,int size,int color,int top){TextView view=PlayerUi.text(this,value,size,color);view.setPadding(0,dp(top),0,dp(6));box.addView(view);return view;}
    private Button button(LinearLayout box,String label,boolean primary){Button button=PlayerUi.chip(this,label);if(primary){button.setTextColor(PlayerUi.BG);button.setBackground(PlayerUi.feedback(this,PlayerUi.ACCENT,12));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);box.addView(button,p);return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onPrivateUnlocked(){historyKey="";if(history!=null)render();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);copying.shutdownNow();super.onDestroy();}
}
