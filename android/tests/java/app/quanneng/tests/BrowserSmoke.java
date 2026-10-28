package app.quanneng.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** 原生 WebView 回归：跨域 iframe、无后缀媒体、blob 原网页播放及收藏 CRUD。 */
public final class BrowserSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        PrivateTestAccess.install(this);Bundle result=new Bundle();
        try{
            Activity browser=open("app.quanneng.BrowserActivity",new Intent().putExtra("url","https://page.fixture.test/index"));
            WebView web=(WebView)field(browser,"web");
            runOnMainSync(()->{
                web.getSettings().setMediaPlaybackRequiresUserGesture(false);WebViewClient original=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public void onPageStarted(WebView v,String url,Bitmap icon){original.onPageStarted(v,url,icon);}
                    @Override public void onPageFinished(WebView v,String url){original.onPageFinished(v,url);}
                    @Override public void onLoadResource(WebView v,String url){original.onLoadResource(v,url);}
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                        original.shouldInterceptRequest(v,request);String url=request.getUrl().toString();
                        try{
                            String host=request.getUrl().getHost();if(host==null||!host.endsWith(".fixture.test"))return null;
                            Map<String,String> headers=new HashMap<>();headers.put("Access-Control-Allow-Origin","*");headers.put("Cache-Control","no-store");
                            if(url.contains("/stream"))return new WebResourceResponse("video/mp4",null,200,"OK",headers,getContext().getAssets().open("flower.mp4"));
                            String html=url.contains("frame.fixture")?frame():page();
                            return new WebResourceResponse("text/html","UTF-8",200,"OK",headers,new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                        }catch(IOException error){throw new RuntimeException(error);}
                    }
                });web.loadUrl("https://page.fixture.test/index?ready=1");
            });
            await(web,"!!document.querySelector('iframe')");SystemClock.sleep(2000);
            js(web,"window.QuannengMedia.postMessage(JSON.stringify({kind:'activate',frame:location.href,session:'forged',title:'forged'}));window.QuannengMedia.postMessage(JSON.stringify({kind:'source',frame:location.href,session:'forged',url:'https://media.fixture.test/stream?id=forged',mime:'video/mp4'}));true");SystemClock.sleep(300);
            if(count(browser)!=0)throw new AssertionError("Preloads/autoplay exported before the user's play gesture");
            AtomicReference<Intent> handed=new AtomicReference<>();
            ActivityMonitor handoff=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_SEND.equals(intent.getAction())&&intent.hasExtra("mediaContext")){handed.set(new Intent(intent));return new ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
            addMonitor(handoff);
            AtomicReference<int[]> point=new AtomicReference<>();runOnMainSync(()->{int[] xy=new int[2];web.getLocationOnScreen(xy);float d=web.getResources().getDisplayMetrics().density;point.set(new int[]{xy[0]+(int)(90*d),xy[1]+(int)(60*d)});});
            int[] xy=point.get();long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,xy[0],xy[1],0),up=android.view.MotionEvent.obtain(now,now+80,1,xy[0],xy[1],0);down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();
            long deadline=SystemClock.elapsedRealtime()+20000;while(handed.get()==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);removeMonitor(handoff);
            Intent selected=handed.get();if(selected==null)throw new AssertionError("User play did not automatically hand off the selected cross-frame blob");
            if(!"https://media.fixture.test/stream?id=chosen".equals(selected.getStringExtra(Intent.EXTRA_TEXT)))throw new AssertionError("Unrelated video handed off");
            JSONObject metadata=new JSONObject(selected.getStringExtra("mediaContext"));
            if(!metadata.optString("title").contains("12")||!metadata.optString("frame").startsWith("https://frame.fixture.test/")||!metadata.optString("page").startsWith("https://page.fixture.test/"))throw new AssertionError("Title, episode or source metadata lost: "+metadata);
            AtomicReference<Intent> nativeIntent=new AtomicReference<>();
            ActivityMonitor nativeMonitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&"app.quanneng.SystemPlayerActivity".equals(intent.getComponent().getClassName())){nativeIntent.set(new Intent(intent));return new ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
            addMonitor(nativeMonitor);
            Activity app=open("app.quanneng.MainActivity",selected);WebView ui=(WebView)field(app,"web");await(ui,"!!document.querySelector('.bottom-nav')");
            await(ui,"JSON.parse(localStorage.getItem('quanneng-v2')||'[]').some(v=>v.name.includes('12')&&v.sourcePage.startsWith('https://page.fixture.test/'))");
            deadline=SystemClock.elapsedRealtime()+10000;while(nativeIntent.get()==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);removeMonitor(nativeMonitor);
            if(nativeIntent.get()==null||!nativeIntent.get().getStringExtra("title").contains("12")||!"video/mp4".equals(nativeIntent.get().getStringExtra("mime")))throw new AssertionError("Extensionless selected source did not reach native player with metadata");
            report("No preloads/autoplay exported; actual cross-frame user tap automatically handed off only the selected blob; title/source persisted and extensionless MIME forwarded");
            if(!"true".equals(js(ui,"!document.body.innerText.includes('AI')&&typeof window.Quanneng.requestAI==='undefined'")))throw new AssertionError("AI feature or bridge remains");
            js(ui,"Array.from(document.querySelectorAll('.bottom-nav button')).find(b=>b.textContent.includes('网址收藏')).click();true");await(ui,"!!document.querySelector('.bookmark-intro')");
            js(ui,"document.querySelector('.bookmark-intro button').click();true");await(ui,"!!document.querySelector('dialog[open]')");
            input(ui,0,"演示网址");input(ui,1,"https://example.com/watch");js(ui,"document.querySelector('dialog form').requestSubmit();true");await(ui,"document.querySelector('.bookmark-card h3')?.textContent==='演示网址'");
            js(ui,"document.querySelector('.bookmark-tools button').click();true");await(ui,"!!document.querySelector('dialog[open]')");input(ui,0,"我的收藏");js(ui,"document.querySelector('dialog form').requestSubmit();true");await(ui,"document.querySelector('.bookmark-card h3')?.textContent==='我的收藏'");
            capture();runOnMainSync(app::finish);SystemClock.sleep(300);
            Activity reopened=open("app.quanneng.MainActivity",new Intent());ui=(WebView)field(reopened,"web");await(ui,"!!document.querySelector('.bottom-nav')");js(ui,"Array.from(document.querySelectorAll('.bottom-nav button')).find(b=>b.textContent.includes('网址收藏')).click();true");await(ui,"document.querySelector('.bookmark-card h3')?.textContent==='我的收藏'");
            js(ui,"document.querySelectorAll('.bookmark-tools button')[1].click();true");await(ui,"!!document.querySelector('dialog[open]')");js(ui,"Array.from(document.querySelectorAll('dialog button')).find(b=>b.textContent==='确认删除').click();true");await(ui,"!document.querySelector('.bookmark-card')&&localStorage.getItem('quanneng-bookmarks-v1')==='[]'");
            report("AI UI and native request bridge removed; bookmark add, rename, restart persistence and delete verified");runOnMainSync(reopened::finish);
            result.putString("stream","QUANNENG_BROWSER_OK\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","QUANNENG_BROWSER_FAILED: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private String page(){return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>测试剧 第12集</title><style>body{margin:0}iframe{border:0;width:100%;height:300px}</style><iframe src='https://frame.fixture.test/player'></iframe><video muted autoplay loop src='https://media.fixture.test/stream?id=autoplay'></video><video preload='auto' src='https://media.fixture.test/stream?id=preload'></video>";}
    private String frame(){return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>测试剧 第12集</title><style>body{margin:0}button{position:absolute;left:0;top:0;width:200px;height:120px}video{margin-top:130px}</style><button onclick=\"fetch('https://media.fixture.test/stream?id=chosen').then(r=>r.blob()).then(b=>{let v=document.querySelector('video');v.src=URL.createObjectURL(b);v.play()})\">播放第12集</button><video muted loop playsinline></video><video preload='auto' src='https://media.fixture.test/stream?id=other'></video>";}
    private Activity open(String name,Intent intent){intent.setComponent(new ComponentName("app.quanneng.next",name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);Activity activity=startActivitySync(intent);try{PrivateTestAccess.awaitUnlocked(this,activity);}catch(Exception e){throw new RuntimeException(e);}return activity;}
    private Object field(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private int count(Activity a){AtomicReference<Integer> value=new AtomicReference<>(0);runOnMainSync(()->{try{value.set(((List<?>)field(a,"media")).size());}catch(Exception error){}});return value.get();}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(script,v->{value.set(v);done.countDown();}));if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("WebView JS timeout");return value.get();}
    private void await(WebView web,String expression)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){if("true".equals(js(web,expression)))return;SystemClock.sleep(100);}throw new AssertionError("UI condition failed: "+expression);}
    private void input(WebView web,int index,String value)throws Exception{js(web,"(()=>{let e=document.querySelectorAll('dialog input')["+index+"];Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,"+JSONObject.quote(value)+");e.dispatchEvent(new Event('input',{bubbles:true}));return true})()");SystemClock.sleep(200);}
    private void capture(){Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)return;Bitmap scaled=Bitmap.createScaledBitmap(bitmap,480,bitmap.getHeight()*480/bitmap.getWidth(),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,80,bytes);report("QUANNENG_UI_BOOKMARKS:"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));scaled.recycle();bitmap.recycle();}
    private void report(String value){Bundle data=new Bundle();data.putString("stream",value+"\n");sendStatus(0,data);}
}
