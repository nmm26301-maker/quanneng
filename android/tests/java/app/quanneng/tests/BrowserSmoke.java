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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** 原生 WebView 回归：跨域 iframe、无后缀媒体、blob 原网页播放及收藏 CRUD。 */
public final class BrowserSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        PrivateTestAccess.install(this);Bundle result=new Bundle();
        try{
            Activity browser=open("app.quanneng.BrowserActivity",new Intent().putExtra("url","https://page.fixture.test/index"));
            WebView web=(WebView)field(browser,"web");
            AtomicBoolean deferred=new AtomicBoolean();AtomicInteger pageLoads=new AtomicInteger(),images=new AtomicInteger();
            runOnMainSync(()->{
                web.getSettings().setMediaPlaybackRequiresUserGesture(false);WebViewClient original=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public void onPageStarted(WebView v,String url,Bitmap icon){original.onPageStarted(v,url,icon);if(url.contains("ready=1"))deferred.set(v.getSettings().getBlockNetworkImage());}
                    @Override public void onPageCommitVisible(WebView v,String url){original.onPageCommitVisible(v,url);}
                    @Override public void onPageFinished(WebView v,String url){original.onPageFinished(v,url);}
                    @Override public void onLoadResource(WebView v,String url){original.onLoadResource(v,url);}
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                        original.shouldInterceptRequest(v,request);String url=request.getUrl().toString();
                        try{
                            String host=request.getUrl().getHost();if(host==null||!host.endsWith(".fixture.test"))return null;
                            Map<String,String> headers=new HashMap<>();headers.put("Access-Control-Allow-Origin","*");headers.put("Cache-Control","no-store");
                            if(request.isForMainFrame()&&url.contains("ready=1"))pageLoads.incrementAndGet();
                            if(url.contains("poster.png")){images.incrementAndGet();byte[] png=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC",0);return new WebResourceResponse("image/png",null,200,"OK",headers,new ByteArrayInputStream(png));}
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
            ActivityMonitor postMonitor=addMonitor("app.quanneng.TransferActivity",null,false);
            runOnMainSync(()->browser.findViewById(1420).performClick());
            Activity post=waitForMonitorWithTimeout(postMonitor,10000);removeMonitor(postMonitor);
            if(post==null)throw new AssertionError("Post video fallback did not open download screen");PrivateTestAccess.awaitUnlocked(this,post);
            if(!"https://page.fixture.test/index?ready=1".equals(((android.widget.EditText)post.findViewById(1600)).getText().toString()))throw new AssertionError("Post URL not handed to download screen");
            runOnMainSync(post::finish);awaitBrowser(browser);

            AtomicReference<Intent> handed=new AtomicReference<>();AtomicInteger nativeStarts=new AtomicInteger();
            ActivityMonitor handoff=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){
                if(intent.getComponent()!=null&&"app.quanneng.SystemPlayerActivity".equals(intent.getComponent().getClassName())){
                    handed.set(new Intent(intent));nativeStarts.incrementAndGet();
                    // Keep the production activity stack and extras; only replace inaccessible test HTTPS bytes.
                    intent.setData(android.net.Uri.parse("content://app.quanneng.next.tests.fixture/buffer-fixture.mp4"));
                }return null;
            }};addMonitor(handoff);
            AtomicReference<int[]> point=new AtomicReference<>();runOnMainSync(()->{int[] xy=new int[2];web.getLocationOnScreen(xy);float d=web.getResources().getDisplayMetrics().density;point.set(new int[]{xy[0]+(int)(90*d),xy[1]+(int)(60*d)});});
            int[] xy=point.get();tap(xy[0],xy[1]);
            long deadline=SystemClock.elapsedRealtime()+15000;while(count(browser)==0&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            if(count(browser)==0)throw new AssertionError("Clicked blob not identified automatically; advanced="+field(browser,"advanced")+", frame="+field(browser,"activeFrame"));
            SystemClock.sleep(1800);
            if(nativeStarts.get()!=0||browser.isFinishing())throw new AssertionError("Web playback or delayed detection launched the player without clicking transfer");
            await(web,"document.querySelector('#poster').complete&&document.querySelector('#poster').naturalWidth>0");
            if(!deferred.get()||images.get()==0)throw new AssertionError("Fast loading did not defer then restore image resources");
            if(!"true".equals(js(web,"window.returnMarker='same-page';document.querySelector('#return-value').value='第12集保留';true")))throw new AssertionError("Cannot set return marker");
            int loadsBefore=pageLoads.get();String originalUrl=js(web,"location.href");
            ActivityMonitor playerMonitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);tap(browser.findViewById(1401));
            Activity player=waitForMonitorWithTimeout(playerMonitor,15000);removeMonitor(playerMonitor);
            if(player==null)throw new AssertionError("Explicit transfer button did not launch player");PrivateTestAccess.awaitUnlocked(this,player);
            Intent selected=handed.get();if(selected==null||!"https://media.fixture.test/stream?id=chosen".equals(selected.getDataString())||!selected.getBooleanExtra("returnToBrowser",false)||!"video/mp4".equals(selected.getStringExtra("mime")))throw new AssertionError("Selected media context was not forwarded to native player");
            if(!selected.getStringExtra("title").contains("12")||!selected.getStringExtra("sourcePage").startsWith("https://page.fixture.test/"))throw new AssertionError("Title/source page lost");
            awaitNative(player,false);
            runOnMainSync(()->{Object video=player.findViewById(1000);try{int duration=((Number)video.getClass().getMethod("getDuration").invoke(video)).intValue();video.getClass().getMethod("seekTo",int.class).invoke(video,duration-700);video.getClass().getMethod("start").invoke(video);}catch(Exception e){throw new RuntimeException(e);}});
            awaitNative(player,true);pressBack();awaitBrowser(browser);
            if(browser.isFinishing()||field(browser,"web")!=web||!originalUrl.equals(js(web,"location.href"))||pageLoads.get()!=loadsBefore)throw new AssertionError("Back recreated, navigated or reloaded the original web page");
            await(web,"window.returnMarker==='same-page'&&document.querySelector('#return-value').value==='第12集保留'");
            SystemClock.sleep(1000);if(nativeStarts.get()!=1)throw new AssertionError("Returning to browser transferred again without a click");
            // Error fallback must return to this existing page too.
            playerMonitor=addMonitor("app.quanneng.SystemPlayerActivity",null,false);tap(browser.findViewById(1401));Activity retry=waitForMonitorWithTimeout(playerMonitor,15000);removeMonitor(playerMonitor);
            if(retry==null)throw new AssertionError("Repeat manual transfer failed");PrivateTestAccess.awaitUnlocked(this,retry);awaitNative(retry,false);
            runOnMainSync(()->{try{Method failure=retry.getClass().getDeclaredMethod("showFailure",int.class);failure.setAccessible(true);failure.invoke(retry,3003);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
            boolean returned=false;deadline=SystemClock.elapsedRealtime()+5000;
            while(!returned&&SystemClock.elapsedRealtime()<deadline){
                android.view.accessibility.AccessibilityNodeInfo dialogRoot=getUiAutomation().getRootInActiveWindow();
                if(dialogRoot!=null)for(android.view.accessibility.AccessibilityNodeInfo node:dialogRoot.findAccessibilityNodeInfosByText("返回原网页"))if("返回原网页".contentEquals(node.getText()==null?"":node.getText())&&node.isClickable()){returned=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);break;}
                if(!returned)SystemClock.sleep(100);
            }
            if(!returned)throw new AssertionError("Error dialog did not offer return to existing page");awaitBrowser(browser);
            if(!originalUrl.equals(js(web,"location.href"))||pageLoads.get()!=loadsBefore)throw new AssertionError("Error return lost the page");
            runOnMainSync(()->browser.findViewById(1410).performClick());
            if(getTargetContext().getSharedPreferences("browser",0).getBoolean("fast-loading",true))throw new AssertionError("Fast mode setting not persisted");
            runOnMainSync(()->browser.findViewById(1410).performClick());runOnMainSync(browser::finish);SystemClock.sleep(200);
            Activity app=open("app.quanneng.MainActivity",new Intent());WebView ui=(WebView)field(app,"web");await(ui,"!!document.querySelector('.bottom-nav')");
            await(ui,"JSON.parse(localStorage.getItem('quanneng-v2')||'[]').some(v=>v.name.includes('12')&&v.sourcePage.startsWith('https://page.fixture.test/'))");
            if(nativeStarts.get()!=2)throw new AssertionError("History synchronization started a video automatically");removeMonitor(handoff);
            report("Click playback identifies the selected video; manual transfer only; decoded completion and back preserve original URL, DOM and form state without reload");
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
    private String page(){return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>测试剧 第12集</title><style>body{margin:0}iframe{border:0;width:100%;height:300px}</style><iframe src='https://frame.fixture.test/player'></iframe><input id='return-value'><img id='poster' src='https://page.fixture.test/poster.png'><video muted autoplay loop src='https://media.fixture.test/stream?id=autoplay'></video><video preload='auto' src='https://media.fixture.test/stream?id=preload'></video>";}
    private String frame(){return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>测试剧 第12集</title><style>body{margin:0}button{position:absolute;left:0;top:0;width:200px;height:120px}video{margin-top:130px}</style><button onclick=\"fetch('https://media.fixture.test/stream?id=chosen').then(r=>r.blob()).then(b=>{let v=document.querySelector('video');v.src=window.URL.createObjectURL(b);v.play()})\">播放第12集</button><video muted loop playsinline></video><video preload='auto' src='https://media.fixture.test/stream?id=other'></video>";}
    private void tap(android.view.View view){AtomicReference<int[]> point=new AtomicReference<>();runOnMainSync(()->{int[] xy=new int[2];view.getLocationOnScreen(xy);point.set(new int[]{xy[0]+view.getWidth()/2,xy[1]+view.getHeight()/2});});int[] xy=point.get();tap(xy[0],xy[1]);}
    private void tap(int x,int y){long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+80,1,x,y,0);down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();}
    private void pressBack(){long now=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new android.view.KeyEvent(now,now,0,android.view.KeyEvent.KEYCODE_BACK,0),true);getUiAutomation().injectInputEvent(new android.view.KeyEvent(now,now+50,1,android.view.KeyEvent.KEYCODE_BACK,0),true);}
    private void awaitBrowser(Activity browser)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> ready=new AtomicReference<>(false);runOnMainSync(()->{try{ready.set(browser.hasWindowFocus()&&!(Boolean)field(browser,"handingOff"));}catch(Exception ignored){}});if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError("Browser did not resume after player back");}
    private void awaitNative(Activity player,boolean ended)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean> ready=new AtomicReference<>(false);runOnMainSync(()->{try{Object v=player.findViewById(1000);int duration=((Number)v.getClass().getMethod("getDuration").invoke(v)).intValue(),position=((Number)v.getClass().getMethod("getCurrentPosition").invoke(v)).intValue();boolean playing=(Boolean)v.getClass().getMethod("isPlaying").invoke(v);ready.set(duration>1000&&(ended?!playing&&position>=duration-500:playing&&position>100));}catch(Exception ignored){}});if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError("Actual native playback state missing; ended="+ended);}
    private Activity open(String name,Intent intent){intent.setComponent(new ComponentName("app.quanneng.next",name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);Activity activity=startActivitySync(intent);try{PrivateTestAccess.awaitUnlocked(this,activity);}catch(Exception e){throw new RuntimeException(e);}return activity;}
    private Object field(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private int count(Activity a){AtomicReference<Integer> value=new AtomicReference<>(0);runOnMainSync(()->{try{value.set(((List<?>)field(a,"media")).size());}catch(Exception error){}});return value.get();}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(script,v->{value.set(v);done.countDown();}));if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("WebView JS timeout");return value.get();}
    private void await(WebView web,String expression)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){if("true".equals(js(web,expression)))return;SystemClock.sleep(100);}throw new AssertionError("UI condition failed: "+expression);}
    private void input(WebView web,int index,String value)throws Exception{js(web,"(()=>{let e=document.querySelectorAll('dialog input')["+index+"];Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,"+JSONObject.quote(value)+");e.dispatchEvent(new Event('input',{bubbles:true}));return true})()");SystemClock.sleep(200);}
    private void capture(){Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)return;Bitmap scaled=Bitmap.createScaledBitmap(bitmap,480,bitmap.getHeight()*480/bitmap.getWidth(),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,80,bytes);report("QUANNENG_UI_BOOKMARKS:"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));scaled.recycle();bitmap.recycle();}
    private void report(String value){Bundle data=new Bundle();data.putString("stream",value+"\n");sendStatus(0,data);}
}
