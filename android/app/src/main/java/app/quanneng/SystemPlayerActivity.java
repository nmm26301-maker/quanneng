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
import tv.danmaku.ijk.media.exo2.IjkExo2MediaPlayer;
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

/** 默认 GSYVideoPlayer + Media3 播放页；保留全能的全屏、跳转和循环控制。 */
public final class SystemPlayerActivity extends PrivateActivity {
    private android.window.OnBackInvokedCallback backCallback;
    private MatureVideoView video;
    private View touchLayer;
    private LinearLayout root,header,controls,overlayHeader,details,fullscreenJumps;
    private ScrollView detailScroll;
    private FrameLayout videoFrame;
    private TextView title,overlayTitle,overlayClock,position,total,pointALabel,pointBLabel,jumpFeedback;
    private long clockUpdatedAt;
    private AlertDialog jumpDialog;
    private Button loopButton,speedButton,jumpSettings;
    private int[] jumpValues=JumpSteps.DEFAULTS.clone();
    private ImageButton playButton,fullButton,screenLockButton,unlockTouchButton;
    private View touchShield;
    private LinearLayout unlockTouchArea;
    private boolean touchLocked;
    private ProgressBar loading;
    private PrecisionSeekBar progress;
    private LinearLayout controlButtons;
    private View controlSpace;
    private int safeLeft,safeRight,safeBottom,safeTop;
    private IjkExo2MediaPlayer media;
    private Uri current;
    private float selectedSpeed=1f;
    private long savedAt,mediaEpoch;
    private String activeSubtitle="",subtitleStamp="";
    private boolean initialSeek,restoringVault;
    private final java.util.concurrent.ExecutorService vaultIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private ArrayList<String> queue;
    private final ArrayList<Button> jumpButtons=new ArrayList<>();
    private int jumpTarget=-1,feedbackEpoch=0;
    private long lastJumpAt=0;
    private long pointA=0,pointB=0;
    private boolean looping=false,ready=false,failed=false,foreground=false,fullscreen=false,scrubbing=false,chromeVisible=true,resumeOnForeground=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable hideControls=()->{AccessibilityManager a=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);if(fullscreen&&getWindow().getDecorView().hasWindowFocus()&&ready&&video.isPlaying()&&!scrubbing&&(a==null||!a.isTouchExplorationEnabled()))setChrome(false);};
    private final Runnable hideJump=()->{final int epoch=feedbackEpoch;jumpFeedback.animate().cancel();if(PlayerUi.motionEnabled())jumpFeedback.animate().alpha(0).translationY(-dp(4)).setDuration(180).withEndAction(()->{if(epoch==feedbackEpoch)jumpFeedback.setVisibility(View.GONE);}).start();else jumpFeedback.setVisibility(View.GONE);};
    private final Runnable tick=new Runnable(){public void run(){
        if(ready){if(SystemClock.uptimeMillis()-savedAt>3000){rememberPosition();savedAt=SystemClock.uptimeMillis();}int now=video.getCurrentPosition();if(looping&&now>=pointB){seekExact((int)pointA);now=(int)pointA;}
            if(!scrubbing){position.setText(time(now));progress.setProgress(now/1000);}
            total.setText(time(video.getDuration()));progress.setSecondaryProgress((int)((long)progress.getMax()*video.getBufferPercentage()/100));updatePlay();
        }
        updateOverlayClock();handler.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        jumpValues=JumpSteps.load(this);buildPlayer();configureMedia();if(Build.VERSION.SDK_INT>=33){backCallback=()->{if(touchLocked){lockedHint();return;}if(fullscreen)setFullscreen(false);else finish();};getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);}
        queue=getIntent().getStringArrayListExtra("queue");current=getIntent().getData();if(current==null){finish();return;}whenUnlocked(()->{if(!restoreVault())load(current,true);});
    }
    private void buildPlayer(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(PlayerUi.BG);setContentView(root);
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(4),dp(12),dp(4));root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        ImageButton close=PlayerUi.icon(this,"back","返回播放列表",false);header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->finish());
        LinearLayout names=new LinearLayout(this);names.setOrientation(1);names.setPadding(dp(8),0,0,0);header.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        names.addView(PlayerUi.text(this,"全能  /  正在播放",12,PlayerUi.MUTED));title=PlayerUi.text(this,"",16,PlayerUi.TEXT);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);names.addView(title);
        videoFrame=new FrameLayout(this);videoFrame.setBackground(PlayerUi.surface(this,Color.BLACK,18));videoFrame.setClipToOutline(true);root.addView(videoFrame);
        video=new MatureVideoView(this);video.setId(1000);video.setContentDescription("视频画面，点击显示或隐藏全屏控制");FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);videoFrame.addView(video,vp);video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        loading=new ProgressBar(this);loading.setIndeterminateTintList(ColorStateList.valueOf(PlayerUi.ACCENT));videoFrame.addView(loading,new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER));
        touchLayer=new View(this);touchLayer.setId(1010);touchLayer.setContentDescription("显示或隐藏播放控制");touchLayer.setFocusable(true);videoFrame.addView(touchLayer,new FrameLayout.LayoutParams(-1,-1));touchLayer.setOnClickListener(v->{if(fullscreen)setChrome(!chromeVisible);});
        touchLayer.setOnTouchListener(new PlayerGestures(this,new PlayerGestures.Target(){public boolean ready(){return SystemPlayerActivity.this.ready;}public int position(){return video.getCurrentPosition();}public int duration(){return video.getDuration();}public void seek(int ms){clearLoop();seekExact(ms);setChrome(true);}public void toggle(){togglePlay();}public void tap(){if(fullscreen)setChrome(!chromeVisible);}public void feedback(String s){showJumpFeedback(s);}public float speed(){return selectedSpeed;}public void speed(float f){if(media!=null){try{media.setSpeed(f,1f);selectedSpeed=f;speedButton.setText(f+"×");}catch(RuntimeException ignored){}}}}));
        buildControls();buildDetails();buildOverlayHeader();
        jumpFeedback=PlayerUi.text(this,"",14,PlayerUi.TEXT);jumpFeedback.setId(1300);jumpFeedback.setGravity(Gravity.CENTER);jumpFeedback.setPadding(dp(18),dp(12),dp(18),dp(12));jumpFeedback.setBackground(PlayerUi.surface(this,0xEF14251E,16));jumpFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);jumpFeedback.setVisibility(View.GONE);videoFrame.addView(jumpFeedback,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        touchShield=new View(this);touchShield.setId(1311);touchShield.setVisibility(View.GONE);touchShield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);touchShield.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP)lockedHint();return true;});videoFrame.addView(touchShield,new FrameLayout.LayoutParams(-1,-1));
        unlockTouchArea=new LinearLayout(this);unlockTouchArea.setOrientation(1);unlockTouchArea.setGravity(Gravity.CENTER);unlockTouchArea.setPadding(dp(4),dp(4),dp(4),dp(4));unlockTouchArea.setBackground(PlayerUi.surface(this,0x66000000,16));unlockTouchArea.setVisibility(View.GONE);videoFrame.addView(unlockTouchArea,new FrameLayout.LayoutParams(dp(76),dp(88),Gravity.END|Gravity.CENTER_VERTICAL));
        unlockTouchButton=PlayerUi.icon(this,"lock","长按解除防误触锁定",false);unlockTouchButton.setId(1312);unlockTouchArea.addView(unlockTouchButton,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView unlockHint=PlayerUi.text(this,"长按解锁",12,PlayerUi.TEXT);unlockHint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);unlockTouchArea.addView(unlockHint);unlockTouchButton.setOnClickListener(v->lockedHint());unlockTouchButton.setOnLongClickListener(v->{setTouchLocked(false);v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);return true;});
        root.post(this::sizePlayer);root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)sizePlayer();});
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());android.graphics.Insets stable=insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());android.graphics.Insets gestures=insets.getInsets(WindowInsets.Type.systemGestures());safeLeft=Math.max(stable.left,gestures.left);safeRight=Math.max(stable.right,gestures.right);safeBottom=Math.max(stable.bottom,gestures.bottom);safeTop=stable.top;view.setPadding(fullscreen?0:bars.left,fullscreen?0:bars.top,fullscreen?0:bars.right,fullscreen?0:bars.bottom);}
            else{safeLeft=insets.getStableInsetLeft();safeRight=insets.getStableInsetRight();safeBottom=insets.getStableInsetBottom();safeTop=insets.getStableInsetTop();}
            sizePlayer();return insets;});
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
    }
    private void buildControls(){
        controls=new LinearLayout(this);controls.setId(1006);controls.setOrientation(1);controls.setPadding(dp(12),dp(4),dp(12),dp(8));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(136),Gravity.BOTTOM);videoFrame.addView(controls,cp);
        LinearLayout times=row(controls);position=PlayerUi.text(this,"00:00:00",12,PlayerUi.TEXT);position.setTypeface(Typeface.MONOSPACE);times.addView(position,new LinearLayout.LayoutParams(0,dp(20),1));total=PlayerUi.text(this,"00:00:00",12,PlayerUi.MUTED);total.setTypeface(Typeface.MONOSPACE);times.addView(total);
        progress=new PrecisionSeekBar(this);progress.setId(1007);progress.setMax(1);progress.setContentDescription("视频进度，左右拖动按秒定位，上移慢调");progress.setProgressTintList(ColorStateList.valueOf(PlayerUi.ACCENT));progress.setThumbTintList(ColorStateList.valueOf(PlayerUi.ACCENT));progress.setSecondaryProgressTintList(ColorStateList.valueOf(PlayerUi.BORDER));progress.setPadding(dp(8),0,dp(8),0);controls.addView(progress,new LinearLayout.LayoutParams(-1,dp(48)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){scrubbing=true;handler.removeCallbacks(hideControls);}public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&ready){if(!scrubbing){clearLoop();seekExact((int)Math.min(video.getDuration(),p*1000L));}position.setText(time(p*1000L));showJumpFeedback("松手定位 · "+time(p*1000L)+(progress.fine()?" · 精细 1 秒":" · 按秒拖动"));}}public void onStopTrackingTouch(SeekBar s){if(ready&&!progress.cancelled()){clearLoop();seekExact((int)Math.min(video.getDuration(),s.getProgress()*1000L));}scrubbing=false;scheduleHide();}});
        fullscreenJumps=new LinearLayout(this);fullscreenJumps.setId(1250);for(int index=0;index<4;index++){Button b=makeJumpButton(index,true,1200+index);addWeighted(fullscreenJumps,b,48);}fullscreenJumps.setVisibility(View.GONE);
        LinearLayout buttons=row(controls);controlButtons=buttons;buttons.setGravity(Gravity.CENTER_VERTICAL);
        playButton=PlayerUi.icon(this,"play","播放",true);playButton.setId(1004);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(56),dp(56));pp.setMargins(dp(4),0,dp(4),0);buttons.addView(playButton,pp);playButton.setOnClickListener(v->togglePlay());
        screenLockButton=PlayerUi.icon(this,"lock","防误触锁定（进入全屏）",false);screenLockButton.setId(1310);screenLockButton.setEnabled(false);buttons.addView(screenLockButton,new LinearLayout.LayoutParams(dp(48),dp(48)));screenLockButton.setOnClickListener(v->{if(ready){if(!fullscreen)setFullscreen(true);setTouchLocked(true);}});
        buttons.addView(fullscreenJumps,new LinearLayout.LayoutParams(0,dp(56),1));controlSpace=new View(this);buttons.addView(controlSpace,new LinearLayout.LayoutParams(0,1,1));
        jumpSettings=PlayerUi.chip(this,"步长");jumpSettings.setId(1251);jumpSettings.setContentDescription("设置四个跳转时间");buttons.addView(jumpSettings,new LinearLayout.LayoutParams(dp(56),dp(48)));jumpSettings.setOnClickListener(v->chooseJumpSteps());
        speedButton=PlayerUi.chip(this,"1.0×");buttons.addView(speedButton,new LinearLayout.LayoutParams(dp(56),dp(48)));speedButton.setContentDescription("选择播放速度");speedButton.setOnClickListener(v->chooseSpeed());
        fullButton=PlayerUi.icon(this,"fullscreen","进入全屏",false);fullButton.setId(1005);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(48),dp(48));fp.leftMargin=dp(4);buttons.addView(fullButton,fp);fullButton.setOnClickListener(v->setFullscreen(!fullscreen));
    }
    private void buildDetails(){
        detailScroll=new ScrollView(this);detailScroll.setId(1008);detailScroll.setFillViewport(false);root.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
        details=new LinearLayout(this);details.setOrientation(1);details.setPadding(dp(16),dp(20),dp(16),dp(24));detailScroll.addView(details);
        LinearLayout precise=card("精准跳转","按你的节奏，回看每一刻");
        TextView heading=(TextView)precise.getChildAt(0);precise.removeView(heading);LinearLayout headingRow=new LinearLayout(this);headingRow.setGravity(Gravity.CENTER_VERTICAL);headingRow.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));Button settings=PlayerUi.chip(this,"设置时间");settings.setId(1151);settings.setOnClickListener(v->chooseJumpSteps());headingRow.addView(settings,new LinearLayout.LayoutParams(dp(96),dp(48)));precise.addView(headingRow,0);
        for(int row=0;row<2;row++){LinearLayout skips=row(precise);for(int column=0;column<2;column++){int index=row*2+column;addWeighted(skips,makeJumpButton(index,false,1100+index),64);}}

        LinearLayout ab=card("A—B 区间循环","标记起终点，让精彩反复发生");LinearLayout marks=row(ab);
        Button a=PlayerUi.chip(this,"设置 A · 起点");a.setId(1001);addWeighted(marks,a,52);a.setOnClickListener(v->{if(ready){pointA=video.getCurrentPosition();clearLoop();updatePoints();}});
        Button b=PlayerUi.chip(this,"设置 B · 终点");b.setId(1002);addWeighted(marks,b,52);b.setOnClickListener(v->{if(ready){pointB=video.getCurrentPosition();clearLoop();updatePoints();}});
        LinearLayout values=row(ab);pointALabel=PlayerUi.text(this,"00:00:00",16,PlayerUi.TEXT);pointBLabel=PlayerUi.text(this,"00:00:00",16,PlayerUi.TEXT);pointALabel.setTypeface(Typeface.MONOSPACE);pointBLabel.setTypeface(Typeface.MONOSPACE);addWeighted(values,pointALabel,36);addWeighted(values,pointBLabel,36);pointALabel.setGravity(Gravity.CENTER);pointBLabel.setGravity(Gravity.CENTER);
        loopButton=PlayerUi.chip(this,"开启区间循环");loopButton.setId(1003);loopButton.setTextColor(PlayerUi.BG);loopButton.setBackground(PlayerUi.feedback(this,PlayerUi.ACCENT,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);ab.addView(loopButton,lp);loopButton.setOnClickListener(v->{if(!ready||pointB<=pointA){toast("先在不同时间设置 A、B 点");return;}looping=!looping;updatePoints();if(looping){seekExact((int)pointA);video.start();updatePlay();}});
        LinearLayout extras=row(details);Button sub=PlayerUi.chip(this,"导入字幕");addWeighted(extras,sub,48);sub.setOnClickListener(v->openSubtitles());Button reset=PlayerUi.chip(this,"重置取点");addWeighted(extras,reset,48);reset.setOnClickListener(v->{pointA=pointB=0;clearLoop();updatePoints();});
        LinearLayout features=card("播放工具","字幕、投屏与接力都在这里");
        Button center=PlayerUi.chip(this,"字幕中心 / 调整偏移与字号");features.addView(center,new LinearLayout.LayoutParams(-1,dp(52)));center.setOnClickListener(v->startActivityForResult(new Intent(this,SubtitleActivity.class).putExtra("pick",true),45));
        for(String kind:new String[]{"cast","handoff","diagnostics","gestures"}){Button actionButton=PlayerUi.chip(this,"cast".equals(kind)?"电视投屏":"handoff".equals(kind)?"分享接力":"diagnostics".equals(kind)?"播放诊断":"手势设置");features.addView(actionButton,new LinearLayout.LayoutParams(-1,dp(52)));actionButton.setOnClickListener(v->{if("handoff".equals(kind)){HandoffActivity.share(this,current.toString(),video.getCurrentPosition());return;}Class<?> screen="cast".equals(kind)?CastActivity.class:"diagnostics".equals(kind)?DiagnosticsActivity.class:GestureSettingsActivity.class;startActivity(new Intent(this,screen).putExtra("url",current.toString()));});}
        TextView hint=PlayerUi.text(this,"全屏轻点显示透明控制。进度条左右拖动按秒定位，上移慢调，松手生效；点按轨道可快速跳转。返回键先退出全屏。",12,PlayerUi.MUTED);hint.setLineSpacing(dp(4),1);hint.setPadding(dp(4),dp(16),dp(4),0);details.addView(hint);
    }
    private LinearLayout card(String heading,String subtitle){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable bg=PlayerUi.surface(this,PlayerUi.SURFACE,20);bg.setStroke(dp(1),PlayerUi.BORDER);card.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);details.addView(card,p);TextView h=PlayerUi.text(this,heading,17,PlayerUi.TEXT);h.setTypeface(null,Typeface.BOLD);card.addView(h);TextView sub=PlayerUi.text(this,subtitle,12,PlayerUi.MUTED);sub.setPadding(0,dp(6),0,dp(12));card.addView(sub);return card;}
    private LinearLayout row(LinearLayout parent){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    private void addWeighted(LinearLayout row,View view,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(view,p);}
    private void buildOverlayHeader(){overlayHeader=new LinearLayout(this);overlayHeader.setGravity(Gravity.CENTER_VERTICAL);overlayHeader.setPadding(dp(12),dp(4),dp(16),dp(4));overlayHeader.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xD9000000,0x00000000}));videoFrame.addView(overlayHeader,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));ImageButton back=PlayerUi.icon(this,"back","退出全屏",false);back.setId(1009);overlayHeader.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->setFullscreen(false));overlayTitle=PlayerUi.text(this,"",14,PlayerUi.TEXT);overlayTitle.setSingleLine();overlayTitle.setEllipsize(TextUtils.TruncateAt.END);overlayHeader.addView(overlayTitle,new LinearLayout.LayoutParams(0,-2,1));overlayClock=PlayerUi.text(this,"",14,PlayerUi.TEXT);overlayClock.setId(1313);overlayClock.setTypeface(Typeface.MONOSPACE);overlayClock.setPadding(dp(12),0,0,0);overlayClock.setGravity(Gravity.CENTER_VERTICAL);overlayClock.setContentDescription("当前时间");overlayHeader.addView(overlayClock,new LinearLayout.LayoutParams(-2,dp(48)));overlayHeader.setVisibility(View.GONE);}
    private void configureMedia(){
        video.setOnPreparedListener(mp->{media=(IjkExo2MediaPlayer)mp;ready=true;failed=false;loading.setVisibility(View.GONE);progress.setMax(Math.max(1,(int)((video.getDuration()+999L)/1000)));progress.setEnabled(video.getDuration()>0);setJumpsEnabled(video.getDuration()>0);playButton.setEnabled(true);screenLockButton.setEnabled(true);handler.removeCallbacks(tick);handler.post(tick);
            int resume=getIntent().hasExtra("position")?getIntent().getIntExtra("position",0):PlaybackHistory.position(this,current.toString());if(!initialSeek&&resume>0&&resume<video.getDuration()-2000)seekExact(resume);initialSeek=true;applyRememberedSubtitle();
            if(foreground)video.start();updatePlay();scheduleHide();
        });
        video.setOnCompletionListener(mp->{if(looping){seekExact((int)pointA);video.start();return;}next();setChrome(true);updatePlay();});
        video.setOnErrorListener((mp,what,extra)->{showFailure(what);return true;});
    }
    private void sizePlayer(){
        int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();if(width<=0)return;
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,fullscreen?0:(int)((width-dp(24))*9f/16f)+dp(136),fullscreen?1:0);if(!fullscreen)p.setMargins(dp(12),0,dp(12),0);videoFrame.setLayoutParams(p);
        FrameLayout.LayoutParams vp=(FrameLayout.LayoutParams)video.getLayoutParams();vp.gravity=fullscreen?Gravity.CENTER:Gravity.TOP|Gravity.CENTER_HORIZONTAL;vp.bottomMargin=fullscreen?0:dp(136);video.setLayoutParams(vp);videoFrame.setBackground(PlayerUi.surface(this,Color.BLACK,fullscreen?0:18));
        jumpSettings.setVisibility(fullscreen?View.VISIBLE:View.GONE);fullscreenJumps.setVisibility(fullscreen?View.VISIBLE:View.GONE);FrameLayout.LayoutParams cp=(FrameLayout.LayoutParams)controls.getLayoutParams();cp.height=dp(136);cp.leftMargin=fullscreen?safeLeft:0;cp.rightMargin=fullscreen?safeRight:0;cp.bottomMargin=fullscreen?safeBottom:0;controls.setLayoutParams(cp);
        LinearLayout lockParent=fullscreen?overlayHeader:controlButtons;if(screenLockButton.getParent()!=lockParent){((android.view.ViewGroup)screenLockButton.getParent()).removeView(screenLockButton);if(fullscreen)lockParent.addView(screenLockButton,Math.max(0,lockParent.getChildCount()-1),new LinearLayout.LayoutParams(dp(48),dp(48)));else lockParent.addView(screenLockButton,new LinearLayout.LayoutParams(dp(48),dp(48)));}screenLockButton.setContentDescription(fullscreen?"防误触锁定":"防误触锁定（进入全屏）");
        controlSpace.setVisibility(fullscreen?View.GONE:View.VISIBLE);LinearLayout.LayoutParams playSize=(LinearLayout.LayoutParams)playButton.getLayoutParams();playSize.width=dp(fullscreen?48:56);playSize.height=dp(fullscreen?48:56);playButton.setLayoutParams(playSize);
        for(Button button:jumpButtons)if(button.getId()>=1200)button.setBackground(PlayerUi.feedback(this,Color.TRANSPARENT,12));
        jumpSettings.setBackground(PlayerUi.feedback(this,fullscreen?Color.TRANSPARENT:PlayerUi.SURFACE,12));speedButton.setBackground(PlayerUi.feedback(this,fullscreen?Color.TRANSPARENT:PlayerUi.SURFACE,12));playButton.setBackground(PlayerUi.feedback(this,fullscreen?Color.TRANSPARENT:PlayerUi.ACCENT,28));updatePlay();
        FrameLayout.LayoutParams hp=(FrameLayout.LayoutParams)overlayHeader.getLayoutParams();hp.leftMargin=fullscreen?safeLeft:0;hp.rightMargin=fullscreen?safeRight:0;hp.topMargin=fullscreen?safeTop:0;overlayHeader.setLayoutParams(hp);
        FrameLayout.LayoutParams unlock=(FrameLayout.LayoutParams)unlockTouchArea.getLayoutParams();unlock.rightMargin=safeRight+dp(16);unlock.leftMargin=safeLeft+dp(16);unlockTouchArea.setLayoutParams(unlock);
        FrameLayout.LayoutParams feedback=(FrameLayout.LayoutParams)jumpFeedback.getLayoutParams();feedback.gravity=fullscreen?Gravity.CENTER:Gravity.TOP|Gravity.CENTER_HORIZONTAL;feedback.topMargin=fullscreen?0:Math.max(dp(16),(int)((width-dp(24))*9f/32f)-dp(24));jumpFeedback.setLayoutParams(feedback);
        controls.setBackground(fullscreen?new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x99000000}):PlayerUi.surface(this,PlayerUi.SURFACE,0));
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
    private void setChrome(boolean show){show=show&&!touchLocked;chromeVisible=show;controls.setVisibility(show?View.VISIBLE:View.GONE);overlayHeader.setVisibility(fullscreen&&show?View.VISIBLE:View.GONE);if(show){clockUpdatedAt=0;updateOverlayClock();scheduleHide();}}
    private void updateOverlayClock(){if(overlayClock==null||!fullscreen||!chromeVisible)return;long now=SystemClock.elapsedRealtime();if(now-clockUpdatedAt<1000)return;clockUpdatedAt=now;String value=android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date());if(!value.contentEquals(overlayClock.getText())){overlayClock.setText(value);overlayClock.setContentDescription("当前时间 "+value);}}
    private void scheduleHide(){handler.removeCallbacks(hideControls);if(fullscreen)handler.postDelayed(hideControls,4000);}
    private void updatePlay(){PlayerUi.setIcon(playButton,video.isPlaying()?"pause":"play",video.isPlaying()?"暂停":"播放",!fullscreen);}
    private void togglePlay(){if(!ready)return;if(video.isPlaying())video.pause();else video.start();updatePlay();setChrome(true);}
    private Button makeJumpButton(int index,boolean compact,int id){
        Button button=PlayerUi.jumpButton(this,jumpValues[index],index%2==1,index>=2,compact);button.setId(id);jumpButtons.add(button);button.setEnabled(false);button.setAlpha(.45f);
        button.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)handler.removeCallbacks(hideControls);else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)scheduleHide();return PlayerUi.pressMotion(v,event);});
        button.setOnClickListener(v->{if(ready&&video.getDuration()>0){skip(JumpSteps.delta(jumpValues,index),JumpSteps.description(jumpValues,index));v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}});return button;
    }
    private void chooseJumpSteps(){
        handler.removeCallbacks(hideControls);ScrollView scroll=new ScrollView(this);LinearLayout fields=new LinearLayout(this);fields.setPadding(dp(24),dp(8),dp(24),dp(8));fields.setOrientation(1);scroll.addView(fields);EditText[] inputs=new EditText[4];
        for(int i=0;i<4;i++){TextView caption=PlayerUi.text(this,JumpSteps.LABELS[i]+(i%2==0?"（1–3600 秒）":"（1–120 分钟）"),13,PlayerUi.MUTED);fields.addView(caption);EditText input=new EditText(this);input.setId(1700+i);input.setTextColor(PlayerUi.TEXT);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setSingleLine();input.setText(String.valueOf(jumpValues[i]));input.setContentDescription(JumpSteps.LABELS[i]);caption.setLabelFor(input.getId());fields.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));inputs[i]=input;}
        AlertDialog dialog=new PrivateDialogBuilder(this).setTitle("设置跳转时间").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();jumpDialog=dialog;dialog.setOnShowListener(visible->{Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setId(1705);save.setOnClickListener(v->{int[] next=new int[4];for(int i=0;i<4;i++){try{next[i]=Integer.parseInt(inputs[i].getText().toString());}catch(NumberFormatException ignored){next[i]=0;}if(next[i]<1||next[i]>JumpSteps.max(i)){inputs[i].setError("请输入 1–"+JumpSteps.max(i)+" 的整数");inputs[i].requestFocus();return;}}if(!JumpSteps.save(this,next)){toast("设置保存失败，请稍后重试");return;}jumpValues=next;for(Button button:jumpButtons){boolean compact=button.getId()>=1200;int index=button.getId()-(compact?1200:1100);PlayerUi.setJumpLabel(button,jumpValues[index],index%2==1,index>=2,compact);}dialog.dismiss();toast("跳转时间已保存");});});dialog.show();
    }
    private void setJumpsEnabled(boolean enabled){for(Button b:jumpButtons){b.setEnabled(enabled);b.setAlpha(enabled?1:.45f);}}
    private void skip(int seconds,String label){
        if(!ready||video.getDuration()<=0)return;long now=SystemClock.uptimeMillis();int base=jumpTarget>=0&&now-lastJumpAt<450?jumpTarget:video.getCurrentPosition();int target=(int)Math.max(0,Math.min(video.getDuration(),base+seconds*1000L));
        clearLoop();seekExact(target);jumpTarget=target;lastJumpAt=now;position.setText(time(target));progress.setProgress(target/1000);setChrome(true);
        String direction=target==0&&base+seconds*1000L<0?"已到开头":target==video.getDuration()&&base+seconds*1000L>video.getDuration()?"已到结尾":label;showJumpFeedback(direction+"  ·  "+time(target));
    }
    private void showJumpFeedback(String message){
        ++feedbackEpoch;handler.removeCallbacks(hideJump);jumpFeedback.animate().cancel();jumpFeedback.setText(message);jumpFeedback.setVisibility(View.VISIBLE);jumpFeedback.setScaleX(1);jumpFeedback.setScaleY(1);
        if(PlayerUi.motionEnabled()){jumpFeedback.setAlpha(0);jumpFeedback.setTranslationY(dp(6));jumpFeedback.animate().alpha(1).translationY(0).setDuration(140).start();}else{jumpFeedback.setAlpha(1);jumpFeedback.setTranslationY(0);}handler.postDelayed(hideJump,900);
    }
    private void updatePoints(){pointALabel.setText(time(pointA));pointBLabel.setText(time(pointB));loopButton.setText(looping?"循环播放中 · 点击关闭":"开启区间循环");loopButton.setSelected(looping);loopButton.setContentDescription(loopButton.getText());}
    private void openSubtitles(){if(!ready){toast("请等待视频加载");return;}try{startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),44);}catch(ActivityNotFoundException e){toast("系统未找到文件选择器");}}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);root.post(this::sizePlayer);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(fullscreen){if(focused){applySystemBars();setChrome(true);}else handler.removeCallbacks(hideControls);}}
    @Override public void onBackPressed(){if(touchLocked){lockedHint();return;}if(fullscreen){setFullscreen(false);return;}super.onBackPressed();}
    private void lockedHint(){showJumpFeedback("已锁定触控 · 长按右侧锁按钮解除");}
    private void setTouchLocked(boolean value){
        touchLocked=value;handler.removeCallbacks(hideControls);scrubbing=false;touchShield.setVisibility(value?View.VISIBLE:View.GONE);unlockTouchArea.setVisibility(value?View.VISIBLE:View.GONE);touchLayer.setImportantForAccessibility(value?View.IMPORTANT_FOR_ACCESSIBILITY_NO:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);setChrome(!value);
        if(value){lockedHint();unlockTouchButton.requestFocus();}else{showJumpFeedback("已解除防误触锁定");playButton.requestFocus();}
    }
    private void seekExact(int value){jumpTarget=-1;try{if(media!=null)media.seekTo((long)value);else video.seekTo(value);}catch(IllegalStateException error){toast("视频尚未准备好，请稍后重试");}}
    private void clearLoop(){looping=false;updatePoints();}
    private void load(Uri uri,boolean initial){
        rememberPosition();++mediaEpoch;activeSubtitle=PlaybackHistory.subtitle(this,uri.toString());subtitleStamp="";current=uri;initialSeek=false;selectedSpeed=1f;ready=false;failed=false;media=null;jumpTarget=-1;setJumpsEnabled(false);pointA=pointB=0;clearLoop();title.setText(displayName(uri));title.setContentDescription(displayName(uri));overlayTitle.setText(displayName(uri));position.setText("连接中…");loading.setVisibility(View.VISIBLE);playButton.setEnabled(false);screenLockButton.setEnabled(false);progress.setEnabled(false);progress.setProgress(0);speedButton.setText("1.0×");
        HashMap<String,String> headers=new HashMap<>();
        if(initial){try{JSONObject data=new JSONObject(getIntent().getStringExtra("headers")==null?"{}":getIntent().getStringExtra("headers"));Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();headers.put(k,data.getString(k));}}catch(JSONException ignored){}
            String cookie=getIntent().getStringExtra("cookie");if(cookie!=null)headers.put("Cookie",cookie);
        }
        if("https".equals(uri.getScheme())){String cookie=CookieManager.getInstance().getCookie(uri.toString());if(cookie!=null)headers.put("Cookie",cookie);String agent=getIntent().getStringExtra("userAgent");if(agent!=null)headers.put("User-Agent",agent);}
        String nas=getIntent().getStringExtra("nasAuth");if(nas!=null&&!nas.isEmpty())headers.put("Authorization",nas);
        try{if(!"content".equals(uri.getScheme()))NetworkPolicy.url(uri.toString());video.setSourceMime(initial?getIntent().getStringExtra("mime"):null);video.setVideoURI(uri,PlaybackHeaders.sanitize(headers));}catch(Exception error){showFailure(-1004);}
        handler.postDelayed(()->{if(current==uri&&!ready&&!failed)showFailure(-110);},40000);
    }
    private String displayName(Uri uri){
        if("content".equals(uri.getScheme()))try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}
        String path=uri.getLastPathSegment();return path==null?"全能播放器":path;
    }
    private void showFailure(int code){
        if(failed||isFinishing())return;if(touchLocked)setTouchLocked(false);failed=true;ready=false;setJumpsEnabled(false);loading.setVisibility(View.GONE);playButton.setEnabled(false);screenLockButton.setEnabled(false);progress.setEnabled(false);setChrome(true);video.stopPlayback();
        String reason=code==-1010?"设备不支持此视频编码。":code==-1007?"视频数据格式不正确或文件损坏。":code==-110?"加载超时，请检查网络和链接是否过期。":"视频地址已失效、需要来源授权，或网络连接失败。";
        if("http".equals(current.getScheme()))reason+=" 请确认发送端仍在共享，且两台设备连接同一 Wi-Fi。";
        position.setText(reason);
        AlertDialog.Builder dialog=new PrivateDialogBuilder(this).setTitle("视频加载失败").setMessage(reason+"\n可以重新加载，或返回原网页重新获取地址。\n诊断代码："+code)
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
    private void chooseSpeed(){if(!ready||media==null)return;String[] labels={"0.5 倍速","0.75 倍速","1.0 倍速","1.25 倍速","1.5 倍速","2.0 倍速","3.0 倍速"};float[] values={.5f,.75f,1f,1.25f,1.5f,2f,3f};new PrivateDialogBuilder(this).setTitle("播放速度").setItems(labels,(d,i)->{try{boolean wasPlaying=video.isPlaying();media.setSpeed(values[i],1f);selectedSpeed=values[i];if(!wasPlaying)video.pause();speedButton.setText(values[i]+"×");setChrome(true);}catch(RuntimeException e){toast("此格式不支持所选速度");}}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);whenUnlocked(()->handleSubtitleResult(request,result,data));
    }
    private void handleSubtitleResult(int request,int result,Intent data){if(request==45&&result==RESULT_OK&&data!=null){String name=data.getStringExtra("subtitle");if(name!=null&&current!=null){activeSubtitle=name;subtitleStamp="";PlaybackHistory.subtitle(this,current.toString(),name);applyRememberedSubtitle();}return;}
        if(request!=44||result!=RESULT_OK||data==null||data.getData()==null||!ready)return;
        try{Uri uri=data.getData();String name=displayName(uri).toLowerCase(Locale.ROOT);String mime=name.endsWith(".vtt")?"text/vtt":"application/x-subrip";
            video.addSubtitle(uri,mime);toast("已导入字幕，在画面下方显示");
        }catch(Exception error){toast("字幕导入失败，请选择 UTF-8 的 SRT 或 VTT 文件");}
    }
    private void rememberPosition(){if(ready&&current!=null)PlaybackHistory.save(this,current.toString(),video.getCurrentPosition(),video.getDuration());}
    private void applyRememberedSubtitle(){
        video.setSubtitleSize(getSharedPreferences("subtitle-settings",0).getInt("size",20));if(!ready||current==null||activeSubtitle.isEmpty())return;
        final java.io.File source=new java.io.File(SubtitleStore.root(this),activeSubtitle);final long epoch=mediaEpoch;final String name=activeSubtitle;
        String stamp=name+":"+source.lastModified()+":"+getSharedPreferences("subtitle-settings",0).getLong(name,0);if(stamp.equals(subtitleStamp))return;subtitleStamp=stamp;
        vaultIo.execute(()->{try{if(!source.getCanonicalFile().getParentFile().equals(SubtitleStore.root(this).getCanonicalFile())||!source.isFile())return;java.io.File adjusted=SubtitleStore.adjusted(this,source);runOnUiThread(()->whenUnlocked(()->{if(epoch==mediaEpoch&&name.equals(activeSubtitle)&&ready)video.addSubtitle(Uri.fromFile(adjusted),"text/vtt");}));}catch(Exception e){runOnUiThread(()->{if(epoch==mediaEpoch)toast("字幕读取失败，请在字幕中心重新选择");});}});
    }
    private Intent resultData(){return new Intent().putExtra("url",current==null?"":current.toString()).putExtra("position",video==null?0:video.getCurrentPosition());}
    @Override public void finish(){rememberPosition();if(current!=null)setResult(RESULT_OK,resultData());super.finish();}
    private String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private boolean restoreVault(){String name=getIntent().getStringExtra("vaultFile");if(name==null)return false;java.io.File opened=current==null?null:new java.io.File(VaultStore.cache(this),current.getLastPathSegment());if(opened!=null&&opened.isFile())return false;if(restoringVault)return true;restoringVault=true;vaultIo.execute(()->{try{java.io.File encrypted=new java.io.File(VaultStore.root(this),name);if(!encrypted.getCanonicalFile().getParentFile().equals(VaultStore.root(this).getCanonicalFile()))throw new java.io.IOException();java.io.File f=VaultStore.open(this,encrypted);runOnUiThread(()->whenUnlocked(()->{restoringVault=false;load(new Uri.Builder().scheme("content").authority(MediaContentProvider.AUTHORITY).appendPath("vault-open").appendPath(f.getName()).build(),true);}));}catch(Exception error){runOnUiThread(()->{restoringVault=false;toast("保险箱视频无法解密，请重新选择");finish();});}});return true;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();foreground=privateReady();if(foreground&&ready&&resumeOnForeground){video.start();resumeOnForeground=false;}if(fullscreen)applySystemBars();}
    @Override protected void onPause(){rememberPosition();if(current!=null&&getIntent().hasExtra("vaultFile"))getIntent().putExtra("position",video.getCurrentPosition());foreground=false;resumeOnForeground=ready&&video.isPlaying();video.pause();handler.removeCallbacks(hideControls);super.onPause();}
    @Override protected void onPrivateUnlocked(){foreground=true;if(video!=null)applyRememberedSubtitle();if(restoreVault())return;if(ready&&resumeOnForeground){video.start();resumeOnForeground=false;}}
    @Override protected void onPrivateLocked(){foreground=false;if(video!=null)video.pause();}
    @Override protected void onDestroy(){if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);handler.removeCallbacksAndMessages(null);video.stopPlayback();vaultIo.shutdownNow();if(getIntent().hasExtra("vaultFile"))VaultStore.purge(this);super.onDestroy();}
}
