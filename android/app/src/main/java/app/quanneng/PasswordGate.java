package app.quanneng;

import android.app.*;
import android.os.*;
import android.text.InputType;
import android.text.method.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** 原生密码表单；首次创建必须二次确认，修改密码必须验证旧密码，没有跳过按钮。 */
final class PasswordGate extends Dialog {
    private static final ExecutorService CRYPTO=Executors.newSingleThreadExecutor();
    private final PrivateActivity owner;
    private final boolean change,setup,systemMode,managing;
    private final Runnable accepted;
    private EditText password,confirmation,previous;
    private TextView error;
    private Button submit;
    private boolean busy;
    PasswordGate(PrivateActivity owner,boolean change,Runnable accepted){this(owner,change,accepted,true);}
    PasswordGate(PrivateActivity owner,boolean change,Runnable accepted,boolean preferSystem){super(owner);this.owner=owner;this.change=change;this.setup=!PasswordStore.configured(owner);this.managing=change||PrivateSession.isUnlocked();this.systemMode=preferSystem&&!managing&&DeviceUnlock.available(owner);this.accepted=accepted;setCancelable(false);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ScrollView scroll=new ScrollView(owner);scroll.setFillViewport(true);scroll.setBackgroundColor(PlayerUi.BG);setContentView(scroll);
        LinearLayout box=new LinearLayout(owner);box.setOrientation(1);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(28),dp(32),dp(28),dp(32));scroll.addView(box,new ScrollView.LayoutParams(-1,-1));
        ImageView logo=new ImageView(owner);logo.setImageResource(app.quanneng.R.drawable.quanneng_icon);logo.setContentDescription("全能");LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(88),dp(88));icon.bottomMargin=dp(24);box.addView(logo,icon);
        if(systemMode){
            box.addView(PlayerUi.text(owner,"欢迎回来",28,PlayerUi.TEXT));TextView hint=PlayerUi.text(owner,"优先使用系统指纹或锁屏密码，无需另设全能密码。解锁后，页面切换和短暂返回不重复验证。",14,PlayerUi.MUTED);hint.setPadding(0,dp(16),0,dp(24));box.addView(hint);
            error=PlayerUi.text(owner,"",14,PlayerUi.MUTED);error.setId(1504);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);box.addView(error);
            Button device=PlayerUi.chip(owner,"使用指纹 / 锁屏密码解锁");device.setId(1506);box.addView(device,new LinearLayout.LayoutParams(-1,dp(56)));device.setOnClickListener(v->owner.beginSystemUnlock());
            if(PasswordStore.configured(owner)){Button fallback=PlayerUi.chip(owner,"使用已有私人密码");fallback.setId(1507);box.addView(fallback,new LinearLayout.LayoutParams(-1,dp(52)));fallback.setOnClickListener(v->owner.usePasswordFallback());}
            TextView note=PlayerUi.text(owner,"验证由 Android 系统完成。锁屏、手动锁定或离开超过 5 分钟后再次验证。",12,PlayerUi.MUTED);note.setPadding(0,dp(24),0,0);box.addView(note);return;
        }
        TextView title=PlayerUi.text(owner,change?"修改私人密码":setup?"设置私人密码":"欢迎回来",28,PlayerUi.TEXT);box.addView(title);
        TextView hint=PlayerUi.text(owner,change?"先验证旧密码，再设置新密码。":setup?"手机尚未设置系统锁屏密码。可设置 8–64 位备用密码。":"输入备用密码。解锁后页面切换和短暂返回不重复验证。",14,PlayerUi.MUTED);hint.setPadding(0,dp(10),0,dp(24));box.addView(hint);
        if(change)previous=field(box,"当前密码",1503);
        password=field(box,setup||change?"新密码":"密码",1500);if(setup||change)confirmation=field(box,"再次输入新密码",1501);
        CheckBox show=new CheckBox(owner);show.setText("显示密码");show.setTextColor(PlayerUi.MUTED);show.setMinHeight(dp(48));box.addView(show);show.setOnCheckedChangeListener((button,checked)->{for(EditText input:new EditText[]{previous,password,confirmation})if(input!=null){int position=input.getSelectionStart();input.setTransformationMethod(checked?HideReturnsTransformationMethod.getInstance():PasswordTransformationMethod.getInstance());input.setSelection(Math.max(0,position));}});
        error=PlayerUi.text(owner,"",13,0xffff9ba7);error.setId(1504);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);error.setPadding(0,dp(8),0,dp(12));box.addView(error);
        submit=PlayerUi.chip(owner,change?"验证并修改":setup?"创建密码并进入":"解锁全能");submit.setId(1502);submit.setTextColor(PlayerUi.BG);submit.setBackground(PlayerUi.feedback(owner,PlayerUi.ACCENT,12));box.addView(submit,new LinearLayout.LayoutParams(-1,dp(52)));submit.setOnClickListener(v->verify());
        TextView note=PlayerUi.text(owner,"密码仅保存在本机的安全验证配置中。请妥善记住，未提供免密重置入口。",12,PlayerUi.MUTED);note.setPadding(0,dp(22),0,0);box.addView(note);
        if(managing){Button cancel=PlayerUi.chip(owner,"取消");box.addView(cancel,new LinearLayout.LayoutParams(-1,dp(48)));cancel.setOnClickListener(v->dismiss());}
        password.setOnEditorActionListener((view,action,event)->{if(confirmation==null){verify();return true;}confirmation.requestFocus();return false;});
    }
    private EditText field(LinearLayout parent,String label,int id){TextView caption=PlayerUi.text(owner,label,13,PlayerUi.MUTED);caption.setPadding(0,dp(8),0,dp(6));parent.addView(caption);EditText input=new EditText(owner);input.setId(id);input.setContentDescription(label);input.setSingleLine();input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setTextColor(PlayerUi.TEXT);input.setPadding(dp(14),0,dp(14),0);input.setBackground(PlayerUi.surface(owner,PlayerUi.SURFACE,12));parent.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));return input;}
    private void verify(){
        if(busy)return;long blocked=PasswordStore.blockedMillis(owner);if(blocked>0){error.setText("尝试过于频繁，请在 "+((blocked+999)/1000)+" 秒后重试");return;}
        char[] value=password.getText().toString().toCharArray(),old=previous==null?new char[0]:previous.getText().toString().toCharArray();
        if((setup||change)&&(value.length<8||value.length>64)){error.setText("新密码需为 8–64 位");Arrays.fill(value,'\0');Arrays.fill(old,'\0');return;}
        if(confirmation!=null&&!password.getText().toString().equals(confirmation.getText().toString())){error.setText("两次输入的新密码不一致");Arrays.fill(value,'\0');Arrays.fill(old,'\0');return;}
        busy=true;submit.setEnabled(false);submit.setText("正在验证…");error.setText("");long epoch=PrivateSession.generation();
        CRYPTO.execute(()->{
            boolean good=false;String reason="密码不正确，请重试";
            try{if(change)good=PasswordStore.change(owner,old,value);else if(setup){PasswordStore.create(owner,value);good=true;}else good=PasswordStore.verify(owner,value);}catch(Exception failure){reason="安全存储暂不可用，无法完成验证。请稍后重试。";}finally{Arrays.fill(value,'\0');Arrays.fill(old,'\0');}
            final boolean success=good;final String message=reason;
            owner.runOnUiThread(()->{busy=false;if(!isShowing())return;submit.setEnabled(true);submit.setText(change?"验证并修改":setup?"创建密码并进入":"解锁全能");
                if(!owner.canAcceptPassword(epoch)){error.setText("应用已离开前台，请重新验证");return;}
                if(success&&(change||PrivateSession.unlock(epoch))){password.setText("");if(previous!=null)previous.setText("");if(confirmation!=null)confirmation.setText("");dismiss();accepted.run();if(change)Toast.makeText(owner,"私人密码已更新",Toast.LENGTH_SHORT).show();}
                else{error.setText(message);password.setText("");}
            });
        });
    }
    @Override public void onBackPressed(){if(managing)dismiss();else owner.finish();}
    @Override public void dismiss(){if(password!=null)password.setText("");if(previous!=null)previous.setText("");if(confirmation!=null)confirmation.setText("");super.dismiss();}
    void systemMessage(String value){if(error!=null)error.setText(value);}
    @Override public void show(){super.show();getWindow().setLayout(-1,-1);if(systemMode)getWindow().getDecorView().post(()->{if(isShowing())owner.beginSystemUnlock();});}
    private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
}
