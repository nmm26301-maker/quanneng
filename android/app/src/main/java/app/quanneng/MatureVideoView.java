package app.quanneng;

import android.content.Context;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import androidx.media3.common.*;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import tv.danmaku.ijk.media.exo2.IjkExo2MediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import java.util.*;

/** 现有全能控制界面的薄适配层；实际解复用、缓冲与解码由 GSY + Media3 负责。 */
public final class MatureVideoView extends FrameLayout {
    private static final class Engine extends IjkExo2MediaPlayer {
        Engine(Context context){super(context);mRendererFactory=new DefaultRenderersFactory(context).setEnableDecoderFallback(true);}
        ExoPlayer player(){return mInternalPlayer;}
    }
    private final SurfaceView surface;
    private final TextView subtitle;
    private Engine engine;
    private PlaybackPipeline pipeline;
    private Uri uri;
    private String sourceMime;
    private Map<String,String> headers=Collections.emptyMap();
    private IMediaPlayer.OnPreparedListener prepared;
    private IMediaPlayer.OnCompletionListener completion;
    private IMediaPlayer.OnErrorListener error;
    public MatureVideoView(Context context){
        super(context);
        surface=new SurfaceView(context);addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        subtitle=new TextView(context);subtitle.setTextColor(Color.WHITE);subtitle.setTextSize(18);subtitle.setGravity(Gravity.CENTER);subtitle.setShadowLayer(3,1,1,Color.BLACK);subtitle.setPadding(16,8,16,16);addView(subtitle,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){if(engine!=null)engine.setDisplay(holder);}
            public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){if(engine!=null)engine.setDisplay(holder);}
            public void surfaceDestroyed(SurfaceHolder holder){if(engine!=null)engine.setDisplay(null);}
        });
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)fitVideo();});
    }
    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener listener){prepared=listener;}
    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener listener){completion=listener;}
    public void setOnErrorListener(IMediaPlayer.OnErrorListener listener){error=listener;}
    public void setSubtitleSize(int sp){subtitle.setTextSize(Math.max(16,Math.min(32,sp)));}
    public void setSourceMime(String mime){sourceMime=mime;}
    public void setVideoURI(Uri value,Map<String,String> requestHeaders){
        stopPlayback();uri=value;headers=new HashMap<>(requestHeaders);subtitle.setText("");
        final Engine player=new Engine(getContext());engine=player;
        player.setCache(false);player.setPreview(true);
        player.setLoadControl(PlaybackPipeline.loadControl(getContext(),"https".equals(value.getScheme())||"http".equals(value.getScheme())));
        player.setTrackSelector(PlaybackPipeline.trackSelector(getContext()));
        if("application/vnd.apple.mpegurl".equals(sourceMime)||"application/x-mpegurl".equals(sourceMime))player.setOverrideExtension("m3u8");
        else if("application/dash+xml".equals(sourceMime))player.setOverrideExtension("mpd");
        player.setOnPreparedListener(mp->{
            if(engine!=player)return;player.setSeekParameter(SeekParameters.EXACT);fitVideo();
            if(player.player()!=null)player.player().addListener(new Player.Listener(){@Override public void onCues(CueGroup group){StringBuilder text=new StringBuilder();for(Cue cue:group.cues)if(cue.text!=null){if(text.length()>0)text.append('\n');text.append(cue.text);}subtitle.setText(text);}});
            if(prepared!=null)prepared.onPrepared(mp);
        });
        player.setOnVideoSizeChangedListener((mp,w,h,sarN,sarD)->{if(engine==player)fitVideo();});
        player.setOnCompletionListener(mp->{if(engine==player&&completion!=null)completion.onCompletion(mp);});
        player.setOnErrorListener((mp,what,extra)->engine!=player||error!=null&&error.onError(mp,what,extra));
        if(surface.getHolder().getSurface().isValid())player.setDisplay(surface.getHolder());
        player.setDataSource(getContext(),value,headers);
        try{pipeline=new PlaybackPipeline(getContext(),value,headers);MediaItem.Builder item=new MediaItem.Builder().setUri(value);if(sourceMime!=null&&!sourceMime.isEmpty())item.setMimeType(sourceMime);player.setMediaSource(pipeline.create(item.build()));}catch(java.io.IOException e){if(error!=null)error.onError(player,-1004,0);return;}
        player.prepareAsync();
    }
    private void fitVideo(){
        if(engine==null||engine.getVideoWidth()<=0||engine.getVideoHeight()<=0||getWidth()<=0||getHeight()<=0)return;
        float ratio=(float)engine.getVideoWidth()/engine.getVideoHeight();int width=getWidth(),height=Math.round(width/ratio);if(height>getHeight()){height=getHeight();width=Math.round(height*ratio);}
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)surface.getLayoutParams();if(params.width!=width||params.height!=height){params.width=width;params.height=height;surface.setLayoutParams(params);}
    }
    public void start(){if(engine!=null)engine.start();}
    public void pause(){if(engine!=null)engine.pause();}
    public boolean isPlaying(){return engine!=null&&engine.isPlaying();}
    public int getDuration(){return engine==null?0:(int)Math.min(Integer.MAX_VALUE,Math.max(0,engine.getDuration()));}
    public int getCurrentPosition(){return engine==null?0:(int)Math.min(Integer.MAX_VALUE,Math.max(0,engine.getCurrentPosition()));}
    public int getBufferPercentage(){return engine==null?0:engine.getBufferedPercentage();}
    public long getBufferedAhead(){return engine==null||engine.player()==null?0:Math.max(0,engine.player().getBufferedPosition()-engine.player().getCurrentPosition());}
    public boolean isBuffering(){return engine!=null&&engine.player()!=null&&engine.player().getPlaybackState()==Player.STATE_BUFFERING;}
    public String streamingStatus(){boolean local=uri==null||!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme());return local?"本地视频 · 不消耗网络流量":"已提前缓冲 "+getBufferedAhead()/1000+" 秒 · "+(pipeline!=null&&pipeline.activeProfile==2?"省流量清晰度":"自动清晰度")+"\n"+(pipeline!=null&&pipeline.cacheEnabled?"边播边缓存 · 上限 512 MB":"当前来源不写入磁盘缓存");}
    public void seekTo(int position){if(engine!=null)engine.seekTo(position);}
    public void stopPlayback(){Engine previous=engine;engine=null;if(previous!=null){previous.release();post(previous::release);}}
    public void addSubtitle(Uri source,String mime){
        if(engine==null||engine.player()==null)return;
        ExoPlayer player=engine.player();long position=player.getCurrentPosition();boolean play=player.getPlayWhenReady();
        MediaItem.SubtitleConfiguration sub=new MediaItem.SubtitleConfiguration.Builder(source).setMimeType(mime).setLanguage("zh").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
        MediaItem.Builder item=new MediaItem.Builder().setUri(uri).setSubtitleConfigurations(Collections.singletonList(sub));if(sourceMime!=null&&!sourceMime.isEmpty())item.setMimeType(sourceMime);
        if(pipeline==null)return;
        player.setMediaSource(pipeline.create(item.build()),position);player.prepare();player.setPlayWhenReady(play);
    }
}
