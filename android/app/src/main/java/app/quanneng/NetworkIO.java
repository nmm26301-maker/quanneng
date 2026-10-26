package app.quanneng;
import java.net.*;
import java.io.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
/** 有界请求与禁用 DTD 的 XML 解析，不自动转发身份到重定向地址。 */
final class NetworkIO {
 static byte[] request(URL url,String method,String auth,String type,String action,byte[] body)throws Exception{
  NetworkPolicy.url(url.toString());okhttp3.OkHttpClient client=new okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(8,java.util.concurrent.TimeUnit.SECONDS).readTimeout(12,java.util.concurrent.TimeUnit.SECONDS).callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build();
  okhttp3.Request.Builder request=new okhttp3.Request.Builder().url(url);if(auth!=null&&!auth.isEmpty()){if(auth.indexOf('\r')>=0||auth.indexOf('\n')>=0)throw new IOException("认证无效");request.header("Authorization",auth);}if("PROPFIND".equals(method))request.header("Depth","1");if(action!=null)request.header("SOAPACTION","\""+action+"\"");
  okhttp3.RequestBody payload=body==null?null:okhttp3.RequestBody.create(okhttp3.MediaType.parse(type==null?"application/octet-stream":type),body);request.method(method,payload);
  try(okhttp3.Response response=client.newCall(request.build()).execute()){int code=response.code();if(code<200||code>=300)throw new IOException("服务器返回 HTTP "+code+(code==401?"，请检查账号密码":code>=300&&code<400?"，请填写重定向后的完整地址":""));if(response.body()==null)return new byte[0];try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("服务器响应超过限制");out.write(bytes,0,n);}return out.toByteArray();}}

 }
 static Document xml(byte[] raw)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);return f.newDocumentBuilder().parse(new ByteArrayInputStream(raw));}
 static String value(Element e,String local){NodeList n=e.getElementsByTagNameNS("*",local);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
 static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
