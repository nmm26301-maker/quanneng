package app.quanneng;

import android.app.*;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

/** 使用 Android 系统解码的视频播放后备方案，支持精确跳转与 AB 循环。 */
public final class SystemPlayerActivity extends Activity {
    private VideoView video;
    private TextView position;
    private Button loopButton;
    private MediaPlayer media;
    private long pointA=0,pointB=0;
    private boolean looping=false,ready=false;
    private int speedIndex=1;
    private final float[] speeds={.75f,1f,1.25f,1.5f,2f};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(ready){int now=video.getCurrentPosition();if(looping&&now>=pointB)video.seekTo((int)pointA);position.setText(time(now)+" / "+time(video.getDuration())+"     A "+time(pointA)+" → B "+time(pointB));}handler.postDelayed(this,80);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);setContentView(root);
        Button close=new Button(this);close.setText("返回全能");close.setOnClickListener(v->finish());root.addView(close);
        video=new VideoView(this);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);
        position=new TextView(this);position.setTextColor(Color.WHITE);position.setTextSize(11);position.setPadding(12,12,12,12);root.addView(position);
        LinearLayout skip=new LinearLayout(this);root.addView(skip);
        for(int seconds:new int[]{-600,-60,-10,10,60,600}){Button b=new Button(this);b.setText((seconds>0?"+":"−")+(Math.abs(seconds)>=60?Math.abs(seconds)/60+"分":Math.abs(seconds)+"秒"));b.setTextSize(10);skip.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));b.setOnClickListener(v->{if(!ready)return;looping=false;loopButton.setText("开启循环");video.seekTo(Math.max(0,Math.min(video.getDuration(),video.getCurrentPosition()+seconds*1000)));});}
        LinearLayout ab=new LinearLayout(this);root.addView(ab);
        Button a=new Button(this);a.setText("设置 A");ab.addView(a,new LinearLayout.LayoutParams(0,dp(58),1));a.setOnClickListener(v->{if(ready){pointA=video.getCurrentPosition();looping=false;loopButton.setText("开启循环");}});
        Button b=new Button(this);b.setText("设置 B");ab.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));b.setOnClickListener(v->{if(ready){pointB=video.getCurrentPosition();looping=false;loopButton.setText("开启循环");}});
        loopButton=new Button(this);loopButton.setText("开启循环");ab.addView(loopButton,new LinearLayout.LayoutParams(0,dp(58),1));loopButton.setOnClickListener(v->{if(!ready||pointB<=pointA){toast("请先设置有效的 A、B 点");return;}looping=!looping;loopButton.setText(looping?"循环中":"开启循环");if(looping){video.seekTo((int)pointA);video.start();}});
        Button speed=new Button(this);speed.setText("1.0 倍速");root.addView(speed);speed.setOnClickListener(v->{if(media==null||!ready)return;speedIndex=(speedIndex+1)%speeds.length;try{media.setPlaybackParams(media.getPlaybackParams().setSpeed(speeds[speedIndex]));speed.setText(speeds[speedIndex]+" 倍速");}catch(RuntimeException e){toast("当前格式不支持倍速");}});
        video.setOnPreparedListener(mp->{media=mp;ready=true;video.start();handler.post(tick);});
        video.setOnCompletionListener(mp->{if(looping){video.seekTo((int)pointA);video.start();}});
        video.setOnErrorListener((mp,what,extra)->{ready=false;new AlertDialog.Builder(this).setMessage("系统解码失败。来源可能限制播放，或设备不支持此格式。").setPositiveButton("返回",(d,w)->finish()).setCancelable(false).show();return true;});
        Uri uri=getIntent().getData();if(uri==null){finish();return;}try{video.setVideoURI(uri);}catch(RuntimeException e){toast("无法打开视频");finish();}
    }
    private String time(long ms){long s=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onPause(){video.pause();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);video.stopPlayback();super.onDestroy();}
}
