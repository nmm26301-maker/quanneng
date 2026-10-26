package app.quanneng;
import java.net.*;
/** 公网只允许 HTTPS；HTTP 明文仅限数字形式的局域网 IPv4。 */
final class NetworkPolicy {
 static boolean privateHost(String host){if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;String[] p=host.split("\\.");int[] n=new int[4];for(int i=0;i<4;i++){n[i]=Integer.parseInt(p[i]);if(n[i]>255)return false;}return n[0]==10||n[0]==192&&n[1]==168||n[0]==172&&n[1]>=16&&n[1]<=31;}
 static URL url(String value)throws java.io.IOException{try{URI u=new URI(value);if(value.length()>8192||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||!("https".equals(u.getScheme())||"http".equals(u.getScheme())&&privateHost(u.getHost())))throw new Exception();return u.toURL();}catch(Exception e){throw new java.io.IOException("使用 HTTPS 地址，或 http://局域网数字IP/ 路径");}}
 static URL local(String value)throws java.io.IOException{URL u=url(value);if(!privateHost(u.getHost()))throw new java.io.IOException("设备地址需要是局域网数字 IP");return u;}
 static boolean sameOrigin(URL a,URL b){return a.getProtocol().equals(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&(a.getPort()<0?a.getDefaultPort():a.getPort())==(b.getPort()<0?b.getDefaultPort():b.getPort());}
}
