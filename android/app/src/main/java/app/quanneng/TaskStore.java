package app.quanneng;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
/** 每任务原子落盘，下载中断后保留分片供恢复。 */
final class TaskStore {
 private static long sequence;
 static final class Job {String id,mode,value,title="";long order;String state="queued",error="";int percent=-1;JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("mode",mode).put("value",value).put("state",state).put("error",error).put("percent",percent).put("title",title).put("order",order);}}
 static File folder(Context c,Job j){return new File(TransferService.root(c),j.id);}
 static synchronized void save(Context c,Job j)throws IOException{File d=folder(c,j);d.mkdirs();AtomicFile a=new AtomicFile(new File(d,"job.json"));FileOutputStream o=null;try{o=a.startWrite();o.write(j.json().toString().getBytes("UTF-8"));a.finishWrite(o);}catch(Exception e){if(o!=null)a.failWrite(o);throw new IOException("任务记录无法保存",e);}}
 static synchronized List<Job> list(Context c){List<Job> jobs=new ArrayList<>();File[] dirs=TransferService.root(c).listFiles();if(dirs==null)return jobs;Arrays.sort(dirs,Comparator.comparing(File::getName));for(File d:dirs){try(InputStream in=new AtomicFile(new File(d,"job.json")).openRead()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()>32768)throw new IOException();out.write(b,0,n);}JSONObject o=new JSONObject(out.toString("UTF-8"));Job j=new Job();j.id=o.getString("id");if(!j.id.equals(d.getName()))continue;j.mode=o.getString("mode");j.value=o.getString("value");j.state=o.getString("state");j.error=o.optString("error");j.percent=o.optInt("percent",-1);j.title=o.optString("title",ToolRequest.label(j.mode));j.order=o.optLong("order",d.lastModified());jobs.add(j);}catch(Exception ignored){}}jobs.sort(Comparator.comparingLong(j->j.order));return jobs;}
 static synchronized Job add(Context c,String mode,String value)throws IOException{Job j=new Job();for(Job old:list(c))sequence=Math.max(sequence,old.order);j.order=Math.max(System.currentTimeMillis(),sequence+1);sequence=j.order;j.id=String.format(Locale.ROOT,"%019d-",j.order)+UUID.randomUUID();j.mode=mode;j.value=value;
  try{if("download".equals(mode)){java.net.URI u=new java.net.URI(value);String path=u.getPath();j.title=(u.getHost()==null?"网络视频":u.getHost())+(path==null?"":path);}else j.title=new JSONObject(value).optString("title","本地视频");}catch(Exception e){j.title="本地视频";}
  j.title=j.title.replaceAll("[\\r\\n\\t]"," ");if(j.title.length()>100)j.title=j.title.substring(0,100);save(c,j);return j;}

 static synchronized void recover(Context c){for(Job j:list(c))if("running".equals(j.state)||"queued".equals(j.state)){j.state="paused";j.error="上次任务中断，点击继续";try{save(c,j);}catch(IOException ignored){}}}
}
