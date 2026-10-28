package app.quanneng;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.source.*;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleExtractor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Media3 官方缓存、连接复用、缓冲和错误恢复的统一入口；本地/私密源不落入网络缓存。 */
final class PlaybackPipeline {
    static final String[] PROFILES={"均衡 · 提前缓冲最多 60 秒","流畅优先 · 提前缓冲最多 120 秒","省流量 · 优先 720p 以下"};
    static final long CACHE_LIMIT=512L*1024*1024;
    private static final okhttp3.OkHttpClient HTTP=new okhttp3.OkHttpClient.Builder()
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true).followSslRedirects(false).build();
    private static SimpleCache cache;
    private final DataSource.Factory upstream,media;
    final boolean cacheEnabled;
    final int activeProfile;
    PlaybackPipeline(Context context,Uri uri,Map<String,String> headers)throws IOException{
        activeProfile=profile(context);
        DataSource.Factory requests=new OkHttpDataSource.Factory(HTTP).setDefaultRequestProperties(headers);
        String auth=null;for(Map.Entry<String,String> h:headers.entrySet())if(h.getKey().equalsIgnoreCase("Authorization"))auth=h.getValue();
        if(auth!=null){final String credential=auth;final java.net.URL origin=NetworkPolicy.url(uri.toString());requests=()->new NasDataSource(origin,credential);}
        upstream=new DefaultDataSource.Factory(context,requests);
        media=cacheFactory(context,uri,headers,upstream);cacheEnabled=media!=upstream;
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("streaming",Context.MODE_PRIVATE);}
    static int profile(Context c){return Math.max(0,Math.min(2,prefs(c).getInt("profile",1)));}
    static void profile(Context c,int value){prefs(c).edit().putInt("profile",Math.max(0,Math.min(2,value))).apply();}
    static boolean cacheSelected(Context c){return prefs(c).getBoolean("cache",true);}
    static void cacheSelected(Context c,boolean value){prefs(c).edit().putBoolean("cache",value).apply();}
    static boolean cacheEligible(Uri uri,Map<String,String> headers){
        if(!"https".equalsIgnoreCase(uri.getScheme()))return false;
        for(String name:headers.keySet())if(name.equalsIgnoreCase("Authorization")||name.equalsIgnoreCase("Cookie")||name.equalsIgnoreCase("Proxy-Authorization"))return false;
        return true;
    }
    private static synchronized SimpleCache cache(Context c)throws IOException{
        if(cache==null){
            File directory=new File(c.getCacheDir(),"streaming-v1");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建视频缓存");
            SimpleCache created=new SimpleCache(directory,new LeastRecentlyUsedCacheEvictor(CACHE_LIMIT),new StandaloneDatabaseProvider(c.getApplicationContext()));
            try{created.checkInitialization();cache=created;}catch(IOException error){created.release();throw error;}
        }
        return cache;
    }
    /** 工厂参数便于不同网络实现共用同一缓存，拒绝将 URL 或请求上下文明文用作索引键。 */
    static DataSource.Factory cacheFactory(Context c,Uri uri,Map<String,String> headers,DataSource.Factory source){
        if(!cacheSelected(c)||!cacheEligible(uri,headers))return source;
        try{
            final String identity=new TreeMap<>(headers).toString();
            return new CacheDataSource.Factory().setCache(cache(c)).setUpstreamDataSourceFactory(source)
                .setCacheKeyFactory(spec->cacheKey(spec,identity)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        }catch(IOException|RuntimeException unavailable){android.util.Log.w("Quanneng","视频缓存不可用，回退网络播放");return source;}
    }
    static String cacheKey(DataSpec spec,String identity){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((spec.uri.toString()+"\n"+identity).getBytes(StandardCharsets.UTF_8));
            StringBuilder key=new StringBuilder("v1-");for(byte b:digest)key.append(String.format(Locale.US,"%02x",b&255));return key.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static synchronized long cacheBytes(){return cache==null?0:cache.getCacheSpace();}
    static synchronized void clearCache(Context c)throws IOException{SimpleCache store=cache(c);for(String key:new HashSet<>(store.getKeys()))store.removeResource(key);}
    static DefaultLoadControl loadControl(Context c,boolean network){
        if(!network)return new DefaultLoadControl();
        int p=profile(c),min=p==1?30000:15000,max=p==1?120000:60000;
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        int memory=manager!=null&&manager.isLowRamDevice()?32:64;
        return new DefaultLoadControl.Builder().setBufferDurationsMs(min,max,2500,8000)
            .setTargetBufferBytes(memory*1024*1024).setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(10000,true).build();
    }
    static DefaultTrackSelector trackSelector(Context c){
        DefaultTrackSelector selector=new DefaultTrackSelector(c);
        if(profile(c)==2)selector.setParameters(selector.buildUponParameters().setMaxVideoSize(1280,720).setMaxVideoBitrate(1500000));
        return selector;
    }
    static final class RetryPolicy extends DefaultLoadErrorHandlingPolicy {
        RetryPolicy(){super(6);}
        @Override public long getRetryDelayMsFor(LoadErrorHandlingPolicy.LoadErrorInfo info){
            Throwable error=info.exception;
            while(error!=null){
                if(error instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)error).responseCode;if(code>=400&&code<500&&code!=408&&code!=429)return C.TIME_UNSET;}
                error=error.getCause();
            }
            long delay=super.getRetryDelayMsFor(info);return delay==C.TIME_UNSET?delay:Math.max(500,delay);
        }
    }
    /** 清单、直播刷新、AES 密钥和外置字幕始终走上游，仅媒体分片与初始化段进入缓存。 */
    MediaSource create(MediaItem item){
        MediaItem bare=item.buildUpon().setSubtitleConfigurations(Collections.emptyList()).build();
        MediaSource main;RetryPolicy retry=new RetryPolicy();
        switch(Util.inferContentTypeForUriAndMimeType(bare.localConfiguration.uri,bare.localConfiguration.mimeType)){
            case C.CONTENT_TYPE_HLS:
                main=new HlsMediaSource.Factory(type->type==C.DATA_TYPE_MEDIA||type==C.DATA_TYPE_MEDIA_INITIALIZATION?media.createDataSource():upstream.createDataSource()).setLoadErrorHandlingPolicy(retry).createMediaSource(bare);break;
            case C.CONTENT_TYPE_DASH:
                main=new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(media),upstream).setLoadErrorHandlingPolicy(retry).createMediaSource(bare);break;
            case C.CONTENT_TYPE_SS:
                main=new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(media),upstream).setLoadErrorHandlingPolicy(retry).createMediaSource(bare);break;
            default:main=new ProgressiveMediaSource.Factory(media).setLoadErrorHandlingPolicy(retry).createMediaSource(bare);
        }
        if(item.localConfiguration.subtitleConfigurations.isEmpty())return main;
        ArrayList<MediaSource> sources=new ArrayList<>();sources.add(main);
        for(MediaItem.SubtitleConfiguration sub:item.localConfiguration.subtitleConfigurations){
            Format format=new Format.Builder().setSampleMimeType(sub.mimeType).setLanguage(sub.language).setSelectionFlags(sub.selectionFlags).setRoleFlags(sub.roleFlags).setLabel(sub.label).setId(sub.id).build();
            DefaultSubtitleParserFactory parsers=new DefaultSubtitleParserFactory();
            sources.add(new ProgressiveMediaSource.Factory(upstream,()->new Extractor[]{new SubtitleExtractor(parsers.create(format),format)})
                .setLoadErrorHandlingPolicy(retry).createMediaSource(MediaItem.fromUri(sub.uri)));
        }
        return new MergingMediaSource(sources.toArray(new MediaSource[0]));
    }
}
