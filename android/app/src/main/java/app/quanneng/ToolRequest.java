package app.quanneng;
import org.json.*;
/** 在打开系统选择器前冻结工具类型及参数，避免返回后换用过期界面值。 */
final class ToolRequest {
    final String mode;final double start,end;
    private ToolRequest(String m,double a,double b){mode=m;start=a;end=b;}
    static ToolRequest capture(String mode,String a,String b){
        if(mode==null||!mode.matches("convert|trim|audio|compress|snapshot|mute"))throw new IllegalArgumentException("工具类型无效");
        double start=mode.equals("trim")||mode.equals("snapshot")?seconds(a):0;
        double end=mode.equals("trim")?seconds(b):0;
        if(mode.equals("trim")&&end<=start)throw new IllegalArgumentException("裁剪终点必须大于起点");
        return new ToolRequest(mode,start,end);
    }
    private static double seconds(String text){try{double n=text==null||text.trim().isEmpty()?0:Double.parseDouble(text.trim());if(!Double.isFinite(n)||n<0||n>604800)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException("请输入 0–604800 范围内的秒数");}}
    String saved()throws JSONException{return new JSONObject().put("mode",mode).put("start",start).put("end",end).toString();}
    static ToolRequest restore(String raw)throws JSONException{JSONObject o=new JSONObject(raw);return capture(o.getString("mode"),String.valueOf(o.getDouble("start")),String.valueOf(o.getDouble("end")));}
    JSONObject options(String uri,String title)throws JSONException{return new JSONObject().put("uri",uri).put("title",title).put("start",start).put("end",end);}
    static String label(String mode){switch(mode){case "trim":return "裁剪片段";case "audio":return "提取音频";case "compress":return "压缩视频";case "snapshot":return "导出截图";case "mute":return "生成无声视频";case "download":return "下载视频";default:return "转换 MP4";}}
}
