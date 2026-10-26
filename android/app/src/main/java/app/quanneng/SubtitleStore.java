package app.quanneng;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
/** 有界 UTF-8 字幕库；通过重写时间轴提供立即生效的字幕偏移。 */
final class SubtitleStore {
 static File root(Context c){File d=new File(c.getFilesDir(),"subtitles");d.mkdirs();return d;}
 static List<File> list(Context c){File[] f=root(c).listFiles((d,n)->n.endsWith(".vtt"));return f==null?new ArrayList<>():new ArrayList<>(Arrays.asList(f));}
 static String read(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("字幕不能超过 4 MB");out.write(b,0,n);}return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString().replace("\uFEFF","").replace("\r","");}
 static final Pattern TIMES=Pattern.compile("(?m)(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})[,.](\\d{3})[ \\t]*-->[ \\t]*(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})[,.](\\d{3})([^\\n]*)");
 static long time(Matcher m,int b){return (Long.parseLong(m.group(b)==null?"0":m.group(b))*3600+Long.parseLong(m.group(b+1))*60+Long.parseLong(m.group(b+2)))*1000+Long.parseLong(m.group(b+3));}
 static String stamp(long ms){ms=Math.max(0,ms);return String.format(Locale.ROOT,"%02d:%02d:%02d.%03d",ms/3600000,ms/60000%60,ms/1000%60,ms%1000);}
 static String shift(String raw,long offset)throws IOException{Matcher m=TIMES.matcher(raw);StringBuffer b=new StringBuffer();int count=0;while(m.find()){long a=time(m,1),z=time(m,5);if(z<=a)throw new IOException("字幕时间范围无效");m.appendReplacement(b,Matcher.quoteReplacement(stamp(a+offset)+" --> "+stamp(z+offset)+m.group(9)));count++;}m.appendTail(b);if(count==0)throw new IOException("请选择 UTF-8 SRT 或 VTT 字幕");String v=b.toString();return v.startsWith("WEBVTT")?v:"WEBVTT\n\n"+v;}
 static File importFile(Context c,Uri uri,String name)throws Exception{String raw;try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("字幕无法读取");raw=read(in);}String base=name.replaceAll("[^\\p{L}\\p{N}._ -]","_");if(base.length()>90)base=base.substring(0,90);File f=new File(root(c),System.currentTimeMillis()+"-"+base+".vtt");java.nio.file.Files.write(f.toPath(),shift(raw,0).getBytes("UTF-8"));return f;}
 static File adjusted(Context c,File source)throws Exception{long offset=c.getSharedPreferences("subtitle-settings",0).getLong(source.getName(),0);String raw;try(InputStream in=new FileInputStream(source)){raw=read(in);}File dir=new File(c.getCacheDir(),"subtitle-playback");dir.mkdirs();File out=new File(dir,"active.vtt");java.nio.file.Files.write(out.toPath(),shift(raw,offset).getBytes("UTF-8"));return out;}
}
