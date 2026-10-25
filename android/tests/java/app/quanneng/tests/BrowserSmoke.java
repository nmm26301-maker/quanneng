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
            long deadline=SystemClock.elapsedRealtime()+25000;boolean ready=false;
            while(SystemClock.elapsedRealtime()<deadline){if(count(browser)>=3&&"true".equals(js(web,"!!document.querySelector('video')&&document.querySelector('video').currentTime>0"))){ready=true;break;}SystemClock.sleep(150);}
            if(!ready)throw new AssertionError("Cross-frame/blob detector failed: "+count(browser)+" sources; advanced="+field(browser,"advanced"));
            runOnMainSync(()->{
                try{
                    Map<?,?> contexts=(Map<?,?>)field(browser,"contexts");JSONObject iframe=(JSONObject)contexts.get("https://media.fixture.test/stream?id=iframe");
                    if(iframe==null||!iframe.optString("page").startsWith("https://frame.fixture.test/"))throw new AssertionError("Nested frame source context lost");
                    if(((List<?>)field(browser,"media")).toString().contains("blob:"))throw new AssertionError("Unplayable blob was exported");
                }catch(Exception error){throw new RuntimeException(error);}
            });
            report("Cross-origin iframe, extensionless fetch/XHR sources and actual blob playback detected");runOnMainSync(browser::finish);SystemClock.sleep(300);
            Activity app=open("app.quanneng.MainActivity",new Intent());WebView ui=(WebView)field(app,"web");await(ui,"!!document.querySelector('.bottom-nav')");
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
    private String page(){return "<!doctype html><video muted loop playsinline></video><iframe src='https://frame.fixture.test/player'></iframe><script>fetch('https://media.fixture.test/stream?id=fetch').then(r=>r.blob()).then(b=>{let v=document.querySelector('video');v.src=URL.createObjectURL(b);v.play()});let x=new XMLHttpRequest();x.open('GET','https://media.fixture.test/stream?id=xhr');x.responseType='blob';x.send();</script>";}
    private String frame(){return "<!doctype html><video muted loop playsinline></video><script>fetch('https://media.fixture.test/stream?id=iframe').then(r=>r.blob()).then(b=>{let v=document.querySelector('video');v.src=URL.createObjectURL(b);v.play()});</script>";}
    private Activity open(String name,Intent intent){intent.setComponent(new ComponentName("app.quanneng",name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);Activity activity=startActivitySync(intent);try{PrivateTestAccess.awaitUnlocked(this,activity);}catch(Exception e){throw new RuntimeException(e);}return activity;}
    private Object field(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private int count(Activity a){AtomicReference<Integer> value=new AtomicReference<>(0);runOnMainSync(()->{try{value.set(((List<?>)field(a,"media")).size());}catch(Exception error){}});return value.get();}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(script,v->{value.set(v);done.countDown();}));if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("WebView JS timeout");return value.get();}
    private void await(WebView web,String expression)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){if("true".equals(js(web,expression)))return;SystemClock.sleep(100);}throw new AssertionError("UI condition failed: "+expression);}
    private void input(WebView web,int index,String value)throws Exception{js(web,"(()=>{let e=document.querySelectorAll('dialog input')["+index+"];Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,"+JSONObject.quote(value)+");e.dispatchEvent(new Event('input',{bubbles:true}));return true})()");SystemClock.sleep(200);}
    private void capture(){Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)return;Bitmap scaled=Bitmap.createScaledBitmap(bitmap,480,bitmap.getHeight()*480/bitmap.getWidth(),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,80,bytes);report("QUANNENG_UI_BOOKMARKS:"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));scaled.recycle();bitmap.recycle();}
    private void report(String value){Bundle data=new Bundle();data.putString("stream",value+"\n");sendStatus(0,data);}
}
