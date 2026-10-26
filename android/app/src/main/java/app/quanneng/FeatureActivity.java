package app.quanneng;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
/** 功能页共用布局、异步反馈及私人会话保护。 */
public abstract class FeatureActivity extends PrivateActivity {
 protected LinearLayout body;protected TextView message;protected final ExecutorService io=Executors.newSingleThreadExecutor();
 protected void page(String title){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(PlayerUi.BG);setContentView(scroll);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(16),dp(20),dp(32));scroll.addView(body);LinearLayout bar=new LinearLayout(this);body.addView(bar);Button back=PlayerUi.chip(this,"返回");bar.addView(back);back.setOnClickListener(v->finish());TextView h=PlayerUi.text(this,title,22,PlayerUi.TEXT);h.setPadding(dp(12),0,0,0);h.setGravity(Gravity.CENTER_VERTICAL);bar.addView(h,new LinearLayout.LayoutParams(0,dp(52),1));message=text(body,"",14);message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.setOnApplyWindowInsetsListener((v,i)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(20)+b.left,dp(16)+b.top,dp(20)+b.right,dp(32)+b.bottom);}return i;});}
 protected int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 protected TextView text(LinearLayout b,String s,int size){TextView t=PlayerUi.text(this,s,size,PlayerUi.TEXT);t.setPadding(0,dp(8),0,dp(8));b.addView(t);return t;}
 protected EditText input(String label,String hint){TextView c=text(body,label,14);EditText e=new EditText(this);e.setId(View.generateViewId());c.setLabelFor(e.getId());e.setTextColor(PlayerUi.TEXT);e.setHintTextColor(PlayerUi.MUTED);e.setHint(hint);e.setSingleLine();body.addView(e,new LinearLayout.LayoutParams(-1,dp(56)));return e;}
 protected Button button(LinearLayout box,String s,Runnable action){Button b=PlayerUi.chip(this,s);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);b.setMinHeight(dp(52));box.addView(b,p);b.setOnClickListener(v->{if(privateReady())action.run();});return b;}
 protected LinearLayout section(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(16),dp(8),dp(16),dp(16));c.setBackground(PlayerUi.surface(this,PlayerUi.SURFACE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);body.addView(c,p);return c;}
 protected interface Work<T>{T run() throws Exception;}
 protected <T> void work(String busy,Work<T> task,java.util.function.Consumer<T> done){message.setText(busy);io.execute(()->{try{T r=task.run();runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())whenUnlocked(()->{message.setText("");done.accept(r);});});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())whenUnlocked(()->{enableButtons(body);message.setText("操作失败："+(e.getMessage()==null?"请检查权限、网络或可用空间":e.getMessage()));});});}});}
 private void enableButtons(android.view.ViewGroup box){for(int i=0;i<box.getChildCount();i++){View child=box.getChildAt(i);if(child instanceof Button)child.setEnabled(true);if(child instanceof android.view.ViewGroup)enableButtons((android.view.ViewGroup)child);}}
 protected void play(android.net.Uri uri){startActivity(new Intent(this,SystemPlayerActivity.class).setData(uri));}
 @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
