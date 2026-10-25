package app.quanneng;

import android.app.*;
import android.content.Context;
import android.view.WindowManager;

/** 对话框也禁止系统截图，并在应用离开前台时关闭，避免泄露网址和文件信息。 */
final class PrivateDialogBuilder extends AlertDialog.Builder {
    private final PrivateActivity owner;
    PrivateDialogBuilder(Context context){super(context);owner=context instanceof PrivateActivity?(PrivateActivity)context:null;}
    @Override public AlertDialog create(){AlertDialog dialog=super.create();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);if(owner!=null)owner.rememberPrivateDialog(dialog);return dialog;}
}
