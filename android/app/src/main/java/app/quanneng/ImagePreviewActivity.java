package app.quanneng;
import android.graphics.*;
import android.os.Bundle;
import android.widget.*;
/** 截图结果的本机安全预览。 */
public final class ImagePreviewActivity extends FeatureActivity {
 @Override public void onCreate(Bundle b){super.onCreate(b);page("截图预览");ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setContentDescription("工具箱导出的画面");body.addView(image,new android.widget.LinearLayout.LayoutParams(-1,-2));whenUnlocked(()->work("读取截图…",()->{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(getIntent().getData())){BitmapFactory.decodeStream(in,null,options);}options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1600);options.inJustDecodeBounds=false;try(java.io.InputStream in=getContentResolver().openInputStream(getIntent().getData())){return BitmapFactory.decodeStream(in,null,options);}},bitmap->{if(bitmap==null)message.setText("图片无法读取");else image.setImageBitmap(bitmap);}));}
}
