package app.quanneng;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.webkit.CookieManager;
import android.widget.*;
import org.json.*;
import java.util.*;

/** 默认原生播放页。网络媒体通过 Android 媒体栈加载，不受网页跨域策略限制。 */
public final class SystemPlayerActivity extends Activity {
    private android.window.OnBackInvokedCallback backCallback;
    private VideoView video;
    private View touchLayer;
    private LinearLayout root,header,controls,overlayHeader,details;
    private ScrollView detailScroll;
    private FrameLayout videoFrame;
    private TextView title,overlayTitle,position,total,pointALabel,pointBLabel;
    private Button loopButton,speedButton;
    private ImageButton playButton,fullButton;
    private ProgressBar loading;
    private SeekBar progress;
    private MediaPlayer media;
    private Uri current;
    private ArrayList<String> queue;
    private long pointA=0,pointB=0;
    private boolean looping=false,ready=false,failed=false,foreground=false,fullscreen=false,scrubbing=false,chromeVisible=true,resumeOnForeground=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable hideControls=()->{AccessibilityManager a=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);if(fullscreen&&ready&&video.isPlaying()&&!scrubbing&&(a==null||!a.isTouchExplorationEnabled()))setChrome(false);};
    private final Runnable tick=new Runnable(){public void run(){
        if(ready){int now=video.getCurrentPosition();if(looping&&now>=pointB){seekExact((int)pointA);now=(int)pointA;}
            if(!scrubbing){position.setText(time(now));progress.setProgress(video.getDuration()>0?(int)(1000L*now/video.getDuration()):0);}
            total.setText(time(video.getDuration()));progress.setSecondaryProgress(video.getBufferPercentage()*10);updatePlay();
        }
        handler.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildPlayer();configureMedia();if(Build.VERSION.SDK_INT>=33){backCallback=()->{if(fullscreen)setFullscreen(false);else finish();};getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);}
        queue=getIntent().getStringArrayListExtra("queue");current=getIntent().getData();if(current==null){finish();return;}load(current,true);
    }
    private void buildPlayer(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(PlayerUi.BG);setContentView(root);
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(4),dp(12),dp(4));root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        ImageButton close=PlayerUi.icon(this,"back","返回播放列表",false);header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->finish());
        LinearLayout names=new LinearLayout(this);names.setOrientation(1);names.setPadding(dp(8),0,0,0);header.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        names.addView(PlayerUi.text(this,"全能  /  正在播放",12,PlayerUi.MUTED));title=PlayerUi.text(this,"",16,PlayerUi.TEXT);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);names.addView(title);
        videoFrame=new FrameLayout(this);videoFrame.setBackground(PlayerUi.surface(this,Color.BLACK,18));videoFrame.setClipToOutline(true);root.addView(videoFrame);
        video=new VideoView(this);video.setId(1000);video.setContentDescription("视频画面，点击显示或隐藏全屏控制");FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);videoFrame.addView(video,vp);video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        loading=new ProgressBar(this);loading.setIndeterminateTintList(ColorStateList.valueOf(PlayerUi.ACCENT));videoFrame.addView(loading,new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER));
        touchLayer=new View(this);touchLayer.setId(1010);touchLayer.setContentDescription("显示或隐藏播放控制");touchLayer.setFocusable(true);videoFrame.addView(touchLayer,new FrameLayout.LayoutParams(-1,-1));touchLayer.setOnClickListener(v->{if(fullscreen)setChrome(!chromeVisible);});
        buildControls();buildDetails();buildOverlayHeader();
        root.post(this::sizePlayer);root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)sizePlayer();});
        root.setOnApplyWindowInsetsListener((view,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(fullscreen?WindowInsets.Type.displayCutout():WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return insets;});
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
    }
    private void buildControls(){
        controls=new LinearLayout(this);controls.setId(1006);controls.setOrientation(1);controls.setPadding(dp(12),dp(4),dp(12),dp(8));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(136),Gravity.BOTTOM);videoFrame.addView(controls,cp);
        LinearLayout times=row(controls);position=PlayerUi.text(this,"00:00:00",12,PlayerUi.TEXT);position.setTypeface(Typeface.MONOSPACE);times.addView(position,new LinearLayout.LayoutParams(0,dp(20),1));total=PlayerUi.text(this,"00:00:00",12,PlayerUi.MUTED);total.setTypeface(Typeface.MONOSPACE);times.addView(total);
        progress=new SeekBar(this);progress.setId(1007);progress.setMax(1000);progress.setContentDescription("视频进度");progress.setProgressTintList(ColorStateList.valueOf(PlayerUi.ACCENT));progress.setThumbTintList(ColorStateList.valueOf(PlayerUi.ACCENT));progress.setSecondaryProgressTintList(ColorStateList.valueOf(PlayerUi.BORDER));progress.setPadding(dp(8),0,dp(8),0);controls.addView(progress,new LinearLayout.LayoutParams(-1,dp(48)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){scrubbing=true;handler.removeCallbacks(hideControls);}public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&ready)position.setText(time((long)p*video.getDuration()/1000));}public void onStopTrackingTouch(SeekBar s){if(ready){clearLoop();seekExact((int)((long)s.getProgress()*video.getDuration()/1000));}scrubbing=false;scheduleHide();}});
        LinearLayout buttons=row(controls);buttons.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton rewind=PlayerUi.icon(this,"rewind","后退10秒",false);buttons.addView(rewind,new LinearLayout.LayoutParams(dp(48),dp(48)));rewind.setOnClickListener(v->skip(-10));
        playButton=PlayerUi.icon(this,"play","播放",true);playButton.setId(1004);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(56),dp(56));pp.setMargins(dp(4),0,dp(4),0);buttons.addView(playButton,pp);playButton.setOnClickListener(v->togglePlay());
        ImageButton forward=PlayerUi.icon(this,"forward","前进10秒",false);buttons.addView(forward,new LinearLayout.LayoutParams(dp(48),dp(48)));forward.setOnClickListener(v->skip(10));
        View space=new View(this);buttons.addView(space,new LinearLayout.LayoutParams(0,1,1));
        speedButton=PlayerUi.chip(this,"1.0×");buttons.addView(speedButton,new LinearLayout.LayoutParams(dp(56),dp(48)));speedButton.setContentDescription("选择播放速度");speedButton.setOnClickListener(v->chooseSpeed());
        fullButton=PlayerUi.icon(this,"fullscreen","进入全屏",false);fullButton.setId(1005);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(48),dp(48));fp.leftMargin=dp(4);buttons.addView(fullButton,fp);fullButton.setOnClickListener(v->setFullscreen(!fullscreen));
    }
    private void buildDetails(){
        detailScroll=new ScrollView(this);detailScroll.setId(1008);detailScroll.setFillViewport(false);root.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
        details=new LinearLayout(this);details.setOrientation(1);details.setPadding(dp(16),dp(20),dp(16),dp(24));detailScroll.addView(details);
        LinearLayout precise=card("精准跳转","按你的节奏，回看每一刻");
        for(int direction:new int[]{-1,1}){LinearLayout skips=row(precise);for(int seconds:direction<0?new int[]{600,60,10}:new int[]{10,60,600}){int delta=direction*seconds;Button b=PlayerUi.chip(this,(direction<0?"− ":"+ ")+(seconds==600?"10 分钟":seconds==60?"1 分钟":"10 秒"));addWeighted(skips,b,48);b.setContentDescription((direction<0?"后退":"前进")+seconds+"秒");b.setOnClickListener(v->skip(delta));}}
        LinearLayout ab=card("A—B 区间循环","标记起终点，让精彩反复发生");LinearLayout marks=row(ab);
        Button a=PlayerUi.chip(this,"设置 A · 起点");a.setId(1001);addWeighted(marks,a,52);a.setOnClickListener(v->{if(ready){pointA=video.getCurrentPosition();clearLoop();updatePoints();}});
        Button b=PlayerUi.chip(this,"设置 B · 终点");b.setId(1002);addWeighted(marks,b,52);b.setOnClickListener(v->{if(ready){pointB=video.getCurrentPosition();clearLoop();updatePoints();}});
        LinearLayout values=row(ab);pointALabel=PlayerUi.text(this,"00:00:00",16,PlayerUi.TEXT);pointBLabel=PlayerUi.text(this,"00:00:00",16,PlayerUi.TEXT);pointALabel.setTypeface(Typeface.MONOSPACE);pointBLabel.setTypeface(Typeface.MONOSPACE);addWeighted(values,pointALabel,36);addWeighted(values,pointBLabel,36);pointALabel.setGravity(Gravity.CENTER);pointBLabel.setGravity(Gravity.CENTER);
        loopButton=PlayerUi.chip(this,"开启区间循环");loopButton.setId(1003);loopButton.setTextColor(PlayerUi.BG);loopButton.setBackground(PlayerUi.feedback(this,PlayerUi.ACCENT,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);ab.addView(loopButton,lp);loopButton.setOnClickListener(v->{if(!ready||pointB<=pointA){toast("先在不同时间设置 A、B 点");return;}looping=!looping;updatePoints();if(looping){seekExact((int)pointA);video.start();updatePlay();}});
        LinearLayout extras=row(details);Button sub=PlayerUi.chip(this,"导入字幕");addWeighted(extras,sub,48);sub.setOnClickListener(v->openSubtitles());Button reset=PlayerUi.chip(this,"重置取点");addWeighted(extras,reset,48);reset.setOnClickListener(v->{pointA=pointB=0;clearLoop();updatePoints();});
        TextView hint=PlayerUi.text(this,"全屏观看时，轻点画面唤出控制。返回键先退出全屏。",12,PlayerUi.MUTED);hint.setLineSpacing(dp(4),1);hint.setPadding(dp(4),dp(16),dp(4),0);details.addView(hint);
    }
    private LinearLayout card(String heading,String subtitle){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable bg=PlayerUi.surface(this,PlayerUi.SURFACE,20);bg.setStroke(dp(1),PlayerUi.BORDER);card.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);details.addView(card,p);TextView h=PlayerUi.text(this,heading,17,PlayerUi.TEXT);h.setTypeface(null,Typeface.BOLD);card.addView(h);TextView sub=PlayerUi.text(this,subtitle,12,PlayerUi.MUTED);sub.setPadding(0,dp(6),0,dp(12));card.addView(sub);return card;}
    private LinearLayout row(LinearLayout parent){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    private void addWeighted(LinearLayout row,View view,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(view,p);}
    private void buildOverlayHeader(){overlayHeader=new LinearLayout(this);overlayHeader.setGravity(Gravity.CENTER_VERTICAL);overlayHeader.setPadding(dp(12),dp(4),dp(16),dp(4));overlayHeader.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xD9000000,0x00000000}));videoFrame.addView(overlayHeader,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));ImageButton back=PlayerUi.icon(this,"back","退出全屏",false);back.setId(1009);overlayHeader.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->setFullscreen(false));overlayTitle=PlayerUi.text(this,"",14,PlayerUi.TEXT);overlayTitle.setSingleLine();overlayTitle.setEllipsize(TextUtils.TruncateAt.END);overlayHeader.addView(overlayTitle,new LinearLayout.LayoutParams(0,-2,1));overlayHeader.setVisibility(View.GONE);}
    private void configureMedia(){
        video.setOnPreparedListener(mp->{media=mp;ready=true;failed=false;loading.setVisibility(View.GONE);progress.setEnabled(video.getDuration()>0);playButton.setEnabled(true);handler.removeCallbacks(tick);handler.post(tick);
            int resume=getIntent().getIntExtra("position",0);if(resume>0&&resume<video.getDuration()-2000)seekExact(resume);
            if(foreground)video.start();updatePlay();scheduleHide();
        });
        video.setOnCompletionListener(mp->{if(looping){seekExact((int)pointA);video.start();return;}next();setChrome(true);updatePlay();});
        video.setOnErrorListener((mp,what,extra)->{showFailure(extra);return true;});
    }
    private void sizePlayer(){
        int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();if(width<=0)return;
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,fullscreen?0:(int)((width-dp(24))*9f/16f)+dp(136),fullscreen?1:0);if(!fullscreen)p.setMargins(dp(12),0,dp(12),0);videoFrame.setLayoutParams(p);
        FrameLayout.LayoutParams vp=(FrameLayout.LayoutParams)video.getLayoutParams();vp.gravity=fullscreen?Gravity.CENTER:Gravity.TOP|Gravity.CENTER_HORIZONTAL;vp.bottomMargin=fullscreen?0:dp(136);video.setLayoutParams(vp);videoFrame.setBackground(PlayerUi.surface(this,Color.BLACK,fullscreen?0:18));
        controls.setBackground(fullscreen?new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x10000000,0xE6000000}):PlayerUi.surface(this,PlayerUi.SURFACE,0));
    }
    private void setFullscreen(boolean value){
        fullscreen=value;header.setVisibility(value?View.GONE:View.VISIBLE);detailScroll.setVisibility(value?View.GONE:View.VISIBLE);setChrome(true);PlayerUi.setIcon(fullButton,value?"collapse":"fullscreen",value?"退出全屏":"进入全屏",false);
        setRequestedOrientation(value?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);applySystemBars();sizePlayer();root.post(this::sizePlayer);scheduleHide();
    }
    private void applySystemBars(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(fullscreen)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(fullscreen?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:0);
        root.requestApplyInsets();
    }
    private void setChrome(boolean show){chromeVisible=show;controls.setVisibility(show?View.VISIBLE:View.GONE);overlayHeader.setVisibility(fullscreen&&show?View.VISIBLE:View.GONE);if(show)scheduleHide();}
    private void scheduleHide(){handler.removeCallbacks(hideControls);if(fullscreen)handler.postDelayed(hideControls,4000);}
    private void updatePlay(){PlayerUi.setIcon(playButton,video.isPlaying()?"pause":"play",video.isPlaying()?"暂停":"播放",true);}
    private void togglePlay(){if(!ready)return;if(video.isPlaying())video.pause();else video.start();updatePlay();setChrome(true);}
    private void skip(int seconds){if(!ready||video.getDuration()<=0)return;clearLoop();seekExact(Math.max(0,Math.min(video.getDuration(),video.getCurrentPosition()+seconds*1000)));setChrome(true);}
    private void updatePoints(){pointALabel.setText(time(pointA));pointBLabel.setText(time(pointB));loopButton.setText(looping?"循环播放中 · 点击关闭":"开启区间循环");loopButton.setSelected(looping);loopButton.setContentDescription(loopButton.getText());}
    private void openSubtitles(){if(!ready){toast("请等待视频加载");return;}try{startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),44);}catch(ActivityNotFoundException e){toast("系统未找到文件选择器");}}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);root.post(this::sizePlayer);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&fullscreen)applySystemBars();}
    @Override public void onBackPressed(){if(fullscreen){setFullscreen(false);return;}super.onBackPressed();}
    private void seekExact(int value){try{if(media!=null)media.seekTo((long)value,MediaPlayer.SEEK_CLOSEST);else video.seekTo(value);}catch(IllegalStateException error){toast("视频尚未准备好，请稍后重试");}}
    private void clearLoop(){looping=false;updatePoints();}
    private void load(Uri uri,boolean initial){
        current=uri;ready=false;failed=false;media=null;pointA=pointB=0;clearLoop();title.setText(displayName(uri));title.setContentDescription(displayName(uri));overlayTitle.setText(displayName(uri));position.setText("连接中…");loading.setVisibility(View.VISIBLE);playButton.setEnabled(false);progress.setEnabled(false);progress.setProgress(0);speedButton.setText("1.0×");
        HashMap<String,String> headers=new HashMap<>();
        if(initial){try{JSONObject data=new JSONObject(getIntent().getStringExtra("headers")==null?"{}":getIntent().getStringExtra("headers"));Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();headers.put(k,data.getString(k));}}catch(JSONException ignored){}
            String cookie=getIntent().getStringExtra("cookie");if(cookie!=null)headers.put("Cookie",cookie);
        }
        if("https".equals(uri.getScheme())){String cookie=CookieManager.getInstance().getCookie(uri.toString());if(cookie!=null)headers.put("Cookie",cookie);String agent=getIntent().getStringExtra("userAgent");if(agent!=null)headers.put("User-Agent",agent);}
        try{video.setVideoURI(uri,PlaybackHeaders.sanitize(headers));}catch(RuntimeException error){showFailure(-1004);}
        handler.postDelayed(()->{if(current==uri&&!ready&&!failed)showFailure(-110);},40000);
    }
    private String displayName(Uri uri){
        if("content".equals(uri.getScheme()))try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}
        String path=uri.getLastPathSegment();return path==null?"全能播放器":path;
    }
    private void showFailure(int code){
        if(failed||isFinishing())return;failed=true;ready=false;loading.setVisibility(View.GONE);playButton.setEnabled(false);progress.setEnabled(false);setChrome(true);video.stopPlayback();
        String reason=code==-1010?"设备不支持此视频编码。":code==-1007?"视频数据格式不正确或文件损坏。":code==-110?"加载超时，请检查网络和链接是否过期。":"视频地址已失效、需要来源授权，或网络连接失败。";
        position.setText(reason);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("视频加载失败").setMessage(reason+"\n可以重新加载，或返回原网页重新获取地址。\n诊断代码："+code)
            .setPositiveButton("重试",(d,w)->load(current,true)).setNegativeButton("返回",(d,w)->finish());
        String page=getIntent().getStringExtra("sourcePage");
        if(MainActivity.isHttps(page))dialog.setNeutralButton("打开原网页",(d,w)->{startActivity(new Intent(this,BrowserActivity.class).putExtra("url",page));finish();});
        else if("https".equals(current.getScheme()))dialog.setNeutralButton("网页内尝试",(d,w)->{Intent result=resultData();result.putExtra("webFallback",true);setResult(RESULT_OK,result);super.finish();});
        dialog.show();
    }
    private void next(){
        if(getIntent().getBooleanExtra("autoNext",true)&&queue!=null){int index=queue.indexOf(current.toString());if(index>=0&&index+1<queue.size()){getIntent().putExtra("position",0);load(Uri.parse(queue.get(index+1)),false);return;}}
        updatePlay();
    }
    private void chooseSpeed(){if(!ready||media==null)return;String[] labels={"0.5 倍速","0.75 倍速","1.0 倍速","1.25 倍速","1.5 倍速","2.0 倍速","3.0 倍速"};float[] values={.5f,.75f,1f,1.25f,1.5f,2f,3f};new AlertDialog.Builder(this).setTitle("播放速度").setItems(labels,(d,i)->{try{boolean wasPlaying=video.isPlaying();media.setPlaybackParams(media.getPlaybackParams().setSpeed(values[i]));if(!wasPlaying)video.pause();speedButton.setText(values[i]+"×");setChrome(true);}catch(RuntimeException e){toast("此格式不支持所选速度");}}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=44||result!=RESULT_OK||data==null||data.getData()==null||!ready)return;
        try{Uri uri=data.getData();String name=displayName(uri).toLowerCase(Locale.ROOT);String mime=name.endsWith(".vtt")?"text/vtt":"application/x-subrip";
            video.addSubtitleSource(getContentResolver().openInputStream(uri),MediaFormat.createSubtitleFormat(mime,"zh"));toast("已导入字幕，按系统字幕显示设置呈现");
        }catch(Exception error){toast("字幕导入失败，请选择 UTF-8 的 SRT 或 VTT 文件");}
    }
    private Intent resultData(){return new Intent().putExtra("url",current==null?"":current.toString()).putExtra("position",video==null?0:video.getCurrentPosition());}
    @Override public void finish(){if(current!=null)setResult(RESULT_OK,resultData());super.finish();}
    private String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();foreground=true;if(ready&&resumeOnForeground){video.start();resumeOnForeground=false;}if(fullscreen)applySystemBars();}
    @Override protected void onPause(){foreground=false;resumeOnForeground=ready&&video.isPlaying();video.pause();handler.removeCallbacks(hideControls);super.onPause();}
    @Override protected void onDestroy(){if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);handler.removeCallbacksAndMessages(null);video.stopPlayback();super.onDestroy();}
}
