package app.quanneng.tests;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** 仅测试 APK 包含的只读公开测试视频，不随正式应用发布。 */
public final class FixtureProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "/jfk.wav".equals(uri.getPath())?"audio/wav":"video/mp4";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!("/flower.mp4".equals(uri.getPath())||"/seek-fixture.mp4".equals(uri.getPath())||"/buffer-fixture.mp4".equals(uri.getPath())||"/jfk.wav".equals(uri.getPath()))||!"r".equals(mode))throw new FileNotFoundException();
        String name=uri.getLastPathSegment();File file=new File(getContext().getCacheDir(),"fixture-"+name);
        try{if(!file.exists())try(InputStream input=getContext().getAssets().open(name);OutputStream output=new FileOutputStream(file)){byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)output.write(block,0,n);}}catch(IOException e){throw new FileNotFoundException(e.getMessage());}
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){MatrixCursor c=new MatrixCursor(new String[]{"_display_name"});c.addRow(new Object[]{uri.getLastPathSegment()});return c;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
