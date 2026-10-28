package app.quanneng;

import android.content.Context;
import org.json.*;

/** User-selected web videos only; request credentials remain in BrowserActivity memory. */
final class BrowserHistory {
    static JSONArray all(Context context){
        try{return new JSONArray(context.getSharedPreferences("browser-history",0).getString("videos","[]"));}
        catch(JSONException error){return new JSONArray();}
    }
    static synchronized void delivered(Context context,String snapshot){
        android.content.SharedPreferences prefs=context.getSharedPreferences("browser-history",0);
        if(snapshot.equals(prefs.getString("videos","[]")))prefs.edit().remove("videos").apply();
    }
    static synchronized void remember(Context context,JSONObject source,int position,int duration){
        if(source==null||!MainActivity.isHttps(source.optString("url")))return;
        try{
            String url=source.getString("url"),page=source.optString("page","");
            JSONObject video=new JSONObject().put("url",url).put("name",source.optString("title","网络视频"))
                .put("sourcePage",MainActivity.isHttps(page)?page:"").put("mime",source.optString("mime",""))
                .put("position",duration>0&&position>=duration-500?0:Math.max(0,position)/1000.0);
            JSONArray next=new JSONArray().put(video),previous=all(context);
            for(int i=0;i<previous.length()&&next.length()<100;i++){JSONObject old=previous.optJSONObject(i);if(old!=null&&!url.equals(old.optString("url"))&&next.toString().length()+old.toString().length()<250000)next.put(old);}
            context.getSharedPreferences("browser-history",0).edit().putString("videos",next.toString()).apply();
        }catch(JSONException ignored){}
    }
}
