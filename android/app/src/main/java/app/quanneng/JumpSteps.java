package app.quanneng;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;

/** 四个独立步长：后退秒、后退分钟、前进秒、前进分钟；单位始终明确。 */
final class JumpSteps {
    static final int[] DEFAULTS={10,1,10,1};
    static final String[] LABELS={"后退秒数","后退分钟数","前进秒数","前进分钟数"};
    private static final String[] KEYS={"backSeconds","backMinutes","forwardSeconds","forwardMinutes"};
    static int max(int index){return index%2==0?3600:120;}
    static boolean valid(int[] values){if(values==null||values.length!=4)return false;for(int i=0;i<4;i++)if(values[i]<1||values[i]>max(i))return false;return true;}
    static int[] load(Context context){SharedPreferences p=context.getSharedPreferences("jump-steps-v1",Context.MODE_PRIVATE);int[] values=new int[4];for(int i=0;i<4;i++)values[i]=p.getInt(KEYS[i],DEFAULTS[i]);return valid(values)?values:DEFAULTS.clone();}
    static boolean save(Context context,int[] values){if(!valid(values))return false;SharedPreferences.Editor edit=context.getSharedPreferences("jump-steps-v1",Context.MODE_PRIVATE).edit();for(int i=0;i<4;i++)edit.putInt(KEYS[i],values[i]);return edit.commit();}
    static int delta(int[] values,int index){return (index<2?-1:1)*values[index]*(index%2==0?1:60);}
    static String description(int[] values,int index){return (index<2?"后退":"前进")+values[index]+(index%2==0?"秒":"分钟");}
    static JSONArray json(Context context){JSONArray result=new JSONArray();for(int value:load(context))result.put(value);return result;}
}
