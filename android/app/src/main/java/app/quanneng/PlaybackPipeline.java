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
    static final long CACHE_LIMIT=Long.MAX_VALUE;
    private static final okhttp3.OkHttpClient HTTP=new okhttp3.OkHttpClient.Builder()
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true).followSslRedirects(false).build();
    private static SimpleCache cache;
    private static final java.util.concurrent.ExecutorService PREFETCH=java.util.concurrent.Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"quanneng-prefetch");t.setDaemon(true);return t;});
    private final DataSource.Factory upstream,media;
    private final String cacheIdentity;
    private final Set<String> sessionKeys=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private java.util.concurrent.Future<?> prefetchTask;
    final boolean cacheEnabled;
    final int activeProfile;
    PlaybackPipeline(Context context,Uri uri,Map<String,String> headers)throws IOException{
        activeProfile=profile(context);
        DataSource.Factory requests=new OkHttpDataSource.Factory(HTTP).setDefaultRequestProperties(headers);
        String auth=null;for(Map.Entry<String,String> h:headers.entrySet())if(h.getKey().equalsIgnoreCase("Authorization"))auth=h.getValue();
        if(auth!=null){final String credential=auth;final java.net.URL origin=NetworkPolicy.url(uri.toString());requests=()->new NasDataSource(origin,credential);}
        upstream=new DefaultDataSource.Factory(context,requests);
        cacheIdentity=new TreeMap<>(headers).toString();media=cacheFactory(context,uri,headers,upstream,sessionKeys);cacheEnabled=media!=upstream;
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
        return cacheFactory(c,uri,headers,source,null);
    }
    private static DataSource.Factory cacheFactory(Context c,Uri uri,Map<String,String> headers,DataSource.Factory source,Set<String> keys){
        if(!cacheSelected(c)||!cacheEligible(uri,headers))return source;
        try{
            final String identity=new TreeMap<>(headers).toString();
            return new CacheDataSource.Factory().setCache(cache(c)).setUpstreamDataSourceFactory(source)
                .setCacheKeyFactory(spec->{String key=cacheKey(spec,identity);if(keys!=null)keys.add(key);return key;}).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
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
    /** For ordinary public progressive media, read the remainder through the same encrypted-key cache. */
    synchronized void prefetch(Uri uri,String mime){
        if(!cacheEnabled||prefetchTask!=null||!"https".equalsIgnoreCase(uri.getScheme())||
           !("video/mp4".equals(mime)||"video/webm".equals(mime)||"video/x-matroska".equals(mime)))return;
        prefetchTask=PREFETCH.submit(()->{DataSource source=media.createDataSource();try{
            source.open(new DataSpec.Builder().setUri(uri).build());byte[] bytes=new byte[128*1024];
            while(!Thread.currentThread().isInterrupted()&&source.read(bytes,0,bytes.length)!=-1){}
        }catch(IOException|RuntimeException ignored){}finally{try{source.close();}catch(IOException ignored){}}});
    }
    synchronized void stopPrefetch(){if(prefetchTask!=null){prefetchTask.cancel(true);prefetchTask=null;}}
    void completed(Uri uri){
        if(!cacheEnabled)return;
        java.util.concurrent.Future<?> pending; synchronized(this){pending=prefetchTask;stopPrefetch();}
        PREFETCH.execute(()->{if(pending!=null)try{pending.get(20,TimeUnit.SECONDS);}catch(Exception ignored){}
            sessionKeys.add(cacheKey(new DataSpec.Builder().setUri(uri).build(),cacheIdentity));
            for(String key:new HashSet<>(sessionKeys))for(int attempt=0;attempt<5;attempt++)try{cache.removeResource(key);break;}catch(RuntimeException busy){
                try{Thread.sleep(500);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
            }});
    }
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
    /** Called off the UI thread. Probe only the first 8 KiB through the same authenticated upstream. */
    MediaItem resolve(MediaItem item)throws IOException{
        Uri uri=item.localConfiguration.uri;
        if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))return item;
        DataSource source=upstream.createDataSource();
        try{
            // Use an ordinary GET: some signed manifest endpoints reject Range probes.
            source.open(new DataSpec.Builder().setUri(uri).build());
            byte[] bytes=new byte[8192];int count=0,read;
            while(count<bytes.length&&(read=source.read(bytes,count,bytes.length-count))!=-1){if(read==0)break;count+=read;}
            String type="";
            for(Map.Entry<String,List<String>> entry:source.getResponseHeaders().entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Content-Type")&&!entry.getValue().isEmpty())type=entry.getValue().get(0);
            String mime=MediaProbe.mime(bytes,count,type,source.getUri()==null?uri.toString():source.getUri().toString(),item.localConfiguration.mimeType);
            if("text/html".equals(mime))throw new IOException("SOURCE_IS_WEBPAGE");
            return item.buildUpon().setMimeType(mime).build();
        }finally{source.close();}
    }
    /** 清单、直播刷新、AES 密钥和外置字幕始终走上游，仅媒体分片与初始化段进入缓存。 */
    MediaSource create(MediaItem item){
        MediaItem bare=item.buildUpon().setSubtitleConfigurations(Collections.emptyList()).build();
        MediaSource main;RetryPolicy retry=new RetryPolicy();
        // Media3's adaptive MIME switch accepts its canonical x-mpegURL constant.
        // Web/CDN responses commonly use vnd.apple.mpegurl or lowercase x-mpegurl.
        String observed=MediaCandidatePolicy.mime(bare.localConfiguration.uri.toString(),bare.localConfiguration.mimeType);
        int contentType="application/vnd.apple.mpegurl".equals(observed)?C.CONTENT_TYPE_HLS:Util.inferContentTypeForUriAndMimeType(bare.localConfiguration.uri,bare.localConfiguration.mimeType);
        switch(contentType){
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
