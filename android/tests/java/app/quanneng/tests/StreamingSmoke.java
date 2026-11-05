package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** 真正使用 Media3 CacheDataSource 和解码器，验证断网重读、限速和中断恢复。 */
public final class StreamingSmoke extends Instrumentation {
    private Class<?> sourceType,factoryType,specType;
    private byte[] fixture;
    private final Context context(){return getTargetContext();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        PrivateTestAccess.install(this);Bundle result=new Bundle();Activity player=null;
        try{
            sourceType=Class.forName("androidx.media3.datasource.DataSource");factoryType=Class.forName("androidx.media3.datasource.DataSource$Factory");specType=Class.forName("androidx.media3.datasource.DataSpec");
            try(InputStream input=getContext().getAssets().open("buffer-fixture.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)out.write(b,0,n);fixture=out.toByteArray();}
            policy("cacheSelected",new Class<?>[]{Context.class,boolean.class},context(),true);
            cacheChecks();
            player=open();Activity active=player;verifySubtitle(active);
            AtomicReference<Throwable> uiError=new AtomicReference<>();runOnMainSync(()->{try{active.findViewById(1801).performClick();}catch(Throwable e){uiError.set(e);}});check(uiError.get()==null,"Optimization settings failed");sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            NativeNetwork network=new NativeNetwork();network.throttled=true;network.interrupt=true;
            Uri uri=Uri.parse("https://stream-fixture.invalid/weak-"+System.nanoTime()+".mp4");Object cached=cached(uri,Collections.emptyMap(),network.factory());
            play(active,cached,uri);awaitPlaying(active,5000,45000);check(network.opens.get()>=2&&network.interrupted.get(),"Interrupted request was not retried");
            report("Throttled source interrupted once, resumed with Range and decoded more than five seconds");
            long limit=SystemClock.elapsedRealtime()+45000;while(network.bytes.get()<fixture.length&&SystemClock.elapsedRealtime()<limit)SystemClock.sleep(100);
            check(network.bytes.get()>=fixture.length,"Fixture never fully buffered");SystemClock.sleep(500);
            pause(active);network.offline=true;int before=network.opens.get();
            play(active,cached,uri);awaitPlaying(active,2000,15000);check(network.opens.get()==before,"Offline replay contacted upstream");
            report("Actual Media3 video decoded and advanced with upstream disabled using persisted disk spans");
            runOnMainSync(()->{try{Object v=active.findViewById(1000);call(v,"seekTo",new Class<?>[]{int.class},15000);}catch(Exception e){throw new RuntimeException(e);}});awaitPlaying(active,15000,12000);check(network.opens.get()==before,"Cached seek contacted upstream");
            report("Cached seek stayed offline");runOnMainSync(active::finish);player=null;
            policy("clearCache",new Class<?>[]{Context.class},context());check(((Long)policy("cacheBytes",new Class<?>[]{}))==0,"Clear cache retained spans");
            result.putString("stream","QUANNENG_STREAMING_OK\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){e.printStackTrace();result.putString("stream","QUANNENG_STREAMING_FAILED: "+cause(e)+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(player!=null){Activity p=player;runOnMainSync(p::finish);}try{policy("profile",new Class<?>[]{Context.class,int.class},context(),1);policy("cacheSelected",new Class<?>[]{Context.class,boolean.class},context(),true);}catch(Exception ignored){}}
    }
    private static Throwable cause(Throwable e){return e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;}
    private void verifySubtitle(Activity active)throws Exception{
        File text=new File(context().getCacheDir(),"streaming-test-subtitle.vtt");
        try(FileOutputStream out=new FileOutputStream(text)){out.write("WEBVTT\n\n00:00:00.000 --> 00:00:15.000\nStreamingSubtitleVerified\n".getBytes("UTF-8"));}
        runOnMainSync(()->{try{Object view=active.findViewById(1000);call(view,"pause",new Class<?>[]{});call(view,"seekTo",new Class<?>[]{int.class},0);call(view,"addSubtitle",new Class<?>[]{Uri.class,String.class},Uri.fromFile(text),"text/vtt");call(view,"start",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});
        long limit=SystemClock.elapsedRealtime()+15000;AtomicBoolean visible=new AtomicBoolean();while(SystemClock.elapsedRealtime()<limit){runOnMainSync(()->{try{android.widget.TextView cue=(android.widget.TextView)PrivateTestAccess.field(active.findViewById(1000),"subtitle");visible.set(cue.getText().toString().contains("StreamingSubtitleVerified"));}catch(Exception ignored){}});if(visible.get()){text.delete();report("External VTT parsed into current Media3 cues and displayed by actual subtitle view");return;}SystemClock.sleep(100);}throw new AssertionError("External subtitle did not render");
    }
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private void report(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        Class<?> type=target instanceof Class?(Class<?>)target:target.getClass();Method m=null;
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{m=c.getDeclaredMethod(name,types);break;}catch(NoSuchMethodException ignored){}
        if(m==null)m=type.getMethod(name,types);m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);
    }
    private Object policy(String name,Class<?>[] types,Object... args)throws Exception{return call(Class.forName("app.quanneng.PlaybackPipeline"),name,types,args);}
    private Object cached(Uri uri,Map<String,String> headers,Object factory)throws Exception{return policy("cacheFactory",new Class<?>[]{Context.class,Uri.class,Map.class,factoryType},context(),uri,headers,factory);}
    private byte[] read(Object factory,Uri uri,long offset,int count)throws Exception{
        Object ds=call(factory,"createDataSource",new Class<?>[]{}),spec=specType.getConstructor(Uri.class,long.class,long.class).newInstance(uri,offset,(long)count);
        ByteArrayOutputStream out=new ByteArrayOutputStream();try{call(ds,"open",new Class<?>[]{specType},spec);byte[] b=new byte[4096];int n;while((n=(Integer)call(ds,"read",new Class<?>[]{byte[].class,int.class,int.class},b,0,b.length))!=-1)out.write(b,0,n);}finally{call(ds,"close",new Class<?>[]{});}return out.toByteArray();
    }
    private void cacheChecks()throws Exception{
        policy("clearCache",new Class<?>[]{Context.class},context());NativeNetwork network=new NativeNetwork();Object upstream=network.factory();Uri uri=Uri.parse("https://stream-fixture.invalid/range-"+System.nanoTime()+".mp4");Object cached=cached(uri,Collections.emptyMap(),upstream);
        check(Arrays.equals(read(cached,uri,0,fixture.length),fixture),"Initial cache content differs");int initial=network.opens.get();network.offline=true;
        Object reopened=cached(uri,Collections.emptyMap(),upstream);check(Arrays.equals(read(reopened,uri,100,4000),Arrays.copyOfRange(fixture,100,4100)),"Offline Range differs");check(network.opens.get()==initial,"Offline cache read reached network");
        check(cached(uri,Collections.singletonMap("Cookie","private=yes"),upstream)==upstream,"Cookie source persisted");check(cached(uri,Collections.singletonMap("authorization","Basic private"),upstream)==upstream,"Authorization source persisted");check(cached(Uri.parse("content://app.quanneng.next.media/vault-open/a"),Collections.emptyMap(),upstream)==upstream,"Vault source persisted");
        check(cached(Uri.parse("http://192.168.1.2/a.mp4"),Collections.emptyMap(),upstream)==upstream,"LAN source persisted");
        Object identity=cached(uri,Collections.singletonMap("Referer","https://other.invalid/"),upstream);boolean blocked=false;try{read(identity,uri,0,100);}catch(InvocationTargetException expected){blocked=true;}check(blocked,"Different source context reused cached bytes");
        policy("cacheSelected",new Class<?>[]{Context.class,boolean.class},context(),false);check(cached(uri,Collections.emptyMap(),upstream)==upstream,"Disabled cache still active");policy("cacheSelected",new Class<?>[]{Context.class,boolean.class},context(),true);
        policy("profile",new Class<?>[]{Context.class,int.class},context(),2);check(((Integer)policy("profile",new Class<?>[]{Context.class},context()))==2,"Profile not saved");policy("profile",new Class<?>[]{Context.class,int.class},context(),1);
        report("Disk cache exact bytes and offline Range, per-context isolation, private-source bypass and setting persistence verified");
    }
    private Activity open()throws Exception{
        ActivityMonitor monitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);
        Intent intent=new Intent().setComponent(new ComponentName("app.quanneng.next","app.quanneng.MainActivity")).setAction(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://app.quanneng.next.tests.fixture/seek-fixture.mp4"),"video/mp4").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        runOnMainSync(()->context().startActivity(intent));Activity p=waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);check(p!=null,"Player did not open");PrivateTestAccess.awaitUnlocked(this,p);awaitPlaying(p,300,20000);runOnMainSync(()->{check(p.findViewById(1810).getVisibility()==android.view.View.GONE,"Single video shows episode selector");check(p.findViewById(1824).getVisibility()==android.view.View.GONE,"Single video shows previous/next episode card");});return p;
    }
    private Object exo(Activity p)throws Exception{Object view=p.findViewById(1000),engine=PrivateTestAccess.field(view,"engine");return call(engine,"player",new Class<?>[]{});}
    private void play(Activity p,Object cached,Uri uri){runOnMainSync(()->{try{
        Class<?> factory=Class.forName("androidx.media3.exoplayer.source.ProgressiveMediaSource$Factory"),itemType=Class.forName("androidx.media3.common.MediaItem"),mediaType=Class.forName("androidx.media3.exoplayer.source.MediaSource"),retryType=Class.forName("androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy");
        Object f=factory.getConstructor(factoryType).newInstance(cached);Constructor<?> retry=Class.forName("app.quanneng.PlaybackPipeline$RetryPolicy").getDeclaredConstructor();retry.setAccessible(true);call(f,"setLoadErrorHandlingPolicy",new Class<?>[]{retryType},retry.newInstance());
        Object item=call(itemType,"fromUri",new Class<?>[]{Uri.class},uri),source=call(f,"createMediaSource",new Class<?>[]{itemType},item),exo=exo(p);
        call(exo,"setMediaSource",new Class<?>[]{mediaType},source);call(exo,"prepare",new Class<?>[]{});call(exo,"play",new Class<?>[]{});
    }catch(Exception e){throw new RuntimeException(e);}});}
    private void pause(Activity p){runOnMainSync(()->{try{call(exo(p),"pause",new Class<?>[]{});call(exo(p),"stop",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});}
    private void awaitPlaying(Activity p,long position,long timeout)throws Exception{
        long limit=SystemClock.elapsedRealtime()+timeout;AtomicBoolean good=new AtomicBoolean();while(SystemClock.elapsedRealtime()<limit){runOnMainSync(()->{try{Object v=p.findViewById(1000),engine=PrivateTestAccess.field(v,"engine");good.set((Integer)call(v,"getCurrentPosition",new Class<?>[]{})>position&&(Integer)call(engine,"getVideoWidth",new Class<?>[]{})>0&&(Boolean)call(v,"isPlaying",new Class<?>[]{}));}catch(Exception ignored){}});if(good.get())return;SystemClock.sleep(100);}throw new AssertionError("Video did not decode and advance beyond "+position);
    }
    private final class NativeNetwork {
        final AtomicInteger opens=new AtomicInteger(),bytes=new AtomicInteger();final AtomicBoolean interrupted=new AtomicBoolean();volatile boolean offline,throttled,interrupt;
        Object factory(){return Proxy.newProxyInstance(factoryType.getClassLoader(),new Class<?>[]{factoryType},(p,m,a)->{
            if(m.getName().equals("createDataSource"))return dataSource();if(m.getName().equals("toString"))return "FixtureFactory";return null;
        });}
        Object dataSource(){return Proxy.newProxyInstance(sourceType.getClassLoader(),new Class<?>[]{sourceType},new InvocationHandler(){
            int at,end;Uri uri;
            public Object invoke(Object p,Method method,Object[] args)throws Throwable{
                switch(method.getName()){
                    case "open":opens.incrementAndGet();if(offline)throw new IOException("Fixture upstream offline");Object spec=args[0];at=(int)specType.getField("position").getLong(spec);long length=specType.getField("length").getLong(spec);uri=(Uri)specType.getField("uri").get(spec);end=length<0?fixture.length:(int)Math.min(fixture.length,at+length);return (long)(end-at);
                    case "read":if(offline)throw new IOException("Fixture disconnected");if(interrupt&&at>=16384&&interrupted.compareAndSet(false,true))throw new IOException("One recoverable connection interruption");int n=Math.min(Math.min((Integer)args[2],throttled?256:8192),end-at);if(n==0)return -1;if(throttled)Thread.sleep(8);System.arraycopy(fixture,at,(byte[])args[0],(Integer)args[1],n);at+=n;bytes.addAndGet(n);return n;
                    case "getUri":return uri;
                    case "getResponseHeaders":return Collections.emptyMap();
                    case "toString":return "FixtureDataSource";
                    default:return null;
                }
            }
        });}
    }
}
