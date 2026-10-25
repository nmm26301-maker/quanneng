package app.quanneng;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.*;
import org.json.*;
import java.util.*;

/** 默认原生播放页。网络媒体通过 Android 媒体栈加载，不受网页跨域策略限制。 */
public final class SystemPlayerActivity extends Activity {
    private VideoView video;
    private TextView title,position;
    private Button loopButton,playButton,speedButton;
    private MediaPlayer media;
    private Uri current;
    private ArrayList<String> queue;
    private long pointA=0,pointB=0;
    private boolean looping=false,ready=false,failed=false,foreground=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){
        if(ready){int now=video.getCurrentPosition();if(looping&&now>=pointB){seekExact((int)pointA);now=(int)pointA;}
            position.setText(time(now)+" / "+time(video.getDuration())+"    A "+time(pointA)+" → B "+time(pointB));playButton.setText(video.isPlaying()?"暂停":"播放");}
        handler.postDelayed(this,80);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(8,12,16));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        Button close=button("返回",top);close.setOnClickListener(v->finish());
        title=new TextView(this);title.setTextColor(Color.WHITE);title.setTextSize(13);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,dp(52),3));
        video=new VideoView(this);video.setId(1000);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);
        position=new TextView(this);position.setText("正在加载视频…");position.setTextColor(Color.rgb(195,221,190));position.setTextSize(11);position.setPadding(dp(12),dp(9),dp(12),dp(9));root.addView(position);
        LinearLayout skip=new LinearLayout(this);root.addView(skip);
        for(int seconds:new int[]{-600,-60,-10,10,60,600}){Button b=button((seconds>0?"+":"−")+(Math.abs(seconds)>=60?Math.abs(seconds)/60+"分":Math.abs(seconds)+"秒"),skip);b.setTextSize(10);b.setOnClickListener(v->{if(!ready)return;clearLoop();seekExact(Math.max(0,Math.min(video.getDuration(),video.getCurrentPosition()+seconds*1000)));});}
        LinearLayout ab=new LinearLayout(this);root.addView(ab);
        Button a=button("设置 A",ab);a.setId(1001);a.setOnClickListener(v->{if(ready){pointA=video.getCurrentPosition();clearLoop();}});
        Button b=button("设置 B",ab);b.setId(1002);b.setOnClickListener(v->{if(ready){pointB=video.getCurrentPosition();clearLoop();}});
        loopButton=button("开启循环",ab);loopButton.setId(1003);loopButton.setOnClickListener(v->{if(!ready||pointB<=pointA){toast("先在不同时间设置 A、B 点");return;}looping=!looping;loopButton.setText(looping?"循环中":"开启循环");if(looping){seekExact((int)pointA);video.start();}});
        LinearLayout tools=new LinearLayout(this);root.addView(tools);
        playButton=button("播放",tools);playButton.setId(1004);playButton.setOnClickListener(v->{if(!ready)return;if(video.isPlaying())video.pause();else video.start();});
        speedButton=button("1.0 倍速",tools);speedButton.setOnClickListener(v->chooseSpeed());
        Button subtitle=button("导入字幕",tools);subtitle.setOnClickListener(v->{if(!ready){toast("请等待视频加载");return;}try{startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),44);}catch(ActivityNotFoundException e){toast("系统未找到文件选择器");}});
        video.setOnPreparedListener(mp->{media=mp;ready=true;failed=false;handler.removeCallbacks(tick);handler.post(tick);
            int resume=getIntent().getIntExtra("position",0);if(resume>0&&resume<video.getDuration()-2000)seekExact(resume);
            if(foreground)video.start();position.setText("视频已就绪");
        });
        video.setOnCompletionListener(mp->{if(looping){seekExact((int)pointA);video.start();return;}next();});
        video.setOnErrorListener((mp,what,extra)->{showFailure(extra);return true;});
        queue=getIntent().getStringArrayListExtra("queue");current=getIntent().getData();if(current==null){finish();return;}load(current,true);
    }
    private Button button(String text,LinearLayout row){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setPadding(dp(3),0,dp(3),0);row.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));return b;}
    private void seekExact(int value){try{if(media!=null)media.seekTo((long)value,MediaPlayer.SEEK_CLOSEST);else video.seekTo(value);}catch(IllegalStateException error){toast("视频尚未准备好，请稍后重试");}}
    private void clearLoop(){looping=false;loopButton.setText("开启循环");}
    private void load(Uri uri,boolean initial){
        current=uri;ready=false;failed=false;media=null;pointA=pointB=0;clearLoop();title.setText(displayName(uri));position.setText("正在加载网络或本地视频…");
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
        if(failed||isFinishing())return;failed=true;ready=false;video.stopPlayback();
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
        playButton.setText("播放");
    }
    private void chooseSpeed(){if(!ready||media==null)return;String[] labels={"0.5 倍速","0.75 倍速","1.0 倍速","1.25 倍速","1.5 倍速","2.0 倍速","3.0 倍速"};float[] values={.5f,.75f,1f,1.25f,1.5f,2f,3f};new AlertDialog.Builder(this).setTitle("播放速度").setItems(labels,(d,i)->{try{media.setPlaybackParams(media.getPlaybackParams().setSpeed(values[i]));speedButton.setText(labels[i]);}catch(RuntimeException e){toast("此格式不支持所选速度");}}).show();}
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
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;video.pause();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);video.stopPlayback();super.onDestroy();}
}
