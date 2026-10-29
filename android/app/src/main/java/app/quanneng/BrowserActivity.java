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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 网页媒体观察；只接受候选数据，网页无法调用应用功能，Cookie 仅在本机内存使用。 */
public final class BrowserActivity extends PrivateActivity {
    private WebView web;
    private Button mediaButton,identifyButton;
    private TextView note;
    private Button fastButton,floatingPlay;
    private ProgressBar pageProgress;
    private boolean fastLoading;
    private long pageEpoch;
    private JSONObject handedContext;
    private static final int WEB_PLAYER=1461;
    private final Map<String,JSONObject> playedContexts=new LinkedHashMap<>();
    private final Map<String,Long> recentMediaRequests=new LinkedHashMap<>();
    private final ExecutorService postWorker=Executors.newSingleThreadExecutor();
    private TransferRuntime.Token postToken;
    private String attemptPage="",recognitionError="";
    private boolean resolvingPost;
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
    private boolean advanced,playing,blob,encrypted,recognitionRequested;
    private String activeFrame="",activeSession="",activeTitle="";
    private boolean handingOff;
    private volatile long webGestureAt=-60000;
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
        fastLoading=getSharedPreferences("browser",0).getBoolean("defer-images-v2",false);
        fastButton=new Button(this);fastButton.setId(1410);pageTools.addView(fastButton,new LinearLayout.LayoutParams(0,dp(48),1));
        fastButton.setOnClickListener(v->{fastLoading=!fastLoading;getSharedPreferences("browser",0).edit().putBoolean("defer-images-v2",fastLoading).apply();updateFastButton();if(!fastLoading)web.getSettings().setBlockNetworkImage(false);toast(fastLoading?"下次打开网页延后图片，节省流量":"已恢复图片即时加载");});updateFastButton();
        Button refresh=new Button(this);refresh.setText("刷新");refresh.setOnClickListener(v->web.reload());pageTools.addView(refresh,new LinearLayout.LayoutParams(dp(80),dp(48)));
        pageProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pageProgress.setMax(100);content.addView(pageProgress,new LinearLayout.LayoutParams(-1,dp(3)));
        web=new WebView(this);content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        Button postFallback=new Button(this);postFallback.setId(1420);postFallback.setText("帖子视频：保存后播放");postFallback.setOnClickListener(v->{String page=web.getUrl();if(MainActivity.isHttps(page))startActivity(new Intent(this,TransferActivity.class).putExtra("postUrl",page));else toast("请先打开 HTTPS 帖子链接");});content.addView(postFallback,new LinearLayout.LayoutParams(-1,dp(48)));
        identifyButton=new Button(this);identifyButton.setId(1402);identifyButton.setText("识别当前视频");content.addView(identifyButton,new LinearLayout.LayoutParams(-1,dp(52)));identifyButton.setOnClickListener(v->identify());
        mediaButton=new Button(this);mediaButton.setId(1401);mediaButton.setSingleLine();mediaButton.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(mediaButton,new LinearLayout.LayoutParams(-1,dp(60)));mediaButton.setOnClickListener(v->chooseMedia());
        note=new TextView(this);note.setTextSize(11);note.setTextColor(Color.rgb(169,183,184));note.setPadding(16,8,16,12);content.addView(note);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);s.setBlockNetworkImage(fastLoading);s.setMediaPlaybackRequiresUserGesture(true);
        floatingPlay=new Button(this);floatingPlay.setText("转到全能播放");floatingPlay.setOnClickListener(v->chooseMedia());floatingPlay.setVisibility(View.GONE);
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
                if(fastLoading)handler.postDelayed(()->releaseImages(epoch),800);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                long age=SystemClock.elapsedRealtime()-webGestureAt;if(age<0||age>15000)return null;
                String u=request.getUrl().toString();if(MainActivity.isHttps(u))requests.put(u,PlaybackHeaders.sanitize(request.getRequestHeaders()));
                if(MediaCandidatePolicy.playable(u)){
                    long when=SystemClock.elapsedRealtime();handler.post(()->rememberMediaRequest(u,when));
                }
                return null;
            }
            @Override public void onPageCommitVisible(WebView view,String url){long epoch=pageEpoch;handler.postDelayed(()->releaseImages(epoch),350);}
            @Override public void onPageFinished(WebView view,String url){address.setText(url);releaseImages(pageEpoch);}
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
                    if("attempt".equals(kind)&&SystemClock.elapsedRealtime()-webGestureAt<15000){
                        String page=data.optString("page","");if(!sameOrigin(page,origin))return;
                        attemptPage=page;recognitionError="";recognitionRequested=true;playing=true;activeFrame=frame;
                        activeTitle=cleanTitle("",frame);updateCount();schedulePostResolve(page,frame,1200);
                    }else if("activate".equals(kind)&&SystemClock.elapsedRealtime()-webGestureAt<15000){
                        media.clear();contexts.clear();activeFrame=frame;activeSession=session;activeTitle=cleanTitle(data.optString("title"),frame);playing=true;blob=false;encrypted=false;recognitionRequested=true;updateCount();
                        handler.postDelayed(()->{if(media.isEmpty())offerRecentMedia();},500);
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
    private void resetDiscovery(){generation.incrementAndGet();if(postToken!=null)postToken.cancel();postToken=null;attemptPage="";recognitionError="";resolvingPost=false;media.clear();contexts.clear();frames.clear();requests.clear();recentMediaRequests.clear();playing=false;blob=false;encrypted=false;recognitionRequested=false;activeFrame="";activeSession="";activeTitle="";handingOff=false;webGestureAt=-60000;updateCount();}
    private void schedulePostResolve(String page,String frame,long delay){
        int current=generation.get();handler.postDelayed(()->{
            if(current!=generation.get()||!privateReady()||encrypted||!media.isEmpty()||resolvingPost||!Objects.equals(page,attemptPage))return;
            resolvingPost=true;recognitionError="";updateCount();TransferRuntime.Token token=new TransferRuntime.Token();postToken=token;
            postWorker.execute(()->{
                String found=null,failure=null;try{found=PostVideoResolver.resolve(this,page,token);}catch(Exception error){failure=error instanceof IOException?error.getMessage():"站点暂时不提供可直播放地址；可使用保存后播放";}
                final String video=found,message=failure;handler.post(()->{
                    if(current!=generation.get()||token!=postToken||isFinishing())return;
                    resolvingPost=false;postToken=null;
                    if(video!=null&&media.isEmpty()&&Objects.equals(activeFrame,frame))collect(video,null,frame,"",true);
                    else if(media.isEmpty())recognitionError=message==null?"此帖子暂时无法解析，请在网页继续观看":message;
                    updateCount();
                });
            });
            handler.postDelayed(()->{if(token==postToken&&resolvingPost){token.cancel();postToken=null;resolvingPost=false;recognitionError="解析超时，请稍后重试或使用保存后播放";updateCount();}},30000);
        },delay);
    }
    private void rememberMediaRequest(String url,long when){
        if(when<webGestureAt||when-webGestureAt>15000||!privateReady())return;
        if(recentMediaRequests.size()>=16)recentMediaRequests.remove(recentMediaRequests.keySet().iterator().next());
        recentMediaRequests.put(url,when);
        if(playing&&media.isEmpty())offerRecentMedia();
    }
    private void offerRecentMedia(){
        if(!playing||encrypted||activeFrame.isEmpty()||!media.isEmpty())return;
        for(Map.Entry<String,Long> entry:new ArrayList<>(recentMediaRequests.entrySet()))
            if(entry.getValue()>=webGestureAt)collect(entry.getKey(),null,activeFrame,"",false);
    }
    private void collect(String url,Map<String,String> requestHeaders,String frame,String observedMime,boolean direct){
        if(!recognitionRequested||!MainActivity.isHttps(url)||url.length()>8192||activeFrame.isEmpty()||!Objects.equals(activeFrame,frame))return;
        if(!MediaCandidatePolicy.exportable(url,direct))return;
        final int seenOn=generation.get();
        HashMap<String,String> captured=PlaybackHeaders.sanitize(requestHeaders==null?requests.get(url):requestHeaders);
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed()||!privateReady()||seenOn!=generation.get())return;
            if(!media.contains(url)&&media.size()<100)media.add(url);
            if(!media.contains(url))return;
            recognitionError="";if(postToken!=null){postToken.cancel();postToken=null;resolvingPost=false;}
            JSONObject old=contexts.get(url);HashMap<String,String> headers=new HashMap<>();
            if(old!=null){JSONObject h=old.optJSONObject("headers");if(h!=null){Iterator<String> keys=h.keys();while(keys.hasNext()){String k=keys.next();headers.put(k,h.optString(k));}}}
            headers.putAll(captured);String page=frame!=null?frame:old!=null?old.optString("page",web.getUrl()):web.getUrl();
            if(!headers.containsKey("Referer")){String ref=PlaybackHeaders.referrer(page,url);if(!ref.isEmpty())headers.put("Referer",ref);}
            headers.put("User-Agent",web.getSettings().getUserAgentString());String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)headers.put("Cookie",cookie);
            try{JSONObject c=new JSONObject();c.put("url",url);c.put("page",MainActivity.isHttps(attemptPage)?attemptPage:web.getUrl());c.put("frame",page);c.put("title",activeTitle);String mime=MediaCandidatePolicy.mime(url,observedMime);if(mime.isEmpty()&&old!=null)mime=old.optString("mime","");c.put("mime",mime);c.put("headers",new JSONObject(PlaybackHeaders.sanitize(headers)));contexts.put(url,c);}catch(JSONException ignored){}
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
        mediaButton.setText(handingOff?"正在打开播放器…":media.isEmpty()?"等待网页视频":"转到全能播放 · "+media.size()+" 条结果");
        if(note!=null)note.setText(encrypted?"此视频使用受保护媒体，请继续在原网页观看。":resolvingPost?"正在解析当前帖子的视频地址…":!recognitionError.isEmpty()?recognitionError:!advanced?"网页组件不支持自动识别；可点“识别当前视频”尝试解析帖子。":media.isEmpty()?(recognitionRequested?"正在查找点播视频；可点“识别当前视频”重试。":"点播网页视频后自动识别；未出现结果可点“识别当前视频”。"):("已识别 "+media.size()+" 条，点“转到全能播放”选择；退出播放器回到原网页。"));
    }
    private void identify(){
        recognitionRequested=true;webGestureAt=SystemClock.elapsedRealtime();
        for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("scan");}catch(RuntimeException ignored){}
        String page=MainActivity.isHttps(attemptPage)?attemptPage:web.getUrl();if(MainActivity.isHttps(page)){attemptPage=page;if(activeFrame.isEmpty())activeFrame=page;if(activeTitle.isEmpty())activeTitle=cleanTitle("",page);schedulePostResolve(page,activeFrame,900);}
        handler.postDelayed(()->{if(!isFinishing()&&privateReady())updateCount();},700);
    }
    private void chooseMedia(){
        if(media.isEmpty()||encrypted)return;
        ArrayList<String> sorted=new ArrayList<>(media);sorted.sort(Comparator.comparingInt(u->MediaCandidatePolicy.priority(u,contexts.containsKey(u)?contexts.get(u).optString("mime",""):"")));
        if(sorted.size()==1){handoff(sorted.get(0));return;}
        String[] labels=new String[sorted.size()];for(int i=0;i<sorted.size();i++){String u=sorted.get(i);JSONObject c=contexts.get(u);labels[i]=(c==null?"视频":c.optString("title","视频"))+" · "+Uri.parse(u).getHost()+" · "+(i+1);}
        new PrivateDialogBuilder(this).setTitle("选择识别结果").setItems(labels,(d,i)->handoff(sorted.get(i))).setNegativeButton("取消",null).show();
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
    private void updateFastButton(){fastButton.setText(fastLoading?"图片延后：开":"图片延后：关");fastButton.setSelected(fastLoading);}
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
    @Override protected void onPrivateLocked(){if(postToken!=null){postToken.cancel();postToken=null;resolvingPost=false;}if(web!=null){web.onPause();web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("pause");}catch(RuntimeException ignored){}}}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(postToken!=null)postToken.cancel();postWorker.shutdownNow();hideCustomView();frames.clear();requests.clear();contexts.clear();if(web!=null)web.destroy();super.onDestroy();}
}
