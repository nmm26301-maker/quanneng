package app.quanneng;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** 仅本应用可读的下载/转换结果；路径受限且未解锁时拒绝新读取。 */
public final class MediaContentProvider extends ContentProvider {
    static Uri uri(File file){return new Uri.Builder().scheme("content").authority("app.quanneng.media").appendPath(file.getParentFile().getName()).appendPath(file.getName()).build();}
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        if(!PrivateSession.isUnlocked())throw new FileNotFoundException("需要先解锁全能");
        List<String> parts=uri.getPathSegments();if(parts.size()!=2)throw new FileNotFoundException();
        try{File root=TransferService.root(getContext()).getCanonicalFile(),file=new File(new File(root,parts.get(0)),parts.get(1)).getCanonicalFile();if(!file.isFile()||!file.getParentFile().getParentFile().equals(root)||!new File(file.getParentFile(),".complete").isFile())throw new FileNotFoundException();return file;}catch(IOException error){throw new FileNotFoundException();}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){String path=uri.getLastPathSegment();return path!=null&&path.endsWith(".webm")?"video/webm":path!=null&&path.endsWith(".mkv")?"video/x-matroska":"video/mp4";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=file(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}catch(FileNotFoundException error){throw new SecurityException("请先解锁并选择有效的视频文件");}}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String w,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String w,String[] a){throw new UnsupportedOperationException();}
}
