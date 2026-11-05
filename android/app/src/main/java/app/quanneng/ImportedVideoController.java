package app.quanneng;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 将用户明确选取的、可读取的普通视频复制到用户选择的位置。
 * 不扫描其他应用的私有目录，不读取 DRM 密钥，也不将缓存伪装成已解密文件。
 */
public final class ImportedVideoController {
    public static final int PICK_SOURCE = 45;
    public static final int SAVE_DESTINATION = 46;
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri source;

    public ImportedVideoController(Activity activity) {
        this.activity = activity;
    }

    public void begin() {
        try {
            // 使用系统文件选择器取得用户授予的访问权限，而非申请全盘存储权限。
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(pick, PICK_SOURCE);
        } catch (ActivityNotFoundException error) {
            message("系统未找到文件选择器");
        }
    }

    public boolean onActivityResult(int request, int result, Intent data) {
        if (request != PICK_SOURCE && request != SAVE_DESTINATION) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            source = null;
            return true;
        }
        if (request == PICK_SOURCE) {
            source = data.getData();
            String name = displayName(source);
            String mime = activity.getContentResolver().getType(source);
            if (!supported(name, mime)) {
                source = null;
                message("请选择已导出的普通视频，例如 MP4、MKV、MOV 或 WebM");
                return true;
            }
            try {
                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType(mime != null && mime.startsWith("video/") ? mime : "application/octet-stream");
                save.putExtra(Intent.EXTRA_TITLE, name);
                save.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.startActivityForResult(save, SAVE_DESTINATION);
            } catch (ActivityNotFoundException error) {
                source = null;
                message("系统未找到保存位置选择器");
            }
            return true;
        }
        final Uri input = source;
        final Uri output = data.getData();
        source = null;
        if (input != null) copyToPhone(input, output);
        return true;
    }

    private static boolean supported(String name, String mime) {
        String lower = name.toLowerCase(Locale.ROOT);
        return (mime != null && mime.startsWith("video/"))
                || lower.matches(".*\\.(mp4|mkv|webm|mov|avi|m4v|3gp|flv|ts)$");
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    name = name.replace('/', '_').replace('\\', '_').trim();
                    return name.length() > 160 ? name.substring(name.length() - 160) : name;
                }
            }
        } catch (RuntimeException ignored) {
            // 某些云端文件提供者不返回文件名。
        }
        return "quanneng-video.mp4";
    }

    private void copyToPhone(Uri input, Uri output) {
        AlertDialog progress = new PrivateDialogBuilder(activity)
                .setTitle("正在保存到手机")
                .setMessage("请保持应用运行，大文件需要更多时间。")
                .setCancelable(false)
                .create();
        progress.show();
        progress.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        worker.execute(() -> {
            boolean success = false;
            try (InputStream in = activity.getContentResolver().openInputStream(input);
                 OutputStream out = activity.getContentResolver().openOutputStream(output, "w")) {
                if (in == null || out == null) throw new IOException("文件提供者无法读取或写入");
                byte[] chunk = new byte[65536];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("保存已中断");
                    out.write(chunk, 0, count);
                }
                out.flush();
                success = true;
            } catch (Exception ignored) {
                // 文件权限、空间不足、云盘无法导出或受保护文件都会走失败提示。
            }
            final boolean copied = success;
            if (!copied) {
                try { activity.getContentResolver().delete(output, null, null); }
                catch (RuntimeException ignored) {}
            }
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                if (!copied) {
                    message("保存失败：请确认来源允许导出且手机有足够空间");
                    return;
                }
                message("视频已保存到选择的位置");
                Intent play = new Intent(activity, SystemPlayerActivity.class)
                        .setData(output).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ((PrivateActivity)activity).whenUnlocked(()->activity.startActivity(play));
            });
        });
    }

    private void message(String value) {
        Toast.makeText(activity, value, Toast.LENGTH_LONG).show();
    }

    public void close() {
        worker.shutdownNow();
    }
}
