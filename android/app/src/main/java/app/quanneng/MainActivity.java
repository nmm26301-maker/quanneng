package app.quanneng;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.util.Rational;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** 全能主界面：可信离线前端、文件选择、全屏及 AI 接口桥接。 */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net/assets/";
    private static final int PICK_FILE = 41;
    private static final int PICK_NATIVE_VIDEO = 42;
    private static final int NATIVE_PLAYER = 43;
    private final Map<String,JSONObject> mediaContexts = new LinkedHashMap<>();
    private WebView web;
    private FrameLayout root;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullCallback;
    private ValueCallback<Uri[]> picker;
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, HttpURLConnection> connections = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private String pendingUrl;
    private boolean pipRequested = false;
    private boolean foreground = false;
    private long mediaRequest = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 12, 16));
        setContentView(root);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        configureWeb();
        receiveIntent(getIntent());
        web.loadUrl(ORIGIN + "index.html");
    }
    private void configureWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "Quanneng");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"appassets.androidplatform.net".equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || !path.startsWith("/assets/") || path.contains("..")) return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                try {
                    String name = path.substring(8);
                    String type = name.endsWith(".js") ? "text/javascript" : name.endsWith(".css") ? "text/css" : name.endsWith(".svg") ? "image/svg+xml" : "text/html";
                    Map<String,String> headers = new HashMap<>();
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(type, "UTF-8", 200, "OK", headers, getAssets().open(name));
                } catch (IOException error) { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0])); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(ORIGIN)) return false;
                if (isHttps(url)) openBrowser(url);
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (url.startsWith(ORIGIN) && pendingUrl != null) {
                    String shared = pendingUrl; pendingUrl = null;
                    web.evaluateJavascript("window.quannengOpenURL&&window.quannengOpenURL(" + JSONObject.quote(shared) + ")", null);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (picker != null) picker.onReceiveValue(null);
                picker = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, PICK_FILE);
                } catch (ActivityNotFoundException error) {
                    picker.onReceiveValue(null); picker = null;
                    toast("系统未找到文件选择器，请安装文件管理器");
                }
                return true;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return; }
                fullscreen = view; fullCallback = callback;
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1,-1));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView() { hideFullscreen(); }
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("确定",(d,w)->result.confirm()).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show(); return true;
            }
        });
    }
    private void hideFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen); fullscreen = null;
        web.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (fullCallback != null) { fullCallback.onCustomViewHidden(); fullCallback = null; }
    }
    private boolean trusted() { return web != null && web.getUrl() != null && web.getUrl().startsWith(ORIGIN); }
    static boolean isHttps(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
    }
    private void openBrowser(String url) {
        if (!isHttps(url)) { toast("请使用 HTTPS 网页地址"); return; }
        startActivity(new Intent(this, BrowserActivity.class).putExtra("url", url));
    }
    private void toast(String text) { runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show()); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request == PICK_NATIVE_VIDEO && result == RESULT_OK && data != null) {
            ArrayList<String> queue=new ArrayList<>();
            if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)queue.add(data.getClipData().getItemAt(i).getUri().toString());}
            else if(data.getData()!=null)queue.add(data.getData().toString());
            if(!queue.isEmpty())startActivityForResult(new Intent(this,SystemPlayerActivity.class).setData(Uri.parse(queue.get(0))).putStringArrayListExtra("queue",queue).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),NATIVE_PLAYER);
        }
        if (request == NATIVE_PLAYER && data != null && trusted()) {
            try { JSONObject resultData=new JSONObject();resultData.put("url",data.getStringExtra("url"));resultData.put("position",data.getIntExtra("position",0)/1000.0);
                web.evaluateJavascript("window.dispatchEvent(new CustomEvent('quanneng-playback',{detail:"+resultData.toString()+"}))",null);
            } catch(JSONException ignored) {}
        }
        if (request == PICK_FILE && picker != null) {
            picker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data)); picker = null;
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); receiveIntent(intent);
        if (pendingUrl != null && trusted()) {
            String value = pendingUrl; pendingUrl = null;
            web.evaluateJavascript("window.quannengOpenURL&&window.quannengOpenURL("+JSONObject.quote(value)+")",null);
        }
    }
    private void receiveIntent(Intent intent) {
        if (intent == null) return;
        String mediaContext=intent.getStringExtra("mediaContext");
        if(mediaContext!=null&&mediaContext.length()<65536){try{JSONObject c=new JSONObject(mediaContext);String u=c.getString("url");if(isHttps(u)){mediaContexts.put(u,c);if(mediaContexts.size()>32)mediaContexts.remove(mediaContexts.keySet().iterator().next());}}catch(JSONException ignored){}}
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https://[^\\s<>\\\"]+").matcher(text);
                if (m.find()) pendingUrl = m.group();
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri uri = intent.getData();
            if ("content".equals(uri.getScheme())) startActivityForResult(new Intent(this,SystemPlayerActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),NATIVE_PLAYER);
        }
    }
    @Override public void onBackPressed() {
        if (fullscreen != null) { hideFullscreen(); return; }
        web.evaluateJavascript("window.quannengBack?window.quannengBack():false", result->{if (!"true".equals(result)) MainActivity.super.onBackPressed();});
    }
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause() {
        foreground=false;
        if (!pipRequested && !isInPictureInPictureMode()) web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);
        super.onPause();
    }
    @Override public void onPictureInPictureModeChanged(boolean active, android.content.res.Configuration config) {
        super.onPictureInPictureModeChanged(active, config);
        if (!active) { pipRequested = false; web.evaluateJavascript("document.documentElement.classList.remove('pip-mode')", null); }
    }
    @Override protected void onDestroy() {
        if (picker != null) picker.onReceiveValue(null);
        for (HttpURLConnection connection:connections.values()) connection.disconnect();
        worker.shutdownNow();
        web.removeJavascriptInterface("Quanneng"); web.destroy(); super.onDestroy();
    }
    public final class Bridge {
        @JavascriptInterface public void openWebsite(String url) { runOnUiThread(()->{if(trusted())openBrowser(url);}); }
        @JavascriptInterface public void enterPip() { runOnUiThread(()->{if(!trusted())return;try{pipRequested=true;web.evaluateJavascript("document.documentElement.classList.add('pip-mode')",null);if(!enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build()))throw new IllegalStateException();}catch(RuntimeException e){pipRequested=false;web.evaluateJavascript("document.documentElement.classList.remove('pip-mode')",null);toast("此设备不支持画中画");}}); }
        @JavascriptInterface public void systemPlay(String url) { runOnUiThread(()->{if(trusted()&&isHttps(url)){try{JSONObject request=new JSONObject();request.put("url",url);launchMedia(request);}catch(JSONException ignored){}}}); }
        @JavascriptInterface public void playMedia(String raw) {runOnUiThread(()->{if(!trusted()||raw==null||raw.length()>100000)return;try{launchMedia(new JSONObject(raw));}catch(Exception error){toast("视频地址无效，请重新打开原网页");}});}
        @JavascriptInterface public void openLocalVideo() {runOnUiThread(()->{if(!trusted())return;try{Intent select=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(select,PICK_NATIVE_VIDEO);}catch(ActivityNotFoundException error){toast("系统未找到文件选择器");}});}

        @JavascriptInterface public void requestAI(String value) {
            runOnUiThread(()->{if(trusted()&&value!=null&&value.length()<300000)worker.execute(()->callAI(value));});
        }
        @JavascriptInterface public void cancelAI(String id) {
            if (id == null) return;
            cancelled.add(id); HttpURLConnection c=connections.remove(id); if(c!=null)c.disconnect();
        }
    }
    private void launchMedia(JSONObject request) throws JSONException {
        String url=request.getString("url");if(!isHttps(url)){toast("请使用 HTTPS 视频地址");return;}
        JSONObject context=mediaContexts.get(url);
        Intent intent=new Intent(this,SystemPlayerActivity.class).setData(Uri.parse(url));
        if(context!=null){intent.putExtra("headers",context.optJSONObject("headers")==null?"{}":context.optJSONObject("headers").toString());intent.putExtra("sourcePage",context.optString("page",""));}
        String cookie=android.webkit.CookieManager.getInstance().getCookie(url);
        if(cookie!=null)intent.putExtra("cookie",cookie);
        intent.putExtra("userAgent",web.getSettings().getUserAgentString());
        intent.putExtra("position",Math.max(0,request.optInt("position",0)));
        intent.putExtra("autoNext",request.optBoolean("autoNext",true));
        JSONArray values=request.optJSONArray("queue");ArrayList<String> queue=new ArrayList<>();
        if(values!=null){for(int i=0;i<Math.min(100,values.length());i++){String u=values.optString(i,"");if(isHttps(u))queue.add(u);}}
        intent.putStringArrayListExtra("queue",queue);
        boolean known=context!=null||java.util.regex.Pattern.compile("(?i)\\.(mp4|m3u8|webm|m4v|mov)(?:[?#]|$)").matcher(url).find();
        if(known){startActivityForResult(intent,NATIVE_PLAYER);return;}
        final long token=++mediaRequest;
        final String requestAgent=web.getSettings().getUserAgentString();
        worker.execute(()->{
            boolean html=false;HttpURLConnection probe=null;
            try{probe=(HttpURLConnection)new URL(url).openConnection();probe.setInstanceFollowRedirects(false);probe.setConnectTimeout(8000);probe.setReadTimeout(8000);probe.setRequestProperty("Range","bytes=0-511");probe.setRequestProperty("User-Agent",requestAgent);if(cookie!=null&&cookie.indexOf('\r')<0&&cookie.indexOf('\n')<0)probe.setRequestProperty("Cookie",cookie);
                int status=probe.getResponseCode();String type=probe.getContentType();html=status>=200&&status<300&&type!=null&&(type.toLowerCase(Locale.ROOT).contains("text/html")||type.toLowerCase(Locale.ROOT).contains("application/xhtml"));
            }catch(Exception ignored){}finally{if(probe!=null)probe.disconnect();}
            final boolean webpage=html;
            runOnUiThread(()->{if(isFinishing()||!foreground||token!=mediaRequest)return;if(webpage){toast("这是网页地址，请在网页内开始播放后点击发现视频");openBrowser(url);}else startActivityForResult(intent,NATIVE_PLAYER);});
        });
    }
    private void callAI(String raw) {
        String id = ""; HttpURLConnection connection=null;
        try {
            JSONObject request = new JSONObject(raw); id = request.getString("id");
            String endpoint = request.getString("endpoint");
            if (!isHttps(endpoint)) throw new IOException("接口必须使用 HTTPS");
            if (cancelled.contains(id)) return;
            String address = endpoint.replaceAll("/$", "") + "/chat/completions";
            connection=(HttpURLConnection)new URL(address).openConnection();
            connections.put(id,connection);
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(20000); connection.setReadTimeout(60000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty("Authorization","Bearer "+request.getString("key"));
            JSONObject body=new JSONObject(); body.put("model",request.getString("model"));body.put("messages",request.getJSONArray("messages"));body.put("stream",false);
            try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=connection.getResponseCode();
            if(code<200||code>=300)throw new IOException("接口请求失败（"+code+"），请检查地址、模型、密钥与额度");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>2*1024*1024)throw new IOException("返回内容过大");bytes.write(buffer,0,count);}
            }
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            String text=response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            deliverAI(id,text,null);
        } catch(Exception error) { deliverAI(id,null,error instanceof SocketTimeoutException?"请求超时，请重试":error.getMessage()==null?"请求失败，请检查接口配置":error.getMessage()); }
        finally {if(connection!=null)connection.disconnect();connections.remove(id);cancelled.remove(id);}
    }
    private void deliverAI(String id,String text,String error) {
        if(cancelled.contains(id)||id.isEmpty())return;
        try {
            JSONObject result=new JSONObject();result.put("id",id);
            if(error!=null)result.put("error",error);else result.put("content",text);
            String js="window.dispatchEvent(new CustomEvent('quanneng-ai',{detail:"+result.toString()+"}))";
            runOnUiThread(()->{if(!isFinishing()&&trusted())web.evaluateJavascript(js,null);});
        } catch(JSONException ignored) { toast("AI 返回内容解析失败"); }
    }
}
