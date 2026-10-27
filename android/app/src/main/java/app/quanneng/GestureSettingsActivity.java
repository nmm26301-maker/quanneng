package app.quanneng;
import android.os.Bundle;
import android.widget.*;
/** 手势有按钮替代，可随时关闭，并尊重系统触摸探索。 */
public final class GestureSettingsActivity extends FeatureActivity {
 @Override public void onCreate(Bundle b){super.onCreate(b);page("智能手势");Switch toggle=new Switch(this);toggle.setText("启用播放器手势");toggle.setTextColor(PlayerUi.TEXT);toggle.setMinHeight(dp(52));toggle.setChecked(getSharedPreferences("player-gestures",0).getBoolean("enabled",true));body.addView(toggle);toggle.setOnCheckedChangeListener((v,on)->getSharedPreferences("player-gestures",0).edit().putBoolean("enabled",on).apply());text(body,"双击左 / 右侧：后退 / 前进 10 秒\n双击中央：播放 / 暂停\n左右拖动画面：按秒预览，松手定位\n进度条：单手左右拖动按秒调整，上移慢调；点按快速跳转\n左侧上下拖动：屏幕亮度\n右侧上下拖动：媒体音量\n长按画面：临时 2 倍速，松手恢复\n\n时间、音量、亮度与速度也可通过按钮或系统控制调整。系统开启触摸探索时自动禁用手势。",16);}
}
