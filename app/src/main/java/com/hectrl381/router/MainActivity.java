package com.hectrl381.router;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private String router = "192.168.8.1", username = "admin", password = "";
    private String cookie = "", token = "";
    private EditText ipInput, userInput, passInput;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView tv(String s, float size) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(dp(8),dp(8),dp(8),dp(8)); return t; }
    private Button btn(String s) { Button b=new Button(this); b.setText(s); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackgroundColor(Color.rgb(37,99,235)); b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(52))); return b; }
    private LinearLayout card() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(14),dp(12),dp(14),dp(12)); l.setBackgroundColor(Color.rgb(17,24,32)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(7),0,dp(7)); l.setLayoutParams(p); return l; }
    private TextView value(String label,String v) { return tv(label+"\n"+v,16); }

    @Override public void onCreate(Bundle state) { super.onCreate(state); loginScreen(); }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private void loginScreen() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(22),dp(30),dp(22),dp(22)); root.setBackgroundColor(Color.rgb(7,11,16));
        root.addView(tv("📡 Huawei Router Manager",27),new LinearLayout.LayoutParams(-1,dp(65)));
        root.addView(tv("H158-381 • تحكم فعلي عبر API المحلي",15),new LinearLayout.LayoutParams(-1,dp(55)));
        ipInput=field("عنوان الراوتر",router); root.addView(ipInput); userInput=field("اسم المستخدم",username); root.addView(userInput); passInput=field("كلمة مرور الإدارة",""); passInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(passInput);
        Button connect=btn("🔐 اتصال وتسجيل دخول"); connect.setOnClickListener(v->connect()); root.addView(connect);
        TextView hint=tv("يتم الاتصال مباشرة بالراوتر. كلمة المرور لا تغادر الجهاز.",13); hint.setTextColor(Color.LTGRAY); root.addView(hint); setContentView(root);
    }
    private EditText field(String hint,String value) { EditText e=new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setPadding(dp(14),0,dp(14),0); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55)); p.setMargins(0,dp(6),0,dp(6)); e.setLayoutParams(p); return e; }

    private void connect() {
        router=ipInput.getText().toString().trim().replaceFirst("^https?://",""); if(router.endsWith("/"))router=router.substring(0,router.length()-1); username=userInput.getText().toString().trim(); password=passInput.getText().toString();
        if(router.isEmpty()){toast("اكتب عنوان الراوتر");return;} setContentView(tv("جاري الاتصال بالراوتر...",16));
        io.execute(()->{try{getSession(); if(!password.isEmpty())login(); runOnUiThread(this::dashboard); refreshData();}catch(Exception e){runOnUiThread(()->{loginScreen();toast("فشل الاتصال: "+e.getMessage());});}});
    }
    private String base(){return "http://"+router;}

    private void getSession() throws Exception {
        HttpResult r=request("GET","/api/webserver/SesTokInfo",null,false); String ses=tag(r.body,"SesInfo"), tok=tag(r.body,"TokInfo");
        if(!ses.isEmpty())cookie=ses.contains("=")?ses:"SessionID="+ses; if(!tok.isEmpty())token=tok; if(cookie.isEmpty())throw new Exception("لم يتم الحصول على SessionID");
    }
    private void login() throws Exception {
        String first=randomHex(32); String challenge="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><username>"+xml(username)+"</username><firstnonce>"+first+"</firstnonce><mode>1</mode></request>";
        HttpResult c=request("POST","/api/user/challenge_login",challenge,true); String salt=tag(c.body,"salt"), server=tag(c.body,"servernonce"); int iterations=parseInt(tag(c.body,"iterations"),1000);
        if(salt.isEmpty()||server.isEmpty())throw new Exception("الراوتر رفض تسجيل الدخول");
        byte[] salted=pbkdf2(password,hexToBytes(salt),iterations), clientKey=hmac(salted,"Client Key"), stored=sha256(clientKey), sig=hmac(stored,first+","+server+","+server), proof=new byte[clientKey.length];
        for(int i=0;i<clientKey.length;i++)proof[i]=(byte)(clientKey[i]^sig[i]);
        String body="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><clientproof>"+hex(proof)+"</clientproof><finalnonce>"+xml(server)+"</finalnonce></request>";
        HttpResult l=request("POST","/api/user/authentication_login",body,true); if(l.code>=400||l.body.contains("<error>"))throw new Exception("كلمة المرور غير صحيحة أو غير مدعومة"); if(l.setCookie!=null&&!l.setCookie.isEmpty())cookie=l.setCookie.split(";",2)[0]; getSession();
    }

    private void refreshData(){io.execute(()->{try{Map<String,String>m=new LinkedHashMap<>(); add(m,get("/api/monitoring/status"),new String[]{"ConnectionStatus","CurrentNetworkType","CurrentNetworkTypeEx","SignalIcon","CurrentWifiUser"}); add(m,get("/api/device/signal"),new String[]{"rsrp","rsrq","sinr","rssi","nrrsrp","nrrsrq","nrsinr","nrrssi","band","pci","scc_pci","cell_id","enodeb_id","nrearfcn","lteearfcn"}); add(m,get("/api/net/current-plmn"),new String[]{"FullName","ShortName","Numeric","Rat","NetworkName","plmn"}); add(m,get("/api/device/information"),new String[]{"DeviceName","SoftwareVersion","SerialNumber","Imei"}); add(m,get("/api/monitoring/traffic-statistics"),new String[]{"CurrentDownloadRate","CurrentUploadRate","TotalDownload","TotalUpload","CurrentConnectTime"}); add(m,get("/api/net/net-mode"),new String[]{"NetworkMode","LTEBand","NRBand","NetworkBand"}); runOnUiThread(()->dashboardWith(m));}catch(Exception e){runOnUiThread(()->toast("تعذر تحديث البيانات: "+e.getMessage()));}});}
    private void add(Map<String,String>m,String xml,String[]names){for(String n:names){String v=tag(xml,n);if(!v.isEmpty())m.put(n,v);}}
    private String get(String p)throws Exception{return request("GET",p,null,false).body;}

    private void dashboard(){dashboardWith(new LinkedHashMap<>());}
    private void dashboardWith(Map<String,String>m){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(20),dp(14),dp(20)); root.setBackgroundColor(Color.rgb(7,11,16));
        root.addView(tv("📡 Huawei Router Manager",26)); TextView sub=tv("H158-381 • "+router+" • اتصال محلي فعلي",14);sub.setTextColor(Color.LTGRAY);root.addView(sub);
        LinearLayout c=card();c.addView(tv("📶 الإشارة والخلية",19));c.addView(value("RSRP / RSRQ / SINR",v(m,"rsrp","—")+" / "+v(m,"rsrq","—")+" / "+v(m,"sinr","—")));c.addView(value("5G RSRP / RSRQ / SINR",v(m,"nrrsrp","—")+" / "+v(m,"nrrsrq","—")+" / "+v(m,"nrsinr","—")));c.addView(value("Band / PCI / Cell ID",v(m,"band","—")+" / "+v(m,"pci",v(m,"scc_pci","—"))+" / "+v(m,"cell_id","—")));c.addView(value("ENodeB / EARFCN",v(m,"enodeb_id","—")+" / "+v(m,"nrearfcn",v(m,"lteearfcn","—"))));root.addView(c);
        c=card();c.addView(tv("🌐 الشبكة",19));c.addView(value("نوع الاتصال",v(m,"CurrentNetworkTypeEx",v(m,"CurrentNetworkType","—"))));c.addView(value("المشغل",v(m,"FullName",v(m,"NetworkName","—"))));c.addView(value("المستخدمون المتصلون",v(m,"CurrentWifiUser","—")));root.addView(c);
        c=card();c.addView(tv("📊 السرعة والبيانات",19));c.addView(value("Download / Upload",rate(v(m,"CurrentDownloadRate","—"))+" / "+rate(v(m,"CurrentUploadRate","—"))));c.addView(value("إجمالي التنزيل / الرفع",bytes(v(m,"TotalDownload","—"))+" / "+bytes(v(m,"TotalUpload","—"))));root.addView(c);
        c=card();c.addView(tv("🔧 التحكم الفعلي",19));Button b=btn("🔄 تحديث البيانات");b.setOnClickListener(x->refreshData());c.addView(b);b=btn("📡 قفل/تحديد 4G و 5G Bands");b.setOnClickListener(x->bandDialog());c.addView(b);b=btn("🔁 إعادة الاتصال بالشبكة");b.setOnClickListener(x->action("/api/net/reconnect","<request><ReconnectAction>1</ReconnectAction></request>","تم طلب إعادة الاتصال"));c.addView(b);b=btn("♻️ إعادة تشغيل الراوتر");b.setOnClickListener(x->confirmReboot());c.addView(b);b=btn("🌐 واجهة Huawei الأصلية");b.setOnClickListener(x->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(base()+"/"))));c.addView(b);root.addView(c);
        TextView note=tv("الأوامر تُرسل إلى API الراوتر مباشرة. إذا كان Firmware يمنع وظيفة معينة سيظهر رفض الراوتر ولن يتم تنفيذ أمر وهمي.",12);note.setTextColor(Color.LTGRAY);root.addView(note);scroll.addView(root);setContentView(scroll);
    }
    private String v(Map<String,String>m,String k,String d){String x=m.get(k);return x==null||x.isEmpty()?d:x;}
    private String rate(String s){try{double x=Double.parseDouble(s);if(x>1000000)return String.format(Locale.US,"%.2f Mbps",x/1000000d);if(x>1000)return String.format(Locale.US,"%.2f Mbps",x/1000d);return s;}catch(Exception e){return s;}}
    private String bytes(String s){try{double x=Double.parseDouble(s);if(x>1e9)return String.format(Locale.US,"%.2f GB",x/1e9);if(x>1e6)return String.format(Locale.US,"%.2f MB",x/1e6);return s;}catch(Exception e){return s;}}

    private void bandDialog(){io.execute(()->{try{String xml=get("/api/net/net-mode");runOnUiThread(()->showBands(xml));}catch(Exception e){runOnUiThread(()->toast("تعذر قراءة الباندات: "+e.getMessage()));}});}
    private void showBands(String xml){
        String lm=tag(xml,"LTEBand"),nm=tag(xml,"NRBand");LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(10),0,dp(10),0);box.addView(tv("4G LTE",18));
        int[] lte={1,3,7,8,20,28,38,40,41};List<CheckBox>lc=new ArrayList<>();for(int b:lte){CheckBox c=new CheckBox(this);c.setText("B"+b);c.setTextColor(Color.WHITE);c.setChecked(maskHas(lm,b));lc.add(c);box.addView(c);}
        box.addView(tv("5G NR",18));int[]nr={1,3,5,7,8,20,28,38,40,41,77,78,79};List<CheckBox>nc=new ArrayList<>();for(int b:nr){CheckBox c=new CheckBox(this);c.setText("n"+b);c.setTextColor(Color.WHITE);c.setChecked(maskHas(nm,b));nc.add(c);box.addView(c);}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("تحديد الباندات").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("تطبيق",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{applyBands(mask(lc,lte),mask(nc,nr));d.dismiss();}));d.show();
    }
    private boolean maskHas(String mask,int band){try{return mask!=null&&!mask.isEmpty()&&new java.math.BigInteger(mask,16).testBit(band-1);}catch(Exception e){return false;}}
    private String mask(List<CheckBox>b,int[]bands){java.math.BigInteger x=java.math.BigInteger.ZERO;for(int i=0;i<bands.length;i++)if(b.get(i).isChecked())x=x.setBit(bands[i]-1);return x.toString(16).toUpperCase(Locale.US);}
    private void applyBands(String lte,String nr){io.execute(()->{try{String cur=get("/api/net/net-mode"),nb=tag(cur,"NetworkBand"),mode=tag(cur,"NetworkMode");if(nb.isEmpty())nb="3FFFFFFFFFFFFFFF";if(mode.isEmpty())mode="00";String body="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><NetworkMode>"+mode+"</NetworkMode><NetworkBand>"+nb+"</NetworkBand><LTEBand>"+lte+"</LTEBand><NRBand>"+nr+"</NRBand></request>";HttpResult r=request("POST","/api/net/net-mode",body,true);if(r.code>=400||r.body.contains("<error>"))throw new Exception("الراوتر رفض الأمر: "+tag(r.body,"code"));runOnUiThread(()->{toast("تم إرسال إعدادات الباندات");refreshData();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
    private void confirmReboot(){new AlertDialog.Builder(this).setTitle("إعادة تشغيل الراوتر؟").setMessage("سيقطع الاتصال لعدة دقائق.").setNegativeButton("إلغاء",null).setPositiveButton("إعادة التشغيل",(d,w)->action("/api/device/control","<request><Control>1</Control></request>","تم إرسال أمر إعادة التشغيل")).show();}
    private void action(String path,String body,String ok){io.execute(()->{try{HttpResult r=request("POST",path,body,true);if(r.code>=400||r.body.contains("<error>"))throw new Exception("الراوتر رفض الأمر: "+tag(r.body,"code"));runOnUiThread(()->toast(ok));}catch(Exception e){runOnUiThread(()->toast("فشل الأمر: "+e.getMessage()));}});}

    private HttpResult request(String method,String path,String body,boolean write)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(base()+path).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestMethod(method);c.setUseCaches(false);c.setRequestProperty("X-Requested-With","XMLHttpRequest");c.setRequestProperty("Accept","*/*");if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);if(write){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/xml; charset=UTF-8");if(!token.isEmpty())c.setRequestProperty("__RequestVerificationToken",token);}if(body!=null){c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));c.getOutputStream().close();}int code=c.getResponseCode();String nt=c.getHeaderField("__RequestVerificationToken");if(nt!=null&&!nt.isEmpty())token=nt;String sc=c.getHeaderField("Set-Cookie");if(sc!=null&&!sc.isEmpty())cookie=sc.split(";",2)[0];InputStream in=code>=400?c.getErrorStream():c.getInputStream();String text=read(in);c.disconnect();return new HttpResult(code,text,sc);}
    private String read(InputStream in)throws Exception{if(in==null)return"";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);r.close();return b.toString();}
    private static class HttpResult{int code;String body,setCookie;HttpResult(int c,String b,String s){code=c;body=b;setCookie=s;}}
    private static String tag(String xml,String name){if(xml==null)return"";Matcher m=Pattern.compile("<"+Pattern.quote(name)+">(.*?)</"+Pattern.quote(name)+">",Pattern.DOTALL).matcher(xml);return m.find()?m.group(1).trim():"";}
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private static String randomHex(int n){byte[]b=new byte[n];new SecureRandom().nextBytes(b);return hex(b);}
    private static byte[]hexToBytes(String s){if(s.length()%2!=0)throw new IllegalArgumentException("salt غير صالح");byte[]r=new byte[s.length()/2];for(int i=0;i<r.length;i++)r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return r;}
    private static String hex(byte[]b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    private static byte[]sha256(byte[]b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
    private static byte[]hmac(byte[]key,String msg)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(msg.getBytes(StandardCharsets.UTF_8));}
    private static byte[]pbkdf2(String pass,byte[]salt,int it)throws Exception{PBEKeySpec s=new PBEKeySpec(pass.toCharArray(),salt,it,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(s).getEncoded();}finally{s.clearPassword();}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){loginScreen();}
}
