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
public final class BrowserActivity extends Activity {
    private WebView web;
    private Button mediaButton;
    private TextView note;
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
    private boolean advanced,playing,blob,encrypted;
    private int scanId;
    private final AtomicInteger generation=new AtomicInteger();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,12,16));setContentView(root);
        content=new LinearLayout(this);content.setOrientation(1);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setPadding(8,4,8,4);content.addView(toolbar);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());toolbar.addView(back);
        EditText address=new EditText(this);address.setSingleLine();address.setTextSize(13);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setText(getIntent().getStringExtra("url"));toolbar.addView(address,new LinearLayout.LayoutParams(0,dp(56),1));
        Button go=new Button(this);go.setText("打开");go.setOnClickListener(v->{String u=address.getText().toString().trim();if(MainActivity.isHttps(u))web.loadUrl(u);else toast("请填写完整的 HTTPS 网页地址");});toolbar.addView(go);
        web=new WebView(this);content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        mediaButton=new Button(this);mediaButton.setId(1401);content.addView(mediaButton,new LinearLayout.LayoutParams(-1,dp(60)));mediaButton.setOnClickListener(v->scan(true));
        note=new TextView(this);note.setTextSize(11);note.setTextColor(Color.rgb(169,183,184));note.setPadding(16,8,16,12);content.addView(note);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        configureDiscovery();updateCount();
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(customView!=null){callback.onCustomViewHidden();return;}
                customView=view;customCallback=callback;content.setVisibility(View.GONE);root.addView(view,new FrameLayout.LayoutParams(-1,-1));getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
            @Override public void onHideCustomView(){hideCustomView();}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(MainActivity.isHttps(request.getUrl().toString()))return false;toast("不支持此链接类型");return true;}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){resetDiscovery();address.setText(url);}
            @Override public void onLoadResource(WebView view,String url){if(MediaCandidatePolicy.playable(url))collect(url,null,null);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String u=request.getUrl().toString();if(MainActivity.isHttps(u))requests.put(u,PlaybackHeaders.sanitize(request.getRequestHeaders()));
                if(MediaCandidatePolicy.playable(u))collect(u,request.getRequestHeaders(),null);return null;
            }
            @Override public void onPageFinished(WebView view,String url){address.setText(url);scan(false);}
        });
        String url=getIntent().getStringExtra("url");if(MainActivity.isHttps(url))web.loadUrl(url);else finish();
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
                    if("source".equals(data.optString("kind")))collect(data.optString("url",""),null,frame,data.optString("mime",""));
                    else if("status".equals(data.optString("kind"))){playing|=data.optBoolean("playing");blob|=data.optBoolean("blob");encrypted|=data.optBoolean("encrypted");updateCount();}
                }catch(JSONException ignored){}
            });
            WebViewCompat.addDocumentStartJavaScript(web,script,origins);
        }catch(IOException|RuntimeException error){advanced=false;}
    }
    private static boolean sameOrigin(String frame,Uri origin){
        if(!MainActivity.isHttps(frame))return false;Uri uri=Uri.parse(frame);
        return Objects.equals(uri.getHost(),origin.getHost())&&(uri.getPort()==-1?443:uri.getPort())==(origin.getPort()==-1?443:origin.getPort());
    }
    private void resetDiscovery(){generation.incrementAndGet();media.clear();contexts.clear();frames.clear();requests.clear();playing=false;blob=false;encrypted=false;scanId++;updateCount();}
    private void collect(String url,Map<String,String> requestHeaders,String frame){collect(url,requestHeaders,frame,"");}
    private void collect(String url,Map<String,String> requestHeaders,String frame,String observedMime){
        if(!MainActivity.isHttps(url)||url.length()>8192)return;
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
            try{JSONObject c=new JSONObject();c.put("url",url);c.put("page",page);String mime=MediaCandidatePolicy.mime(url,observedMime);if(mime.isEmpty()&&old!=null)mime=old.optString("mime","");c.put("mime",mime);c.put("headers",new JSONObject(PlaybackHeaders.sanitize(headers)));contexts.put(url,c);}catch(JSONException ignored){}
            updateCount();
        });
    }
    private void updateCount(){
        if(mediaButton==null)return;
        mediaButton.setText(media.isEmpty()?(playing?"网页正在播放 · 检查可转播地址":"发现视频（0） · 点击检测"):"发现视频（"+media.size()+"） · 转到全能播放");
        if(note!=null)note.setText(!advanced?"基础检测模式：更新 Android 系统 WebView 可增强嵌入视频识别。":encrypted?"页面使用受保护媒体；可留在原网页观看，独立转播可能不可用。":media.isEmpty()&&playing?"已确认网页播放。尚未取得独立地址；部分媒体只能在原网页播放。":"播放后会自动发现地址，支持嵌入页面与动态媒体请求。点击上方按钮选择转播。");
    }
    private void scan(boolean show){
        for(JavaScriptReplyProxy reply:new ArrayList<>(frames.values()))try{reply.postMessage("scan");}catch(RuntimeException ignored){}
        // Older WebViews retain direct-source and same-origin iframe detection.
        web.evaluateJavascript("JSON.stringify((function walk(w){let a=[];try{w.document.querySelectorAll('video,video source').forEach(v=>{let u=v.currentSrc||v.src;if(u&&u.startsWith('https://'))a.push(u)});for(let i=0;i<w.frames.length;i++)a=a.concat(walk(w.frames[i]));}catch(e){}return a;})(window))",value->{
            try{Object decoded=new JSONTokener(value).nextValue();if(decoded instanceof String){JSONArray urls=new JSONArray((String)decoded);for(int i=0;i<Math.min(100,urls.length());i++)collect(urls.getString(i),null,null);}}catch(Exception ignored){}
        });
        if(show){int id=++scanId;handler.postDelayed(()->{if(id==scanId&&!isFinishing())showChoices();},600);}
    }
    private void showChoices(){
        if(media.isEmpty()){
            String message=encrypted?"网页正在使用受保护媒体，无法作为普通视频地址转播。可继续在网页观看。":playing||blob?"已确认网页视频加载，但暂未取得可独立播放的地址。可以继续在网页观看；这类媒体不保证能转到原生播放器。":"尚未发现视频请求。请先点击网页中的播放按钮，再检测。";
            new AlertDialog.Builder(this).setTitle("暂未取得转播地址").setMessage(message).setPositiveButton("继续网页播放",null).show();return;
        }
        final ArrayList<String> choices=new ArrayList<>(media);choices.sort(Comparator.comparingInt(u->MediaCandidatePolicy.priority(u,contexts.containsKey(u)?contexts.get(u).optString("mime",""):"")));
        String[] names=new String[choices.size()];for(int i=0;i<names.length;i++){String u=choices.get(i);names[i]=(i+1)+" · "+(MediaCandidatePolicy.mime(u,contexts.containsKey(u)?contexts.get(u).optString("mime",""):"").contains("mpegurl")?"HLS":MediaCandidatePolicy.role(u).equals("dash")?"DASH":"视频")+"\n"+(u.length()>130?u.substring(0,130)+"…":u);}
        new AlertDialog.Builder(this).setTitle("在全能中播放").setItems(names,(dialog,index)->{
            String value=choices.get(index);JSONObject context=contexts.get(value);
            Intent intent=new Intent(this,MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if(context!=null)intent.putExtra("mediaContext",context.toString());
            web.loadUrl("about:blank");startActivity(intent);finish();
        }).setNegativeButton("取消",null).show();
    }
    private void hideCustomView(){if(customView==null)return;root.removeView(customView);customView=null;content.setVisibility(View.VISIBLE);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String text){runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(customView!=null)hideCustomView();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);hideCustomView();frames.clear();requests.clear();contexts.clear();if(web!=null)web.destroy();super.onDestroy();}
}
