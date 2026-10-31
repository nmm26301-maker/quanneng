package app.quanneng;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 网页媒体观察；只接受候选数据，网页无法调用应用功能，Cookie 仅在本机内存使用。 */
public final class BrowserActivity extends PrivateActivity {
    private WebView web;
    private Button mediaButton;
    private TextView note;
    private Button fastButton,floatingPlay;
    private ProgressBar pageProgress;
    private boolean fastLoading=true;
    private long pageEpoch;
    private JSONObject handedContext;
    private static final int WEB_PLAYER=1461;
    private final Map<String,JSONObject> playedContexts=new LinkedHashMap<>();
    private FrameLayout root;
    private LinearLayout content;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<String> media=new ArrayList<>();
    private final Map<String,JSONObject> contexts=new HashMap<>();
    private final Map<String,JavaScriptReplyProxy> frames=new LinkedHashMap<>();
    private final Map<String,Map<String,String>> requests=Collections.synchronizedMap(new LinkedHashMap<String,Map<String,String>>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Map<String,String>> entry){return size()>256;}
    });
    private final Map<String,Long> requestTimes=Collections.synchronizedMap(new LinkedHashMap<String,Long>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> entry){return size()>256;}
    });
    private boolean advanced,playing,blob,encrypted;
    private String activeFrame="",activeSession="",activeTitle="";
    private boolean handingOff;
    private long webGestureAt=-60000;
    private final AtomicInteger generation=new AtomicInteger();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,12,16));setContentView(root);
        content=new LinearLayout(this);content.setOrientation(1);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setPadding(8,4,8,4);content.addView(toolbar);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());toolbar.addView(back);
        EditText address=new EditText(this);address.setSingleLine();address.setTextSize(13);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setText(getIntent().getStringExtra("url"));toolbar.addView(address,new LinearLayout.LayoutParams(0,dp(56),1));
        Button go=new Button(this);go.setText("打开");go.setOnClickListener(v->{String u=address.getText().toString().trim();if(MainActivity.isHttps(u))web.loadUrl(u);else toast("请填写完整的 HTTPS 网页地址");});toolbar.addView(go);
        LinearLayout pageTools=new LinearLayout(this);pageTools.setPadding(dp(8),0,dp(8),0);content.addView(pageTools);
        fastLoading=getSharedPreferences("browser",0).getBoolean("fast-loading",true);
        fastButton=new Button(this);fastButton.setId(1410);pageTools.addView(fastButton,new LinearLayout.LayoutParams(0,dp(48),1));
        fastButton.setOnClickListener(v->{fastLoading=!fastLoading;getSharedPreferences("browser",0).edit().putBoolean("fast-loading",fastLoading).apply();updateFastButton();if(!fastLoading)web.getSettings().setBlockNetworkImage(false);toast(fastLoading?"下次打开网页优先加载内容，图片稍后显示":"已切换完整加载");});updateFastButton();
        Button refresh=new Button(this);refresh.setText("刷新");refresh.setOnClickListener(v->web.reload());pageTools.addView(refresh,new LinearLayout.LayoutParams(dp(80),dp(48)));
        pageProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pageProgress.setMax(100);content.addView(pageProgress,new LinearLayout.LayoutParams(-1,dp(3)));
        web=new WebView(this);content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        mediaButton=new Button(this);mediaButton.setId(1401);mediaButton.setSingleLine();mediaButton.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(mediaButton,new LinearLayout.LayoutParams(-1,dp(60)));mediaButton.setOnClickListener(v->scan(true));
        note=new TextView(this);note.setTextSize(11);note.setTextColor(Color.rgb(169,183,184));note.setPadding(16,8,16,12);content.addView(note);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);s.setBlockNetworkImage(fastLoading);s.setMediaPlaybackRequiresUserGesture(true);
        floatingPlay=new Button(this);floatingPlay.setText("转到全能播放");floatingPlay.setOnClickListener(v->scan(true));floatingPlay.setVisibility(View.GONE);
        configureDiscovery();updateCount();
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int progress){pageProgress.setProgress(progress);pageProgress.setVisibility(progress>=100?View.INVISIBLE:View.VISIBLE);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(customView!=null){callback.onCustomViewHidden();return;}
                customView=view;customCallback=callback;content.setVisibility(View.GONE);root.addView(view,new FrameLayout.LayoutParams(-1,-1));getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                FrameLayout.LayoutParams action=new FrameLayout.LayoutParams(dp(200),dp(52),Gravity.BOTTOM|Gravity.END);action.setMargins(dp(16),dp(16),dp(16),dp(16));root.addView(floatingPlay,action);updateCount();
            }
            @Override public void onHideCustomView(){hideCustomView();}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(MainActivity.isHttps(request.getUrl().toString()))return false;toast("不支持此链接类型");return true;}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){resetDiscovery();address.setText(url);long epoch=++pageEpoch;pageProgress.setVisibility(View.VISIBLE);web.getSettings().setBlockNetworkImage(fastLoading);
                handler.postDelayed(()->releaseImages(epoch),2500);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String u=request.getUrl().toString();if(MainActivity.isHttps(u)){requests.put(u,PlaybackHeaders.sanitize(request.getRequestHeaders()));requestTimes.put(u,SystemClock.elapsedRealtime());}
                return null;
            }
            @Override public void onPageCommitVisible(WebView view,String url){long epoch=pageEpoch;handler.postDelayed(()->releaseImages(epoch),350);}
            @Override public void onPageFinished(WebView view,String url){address.setText(url);releaseImages(pageEpoch);scan(false);}
        });
        String url=getIntent().getStringExtra("url");if(MainActivity.isHttps(url))whenUnlocked(()->{
            Bundle saved=state==null?null:state.getBundle("browser-web-state");
            if(saved==null||web.restoreState(saved)==null)web.loadUrl(url);
        });else finish();
    }
    private void configureDiscovery(){
        advanced=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)&&WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if(!advanced)return;
        try{
            String script;try(InputStream input=getAssets().open("media-discovery.js");ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] block=new byte[4096];int count;while((count=input.read(block))!=-1)output.write(block,0,count);script=output.toString("UTF-8");
            }
            Set<String> origins=Collections.singleton("*");
            WebViewCompat.addWebMessageListener(web,"QuannengMedia",origins,(view,message,origin,mainFrame,reply)->{
                if(isFinishing()||isDestroyed()||!"https".equals(origin.getScheme()))return;
                String raw=message.getData();if(raw==null||raw.length()>16384)return;
                try{
                    JSONObject data=new JSONObject(raw);String frame=data.optString("frame","");
                    if(!sameOrigin(frame,origin))return;
                    if(frames.size()<64||frames.containsKey(frame))frames.put(frame,reply);
                    String kind=data.optString("kind"),session=data.optString("session");
                    if("activate".equals(kind)&&SystemClock.elapsedRealtime()-webGestureAt<15000){
                        media.clear();contexts.clear();activeFrame=frame;activeSession=session;activeTitle=cleanTitle(data.optString("title"),frame);playing=true;blob=false;encrypted=false;updateCount();
                    }else if("source".equals(kind)&&frame.equals(activeFrame)&&session.equals(activeSession))collect(data.optString("url",""),null,frame,data.optString("mime",""),data.optBoolean("direct"));
                    else if("status".equals(kind)&&frame.equals(activeFrame)&&session.equals(activeSession)){playing|=data.optBoolean("playing");blob|=data.optBoolean("blob");encrypted|=data.optBoolean("encrypted");updateCount();}
                }catch(JSONException ignored){}
            });
            WebViewCompat.addDocumentStartJavaScript(web,script,origins);
        }catch(IOException|RuntimeException error){advanced=false;}
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&web!=null&&privateReady()){
            android.graphics.Rect bounds=new android.graphics.Rect();
            if(customView!=null||web.getGlobalVisibleRect(bounds)&&bounds.contains((int)event.getRawX(),(int)event.getRawY()))armWebGesture();
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&web!=null&&web.hasFocus()&&privateReady()&&(event.getKeyCode()==KeyEvent.KEYCODE_ENTER||event.getKeyCode()==KeyEvent.KEYCODE_SPACE||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER))armWebGesture();
        return super.dispatchKeyEvent(event);
    }
    private void armWebGesture(){
        webGestureAt=SystemClock.elapsedRealtime();
        for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("gesture");}catch(RuntimeException ignored){}
    }
    private static boolean sameOrigin(String frame,Uri origin){
        if(!MainActivity.isHttps(frame))return false;Uri uri=Uri.parse(frame);
        return Objects.equals(uri.getHost(),origin.getHost())&&(uri.getPort()==-1?443:uri.getPort())==(origin.getPort()==-1?443:origin.getPort());
    }
    private void resetDiscovery(){generation.incrementAndGet();media.clear();contexts.clear();frames.clear();requests.clear();requestTimes.clear();playing=false;blob=false;encrypted=false;activeFrame="";activeSession="";activeTitle="";handingOff=false;webGestureAt=-60000;updateCount();}
    private void collect(String url,Map<String,String> requestHeaders,String frame,String observedMime,boolean direct){
        if(!MainActivity.isHttps(url)||url.length()>8192||(!activeFrame.isEmpty()&&!Objects.equals(activeFrame,frame)))return;
        if(!MediaCandidatePolicy.exportable(url,direct))return;
        final int seenOn=generation.get();
        HashMap<String,String> captured=PlaybackHeaders.sanitize(requestHeaders==null?requests.get(url):requestHeaders);
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed()||seenOn!=generation.get())return;
            if(!media.contains(url)&&media.size()<100)media.add(url);
            if(!media.contains(url))return;
            JSONObject old=contexts.get(url);HashMap<String,String> headers=new HashMap<>();
            if(old!=null){JSONObject h=old.optJSONObject("headers");if(h!=null){Iterator<String> keys=h.keys();while(keys.hasNext()){String k=keys.next();headers.put(k,h.optString(k));}}}
            headers.putAll(captured);String page=frame!=null?frame:old!=null?old.optString("page",web.getUrl()):web.getUrl();
            if(!headers.containsKey("Referer")){String ref=PlaybackHeaders.referrer(page,url);if(!ref.isEmpty())headers.put("Referer",ref);}
            headers.put("User-Agent",web.getSettings().getUserAgentString());String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)headers.put("Cookie",cookie);
            try{JSONObject c=new JSONObject();c.put("url",url);c.put("page",web.getUrl());c.put("frame",page);c.put("title",activeTitle.isEmpty()?cleanTitle("",page):activeTitle);String mime=MediaCandidatePolicy.mime(url,observedMime);if(mime.isEmpty()&&old!=null)mime=old.optString("mime","");c.put("mime",mime);c.put("headers",new JSONObject(PlaybackHeaders.sanitize(headers)));contexts.put(url,c);}catch(JSONException ignored){}
            updateCount();
        });
    }
    private String cleanTitle(String value,String frame){
        String title=value==null?"":value.replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
        String pageTitle=web.getTitle();
        if(pageTitle!=null&&!pageTitle.trim().isEmpty()){
            pageTitle=pageTitle.replaceAll("[\\p{Cntrl}]"," ").trim();
            if(title.isEmpty()||title.matches("(?i).*(?:^player$|^video$|^播放器$|^在线播放$|^视频播放$).*"))title=pageTitle;
            else if(!Objects.equals(frame,web.getUrl())&&!title.contains(pageTitle)&&!pageTitle.contains(title))title=pageTitle+" · "+title;
        }
        if(title.isEmpty())title="当前点播视频";
        return title.substring(0,Math.min(180,title.length()));
    }
    private void updateCount(){
        if(mediaButton==null)return;
        boolean available=!media.isEmpty()&&!encrypted&&!handingOff;
        mediaButton.setEnabled(available);floatingPlay.setEnabled(available);floatingPlay.setVisibility(customView!=null?View.VISIBLE:View.GONE);
        mediaButton.setText(handingOff?"正在打开播放器…":media.isEmpty()?"先在网页播放，再点此转到全能":"转到全能播放 · "+activeTitle);
        if(note!=null)note.setText(!advanced?"请先更新 Android 系统 WebView，才能识别你点播的视频。":encrypted?"此视频使用受保护媒体，请继续在原网页观看。":media.isEmpty()?(playing?"正在识别当前点播视频…暂未取得可独立播放的地址。":"网页开始播放后识别当前视频，点“转到全能播放”才打开播放器。"):"已备注："+activeTitle+" · "+Uri.parse(web.getUrl()).getHost()+"\n手动点按钮转接；退出播放器回到此网页。");
    }
    private void collectNativeRequests(){
        long now=SystemClock.elapsedRealtime(),cutoff=webGestureAt>0?webGestureAt-5000:Long.MAX_VALUE;
        String frame=activeFrame.isEmpty()?(web==null?"":web.getUrl()):activeFrame;
        ArrayList<String> candidates=new ArrayList<>();
        synchronized(requestTimes){for(Map.Entry<String,Long> entry:requestTimes.entrySet()){
            if(entry.getValue()>=cutoff&&entry.getValue()<=now&&MediaCandidatePolicy.playable(entry.getKey()))candidates.add(entry.getKey());
        }}
        for(String url:candidates)collect(url,requests.get(url),frame,"",false);
    }
    private void scan(boolean show){
        if(media.isEmpty())collectNativeRequests();
        for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("scan");}catch(RuntimeException ignored){}
        if(show)awaitScanResult(0);
    }
    private void awaitScanResult(int attempt){
        if(handingOff)return;
        if(!media.isEmpty()){handoff(best());return;}
        if(attempt<8){handler.postDelayed(()->{if(media.isEmpty())collectNativeRequests();for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("scan");}catch(RuntimeException ignored){}awaitScanResult(attempt+1);},200);return;}
        toast(encrypted?"受保护媒体请在原网页观看":"请先在网页中点播放；只识别你当前点播的视频");
    }
    private String best(){
        return Collections.min(media,Comparator.comparingInt(u->MediaCandidatePolicy.priority(u,contexts.containsKey(u)?contexts.get(u).optString("mime",""):"")));
    }
    private void handoff(String value){
        if(handingOff||encrypted||!privateReady()||isFinishing())return;
        JSONObject context=contexts.get(value);if(context==null)return;
        handingOff=true;handedContext=context;playedContexts.put(value,context);if(playedContexts.size()>32)playedContexts.remove(playedContexts.keySet().iterator().next());
        BrowserHistory.remember(this,context,PlaybackHistory.position(this,value),0);
        Intent intent=new Intent(this,SystemPlayerActivity.class).setData(Uri.parse(value)).putExtra("returnToBrowser",true)
            .putExtra("title",context.optString("title","网络视频")).putExtra("sourcePage",context.optString("page",""))
            .putExtra("mime",context.optString("mime","")).putExtra("headers",context.optJSONObject("headers")==null?"{}":context.optJSONObject("headers").toString())
            .putExtra("userAgent",web.getSettings().getUserAgentString());
        for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("pause");}catch(RuntimeException ignored){}
        hideCustomView();updateCount();
        try{startActivityForResult(intent,WEB_PLAYER);}catch(RuntimeException error){handingOff=false;updateCount();toast("播放器打开失败，请重试");}
    }
    private void releaseImages(long epoch){if(web!=null&&!isDestroyed()&&epoch==pageEpoch)web.getSettings().setBlockNetworkImage(false);}
    private void updateFastButton(){fastButton.setText(fastLoading?"网页加速：开":"网页加速：关");fastButton.setSelected(fastLoading);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==WEB_PLAYER){
            handingOff=false;
            if(data!=null&&handedContext!=null)BrowserHistory.remember(this,handedContext,data.getIntExtra("position",0),data.getIntExtra("duration",0));
            whenUnlocked(()->{if(web!=null)web.onResume();updateCount();});
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){Bundle saved=new Bundle();web.saveState(saved);state.putBundle("browser-web-state",saved);super.onSaveInstanceState(state);}
    @Override public void finish(){
        JSONArray remembered=new JSONArray();for(JSONObject context:playedContexts.values())if(remembered.toString().length()+context.toString().length()<250000)remembered.put(context);
        setResult(RESULT_OK,new Intent().putExtra("browserContexts",remembered.toString()));super.finish();
    }
    private void hideCustomView(){if(customView==null)return;root.removeView(customView);if(floatingPlay.getParent()==root)root.removeView(floatingPlay);floatingPlay.setVisibility(View.GONE);customView=null;content.setVisibility(View.VISIBLE);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String text){runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(customView!=null)hideCustomView();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null&&privateReady())web.onResume();}
    @Override protected void onPrivateUnlocked(){if(web!=null)web.onResume();}
    @Override protected void onPrivateLocked(){if(web!=null){web.onPause();web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("pause");}catch(RuntimeException ignored){}}}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);hideCustomView();frames.clear();requests.clear();requestTimes.clear();contexts.clear();if(web!=null)web.destroy();super.onDestroy();}
}
