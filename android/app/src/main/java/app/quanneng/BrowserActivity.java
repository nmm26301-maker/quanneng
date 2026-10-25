package app.quanneng;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.regex.Pattern;

/** 独立网页浏览器：只发现公开加载的媒体地址，不向网页暴露应用桥接。 */
public final class BrowserActivity extends Activity {
    private WebView web;
    private Button mediaButton;
    private final List<String> media = new ArrayList<>();
    private static final Pattern VIDEO=Pattern.compile("(?i)\\.(m3u8|mp4|webm|m4v|mov)(?:[?#]|$)");
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(8,12,16));setContentView(root);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setPadding(8,4,8,4);root.addView(toolbar);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());toolbar.addView(back);
        EditText address=new EditText(this);address.setSingleLine();address.setTextSize(13);address.setText(getIntent().getStringExtra("url"));toolbar.addView(address,new LinearLayout.LayoutParams(0,dp(56),1));
        Button go=new Button(this);go.setText("打开");go.setOnClickListener(v->{String u=address.getText().toString().trim();if(MainActivity.isHttps(u)){media.clear();updateCount();web.loadUrl(u);}else toast("请填写完整的 HTTPS 网页地址");});toolbar.addView(go);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        mediaButton=new Button(this);mediaButton.setText("发现视频（0） · 点击重新检测");root.addView(mediaButton,new LinearLayout.LayoutParams(-1,dp(60)));mediaButton.setOnClickListener(v->scan(true));
        TextView note=new TextView(this);note.setText("仅发现网页公开加载的视频。登录、加密与防盗链内容可能无法播放。");note.setTextSize(11);note.setPadding(16,8,16,12);root.addView(note);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(MainActivity.isHttps(request.getUrl().toString()))return false;toast("不支持此链接类型");return true;}
            @Override public void onLoadResource(WebView view,String url){collect(url);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){collect(request.getUrl().toString());return null;}
            @Override public void onPageFinished(WebView view,String url){address.setText(url);scan(false);}
        });
        String url=getIntent().getStringExtra("url");if(MainActivity.isHttps(url))web.loadUrl(url);else finish();
    }
    private void collect(String url){if(!MainActivity.isHttps(url)||!VIDEO.matcher(url).find())return;runOnUiThread(()->{if(!media.contains(url)&&media.size()<100){media.add(url);updateCount();}});}
    private void updateCount(){if(mediaButton!=null)mediaButton.setText("发现视频（"+media.size()+"） · 选择播放");}
    private void scan(boolean show){
        web.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('video,video source')).map(v=>v.currentSrc||v.src).filter(s=>s&&s.startsWith('https://')))",value->{
            try{Object decoded=new JSONTokener(value).nextValue();if(decoded instanceof String){JSONArray urls=new JSONArray((String)decoded);for(int i=0;i<urls.length();i++){String u=urls.getString(i);if(MainActivity.isHttps(u)&&!media.contains(u))media.add(u);}updateCount();}}catch(Exception ignored){}
            if(show)showChoices();
        });
    }
    private void showChoices(){
        if(media.isEmpty()){toast("还未发现可用的视频地址。请先在网页中点击播放，再重新检测");return;}
        String[] names=new String[media.size()];for(int i=0;i<names.length;i++){String u=media.get(i);names[i]=(i+1)+" · "+(u.contains(".m3u8")?"M3U8":"视频")+"\n"+(u.length()>150?u.substring(0,150)+"…":u);}
        new AlertDialog.Builder(this).setTitle("在全能中播放").setItems(names,(dialog,index)->{
            String value=media.get(index);web.loadUrl("about:blank");startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
        }).setNegativeButton("取消",null).show();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String text){runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
