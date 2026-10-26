package app.quanneng;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.util.function.Consumer;

/** 投屏与接力共用显式共享流程；离开页面后保留通知中的停止入口。 */
final class LocalSharePanel {
    private static final int PICK=96;
    private final FeatureActivity activity;
    private final Consumer<String> ready;
    private final TextView status;
    private String delivered="";
    private boolean waiting;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable update=new Runnable(){public void run(){if(activity.privateReady()){
        if(LanShareService.expires>0&&SystemClock.elapsedRealtime()>=LanShareService.expires)LanShareService.end(activity);status.setText(LanShareService.phase);
        String value=LanShareService.url;if(waiting&&!value.isEmpty()&&!value.equals(delivered)){delivered=value;waiting=false;ready.accept(value);}
    }handler.postDelayed(this,500);}};
    LocalSharePanel(FeatureActivity a,String current,Consumer<String> callback){
        activity=a;ready=callback;LinearLayout box=a.section();a.text(box,"本地视频共享",18);a.text(box,"准备完成前请保持全能在前台。同一 Wi-Fi 内，持本次链接者可播放。开启后可后台继续，30 分钟自动关闭；立即锁定或停止共享会断开。只共享选定普通视频，保险箱视频不共享。",14);
        if(current!=null&&LanShareService.allowed(Uri.parse(current)))a.button(box,"共享正在播放的视频",()->confirm(Uri.parse(current)));
        a.button(box,"选择本地视频并共享",()->{try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),PICK);}catch(ActivityNotFoundException e){a.message.setText("系统没有文件选择器");}});
        a.button(box,"使用已开启的本地共享",()->{if(LanShareService.url.isEmpty())a.message.setText("请先选择视频并开启共享");else{delivered=LanShareService.url;ready.accept(delivered);}});
        a.button(box,"立即停止本地共享",()->{waiting=false;LanShareService.end(a);delivered="";});status=a.text(box,LanShareService.phase,14);handler.post(update);
    }
    private void confirm(Uri source){if(!LanShareService.allowed(source)){activity.message.setText("保险箱视频不能开启共享");return;}new PrivateDialogBuilder(activity).setTitle("开启本次局域网共享？").setMessage("只临时复制这一个视频，链接 30 分钟内有效，后台继续共享。可以在本页或系统任务通知中停止。").setNegativeButton("取消",null).setPositiveButton("开启",(d,w)->{if(!activity.privateReady())return;delivered="";waiting=LanShareService.request(activity,source);if(!waiting)activity.message.setText(LanShareService.phase);}).show();}
    boolean result(int request,int result,Intent data){if(request!=PICK)return false;if(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{activity.getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}confirm(u);}return true;}
    void close(){handler.removeCallbacksAndMessages(null);}
}
