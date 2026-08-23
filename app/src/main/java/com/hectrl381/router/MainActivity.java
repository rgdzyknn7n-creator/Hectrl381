package com.hectrl381.router;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
import java.util.LinkedHashMap;
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
    private String cookie = "", token = "", tokenOne = "", tokenTwo = "";
    private EditText ipInput, userInput, passInput;
    private Map<String,String> lastData = new LinkedHashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private volatile boolean refreshInFlight = false;
    private boolean autoRefresh = false;
    private final Runnable refreshLoop = () -> refreshData();

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView tv(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(8),dp(6),dp(8),dp(6));return t;}
    private int green(){return Color.rgb(126,231,75);} private int yellow(){return Color.rgb(255,193,7);} private int red(){return Color.rgb(244,67,54);}
    private android.graphics.drawable.GradientDrawable bg(int color,float radius){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));g.setStroke(dp(1),Color.rgb(38,48,62));return g;}
    private LinearLayout panel(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(12),dp(12),dp(12),dp(12));l.setBackground(bg(Color.rgb(13,19,28),18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(7));l.setLayoutParams(p);return l;}
    private Button actionButton(String text,int color){Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(bg(color,14));b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(54),1));return b;}

    @Override public void onCreate(Bundle state){super.onCreate(state);loginScreen();}
    @Override protected void onDestroy(){stopAutoRefresh();io.shutdownNow();super.onDestroy();}

    private void loginScreen(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(22),dp(30),dp(22),dp(22));root.setBackgroundColor(Color.rgb(5,9,14));
        root.addView(tv("📡 Huawei Router Manager",27),new LinearLayout.LayoutParams(-1,dp(65)));
        root.addView(tv("H158-381 • تحكم فعلي عبر API المحلي",15),new LinearLayout.LayoutParams(-1,dp(55)));
        ipInput=field("عنوان الراوتر",router);root.addView(ipInput);
        userInput=field("اسم المستخدم",username);root.addView(userInput);
        passInput=field("كلمة مرور الإدارة","");passInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(passInput);
        Button connect=new Button(this);connect.setText("🔐 اتصال وتسجيل دخول");connect.setTextSize(15);connect.setTextColor(Color.WHITE);connect.setAllCaps(false);connect.setBackground(bg(Color.rgb(37,99,235),14));connect.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));connect.setOnClickListener(v->connect());root.addView(connect);
        TextView hint=tv("يتم الاتصال مباشرة بالراوتر. كلمة المرور لا تغادر الجهاز.",13);hint.setTextColor(Color.LTGRAY);root.addView(hint);setContentView(root);
    }
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setPadding(dp(14),0,dp(14),0);e.setBackground(bg(Color.rgb(13,19,28),12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));p.setMargins(0,dp(6),0,dp(6));e.setLayoutParams(p);return e;}

    private void connect(){
        router=ipInput.getText().toString().trim().replaceFirst("^https?://","");if(router.endsWith("/"))router=router.substring(0,router.length()-1);
        username=userInput.getText().toString().trim();password=passInput.getText().toString();
        if(router.isEmpty()){toast("اكتب عنوان الراوتر");return;}
        setContentView(tv("جاري الاتصال بالراوتر...",16));
        io.execute(()->{try{if(password.isEmpty())throw new Exception("أدخل كلمة مرور الإدارة");login();runOnUiThread(()->{dashboard();startAutoRefresh();});}catch(Exception e){runOnUiThread(()->{loginScreen();toast("فشل تسجيل الدخول: "+e.getMessage());});}});
    }
    private String base(){return "http://"+router;}

    private void initSession() throws Exception{
        cookie="";token="";tokenOne="";tokenTwo="";HttpResult r=request("GET","/api/webserver/SesTokInfo",null,false);
        String ses=tag(r.body,"SesInfo"),tok=tag(r.body,"TokInfo");if(!ses.isEmpty())cookie=ses.startsWith("SessionID=")?ses:"SessionID="+ses;token=firstToken(tok);
        if(cookie.isEmpty())throw new Exception("لم يتم الحصول على SessionID");
        if(token.isEmpty()){HttpResult t=request("GET","/api/webserver/token",null,false);token=firstToken(tag(t.body,"token"));}
        if(token.isEmpty())throw new Exception("لم يتم الحصول على رمز الدخول");
    }
    private void login() throws Exception{
        Exception last=null;for(int attempt=0;attempt<2;attempt++){try{initSession();HttpResult state=request("GET","/api/user/state-login",null,false);String st=firstNonEmpty(tag(state.body,"State"),tag(state.body,"state"));
            if("0".equals(st)){String u=firstNonEmpty(tag(state.body,"Username"),tag(state.body,"username"));if(u.isEmpty()||username.equalsIgnoreCase(u))return;}
            String passwordType=firstNonEmpty(tag(state.body,"password_type"),tag(state.body,"PasswordType"));try{if("4".equals(passwordType)||passwordType.isEmpty())legacyLogin();else scramLogin();verifyLoggedIn();return;}catch(Exception e){last=e;String msg=e.getMessage()==null?"":e.getMessage();if(msg.contains("125003")||msg.contains("125002")||msg.contains("125001")||msg.toLowerCase(Locale.US).contains("token"))continue;throw e;}
        }catch(Exception e){last=e;String msg=e.getMessage()==null?"":e.getMessage();if(msg.contains("125003")||msg.contains("125002")||msg.contains("125001")||msg.toLowerCase(Locale.US).contains("token"))continue;throw e;}}
        throw last==null?new Exception("تعذر تسجيل الدخول"):last;
    }
    private void scramLogin() throws Exception{
        String first=randomHex(32);String challenge="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><username>"+xml(username)+"</username><firstnonce>"+first+"</firstnonce><mode>1</mode></request>";
        HttpResult c=request("POST","/api/user/challenge_login",challenge,true);String err=errorCode(c.body);if(!err.isEmpty())throw new Exception("رمز الراوتر "+err);String salt=tag(c.body,"salt"),server=tag(c.body,"servernonce");int iterations=parseInt(tag(c.body,"iterations"),100);if(salt.isEmpty()||server.isEmpty())throw new Exception("الراوتر لم يعطِ بيانات تسجيل الدخول");
        byte[] salted=pbkdf2(password,hexToBytes(salt),iterations),clientKey=hmac(salted,"Client Key"),stored=sha256(clientKey),sig=hmac(stored,first+","+server+","+server);byte[] proof=new byte[clientKey.length];for(int i=0;i<clientKey.length;i++)proof[i]=(byte)(clientKey[i]^sig[i]);
        String body="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><clientproof>"+hex(proof)+"</clientproof><finalnonce>"+xml(server)+"</finalnonce></request>";HttpResult l=request("POST","/api/user/authentication_login",body,true);err=errorCode(l.body);if(!err.isEmpty())throw new Exception("رمز الراوتر "+err);if(l.code>=400)throw new Exception("فشل تسجيل الدخول HTTP "+l.code);if(l.setCookie!=null&&!l.setCookie.isEmpty())cookie=extractCookie(l.setCookie);captureTokens(l);
    }
    private void legacyLogin() throws Exception{
        if(token.isEmpty())throw new Exception("لا يوجد رمز دخول");String hashed=base64Sha256(password),passwordValue=base64Sha256(username+hashed+token);String body="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Username>"+xml(username)+"</Username><Password>"+passwordValue+"</Password><password_type>4</password_type></request>";HttpResult r=request("POST","/api/user/login",body,true);String err=errorCode(r.body);if(!err.isEmpty())throw new Exception("رمز الراوتر "+err);if(!r.body.contains("OK")&&r.code>=400)throw new Exception("فشل تسجيل الدخول HTTP "+r.code);if(r.setCookie!=null&&!r.setCookie.isEmpty())cookie=extractCookie(r.setCookie);captureTokens(r);String ses=tag(r.body,"SesInfo");if(!ses.isEmpty())cookie=ses.startsWith("SessionID=")?ses:"SessionID="+ses;
    }
    private void verifyLoggedIn() throws Exception{HttpResult r=request("GET","/api/user/state-login",null,false);String state=firstNonEmpty(tag(r.body,"State"),tag(r.body,"state"));if(!"0".equals(state)){String err=errorCode(r.body);throw new Exception(err.isEmpty()?"الراوتر لم يؤكد تسجيل الدخول":"رمز الراوتر "+err);}}
    private void captureTokens(HttpResult r){String h=r.tokenHeader;if(h!=null&&!h.isEmpty()){String[]p=h.trim().split("#");token=p.length>0?p[0].trim():"";tokenOne=p.length>1?p[1].trim():"";tokenTwo=p.length>2?p[2].trim():"";}if((token==null||token.isEmpty())&&r.tokenOne!=null&&!r.tokenOne.isEmpty())token=firstToken(r.tokenOne);}

    private void startAutoRefresh(){autoRefresh=true;refreshHandler.removeCallbacks(refreshLoop);refreshHandler.post(refreshLoop);}
    private void stopAutoRefresh(){autoRefresh=false;refreshHandler.removeCallbacks(refreshLoop);}
    private void refreshData(){
        if(refreshInFlight)return;
        refreshInFlight=true;
        io.execute(()->{try{Map<String,String>m=new LinkedHashMap<>();add(m,get("/api/monitoring/status"),new String[]{"ConnectionStatus","CurrentNetworkType","CurrentNetworkTypeEx","SignalIcon","CurrentWifiUser"});add(m,get("/api/device/signal"),new String[]{"rsrp","rsrq","sinr","rssi","nrrsrp","nrrsrq","nrsinr","nrrssi","band","pci","scc_pci","cell_id","enodeb_id","nrearfcn","lteearfcn"});add(m,get("/api/net/current-plmn"),new String[]{"FullName","ShortName","Numeric","Rat","NetworkName","plmn"});add(m,get("/api/device/information"),new String[]{"DeviceName","SoftwareVersion","SerialNumber","Imei"});add(m,get("/api/monitoring/traffic-statistics"),new String[]{"CurrentDownloadRate","CurrentUploadRate","TotalDownload","TotalUpload","CurrentConnectTime"});add(m,get("/api/net/net-mode"),new String[]{"NetworkMode","LTEBand","NRBand","NetworkBand"});lastData=m;runOnUiThread(()->{dashboardWith(m);if(autoRefresh)refreshHandler.postDelayed(refreshLoop,1000);});}catch(Exception e){runOnUiThread(()->{toast("تعذر تحديث البيانات: "+e.getMessage());if(autoRefresh)refreshHandler.postDelayed(refreshLoop,1000);});}finally{refreshInFlight=false;}});
    }
    private void add(Map<String,String>m,String x,String[]names){for(String n:names){String v=tag(x,n);if(!v.isEmpty())m.put(n,v);}}
    private String get(String p)throws Exception{return request("GET",p,null,false).body;}
    private String v(Map<String,String>m,String k,String d){String x=m.get(k);return x==null||x.trim().isEmpty()?d:x.trim();}

    private void dashboard(){dashboardWith(lastData);}
    private void dashboardWith(Map<String,String>m){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(12),dp(12),dp(20));root.setBackgroundColor(Color.rgb(5,9,14));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(8),dp(12),dp(8));header.setBackground(bg(Color.rgb(13,19,28),18));
        TextView logo=tv("📡",30);header.addView(logo,new LinearLayout.LayoutParams(dp(46),-2));LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);titleBox.addView(tv("Huawei Router Manager",22));titleBox.addView(tv("H158-381 • "+router+" • اتصال محلي",13));header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));TextView status=tv("● متصل\n↻",13);status.setTextColor(green());status.setGravity(Gravity.CENTER);status.setBackground(bg(Color.rgb(19,35,25),14));header.addView(status,new LinearLayout.LayoutParams(dp(86),dp(58)));root.addView(header);

        LinearLayout signal=panel();signal.addView(tv("الإشارة والخلية  📶",19));LinearLayout gauges=new LinearLayout(this);gauges.setGravity(Gravity.CENTER);gauges.setOrientation(LinearLayout.HORIZONTAL);
        addGauge(gauges,"RSRP","قوة الإشارة",v(m,"rsrp","—"),-120,-50,"dBm");addGauge(gauges,"RSRQ","جودة الإشارة",v(m,"rsrq","—"),-20,0,"dB");addGauge(gauges,"SINR","نسبة الإشارة للضوضاء",v(m,"sinr","—"),-10,40,"dB");signal.addView(gauges);TextView five=tv("5G RSRP / RSRQ / SINR   "+v(m,"nrrsrp","—")+" / "+v(m,"nrrsrq","—")+" / "+v(m,"nrsinr","—"),13);five.setTextColor(Color.LTGRAY);signal.addView(five);root.addView(signal);

        LinearLayout info=panel();info.addView(tv("تفاصيل الخلية والشبكة",18));LinearLayout r1=row();r1.addView(infoCell("🌐","الشبكة",v(m,"CurrentNetworkTypeEx",v(m,"CurrentNetworkType","—"))));r1.addView(infoCell("📡","النطاق",v(m,"band","—")));r1.addView(infoCell("🪪","Cell ID",v(m,"cell_id","—")));r1.addView(infoCell("◉","eNodeB",v(m,"enodeb_id","—")));info.addView(r1);LinearLayout r2=row();r2.addView(infoCell("📞","نوع الاتصال",v(m,"Rat","101")));r2.addView(infoCell("🏢","المشغل",v(m,"FullName",v(m,"NetworkName","—"))));r2.addView(infoCell("👥","المتصلون",v(m,"CurrentWifiUser","—")));info.addView(r2);root.addView(info);

        LinearLayout speed=panel();speed.addView(tv("السرعة والبيانات  📊",18));LinearLayout sr=row();sr.addView(metric("⬇","سرعة التحميل",rate(v(m,"CurrentDownloadRate","—")),green()));sr.addView(metric("⬆","سرعة الرفع",rate(v(m,"CurrentUploadRate","—")),yellow()));sr.addView(metric("◉","إجمالي البيانات",bytes(v(m,"TotalDownload","—"))+" / "+bytes(v(m,"TotalUpload","—")),Color.rgb(79,190,255)));speed.addView(sr);root.addView(speed);

        LinearLayout control=panel();control.addView(tv("التحكم الفعلي  🔧",18));LinearLayout cr=row();Button refresh=actionButton("↻\nتحديث البيانات",Color.rgb(24,58,38));refresh.setOnClickListener(x->refreshData());cr.addView(refresh);Button web=actionButton("🌐\nفتح لوحة Huawei",Color.rgb(28,48,74));web.setOnClickListener(x->{try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(base())));}catch(Exception e){toast("تعذر فتح لوحة الراوتر");}});cr.addView(web);Button bands=actionButton("📶\nإعدادات 4G / 5G",Color.rgb(55,38,78));bands.setOnClickListener(x->bandDialog());cr.addView(bands);Button reboot=actionButton("↻\nإعادة تشغيل",Color.rgb(67,35,35));reboot.setOnClickListener(x->confirmReboot());cr.addView(reboot);control.addView(cr);root.addView(control);

        TextView footer=tv("Huawei Router Manager • اتصال محلي مباشر بالراوتر",12);footer.setTextColor(Color.GRAY);footer.setGravity(Gravity.CENTER);root.addView(footer);scroll.addView(root);setContentView(scroll);
    }

    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER);return r;}
    private void addGauge(LinearLayout parent,String name,String sub,String raw,double min,double max,String unit){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);double val=parseDouble(raw,Double.NaN);SignalGauge g=new SignalGauge(this,name,sub,val,min,max,unit);box.addView(g,new LinearLayout.LayoutParams(0,dp(190),1));parent.addView(box,new LinearLayout.LayoutParams(0,-2,1));}
    private LinearLayout infoCell(String icon,String label,String value){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(8),dp(9),dp(8),dp(9));c.setBackground(bg(Color.rgb(15,23,34),13));TextView a=tv(icon+"  "+label,11);a.setTextColor(Color.LTGRAY);c.addView(a);TextView b=tv(value,14);b.setTextColor(Color.WHITE);c.addView(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(76),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));c.setLayoutParams(p);return c;}
    private LinearLayout metric(String icon,String label,String value,int color){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(8),dp(10),dp(8),dp(10));c.setBackground(bg(Color.rgb(14,22,29),14));TextView i=tv(icon,28);i.setTextColor(color);i.setGravity(Gravity.CENTER);c.addView(i);TextView l=tv(label,12);l.setTextColor(Color.LTGRAY);l.setGravity(Gravity.CENTER);c.addView(l);TextView v=tv(value,18);v.setTextColor(Color.WHITE);v.setGravity(Gravity.CENTER);c.addView(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(142),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));c.setLayoutParams(p);return c;}

    private void bandDialog(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(5),dp(20),dp(5));TextView h=tv("استخدم إعدادات الباند الحالية للراوتر.\nيمكن تعديلها من لوحة Huawei إذا كان المودم يسمح بذلك.",14);h.setTextColor(Color.LTGRAY);l.addView(h);new AlertDialog.Builder(this).setTitle("إعدادات 4G / 5G").setView(l).setNegativeButton("إلغاء",null).setPositiveButton("فتح لوحة Huawei",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(base())));}catch(Exception e){}}).show();}
    private void confirmReboot(){new AlertDialog.Builder(this).setTitle("إعادة تشغيل الراوتر؟").setMessage("سيقطع الاتصال لعدة دقائق.").setNegativeButton("إلغاء",null).setPositiveButton("إعادة التشغيل",(d,w)->action("/api/device/control","<request><Control>1</Control></request>","تم إرسال أمر إعادة التشغيل")).show();}
    private void action(String path,String body,String ok){io.execute(()->{try{HttpResult r=request("POST",path,body,true);String err=errorCode(r.body);if(!err.isEmpty())throw new Exception("الراوتر رفض الأمر: "+err);runOnUiThread(()->toast(ok));}catch(Exception e){runOnUiThread(()->toast("فشل الأمر: "+e.getMessage()));}});}

    private String rate(String s){if(s==null||s.equals("—"))return "—";try{double n=Double.parseDouble(s);if(n>1000000)return String.format(Locale.US,"%.2f Mbps",n/1000000);if(n>1000)return String.format(Locale.US,"%.2f Mbps",n/1000);return String.format(Locale.US,"%.0f Kbps",n);}catch(Exception e){return s;}}
    private String bytes(String s){if(s==null||s.equals("—"))return "—";try{double n=Double.parseDouble(s);if(n>1024*1024*1024)return String.format(Locale.US,"%.2f GB",n/1024/1024/1024);if(n>1024*1024)return String.format(Locale.US,"%.2f GB",n/1024/1024);if(n>1024)return String.format(Locale.US,"%.2f MB",n/1024);return s;}catch(Exception e){return s;}}
    private double parseDouble(String s,double d){try{return Double.parseDouble(s.replace("dBm","").replace("dB","").trim());}catch(Exception e){return d;}}

    private class SignalGauge extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);RectF oval=new RectF();String name,sub,unit;double value,min,max;
        SignalGauge(android.content.Context c,String n,String s,double v,double mn,double mx,String u){super(c);name=n;sub=s;value=v;min=mn;max=mx;unit=u;p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h*.52f;float radius=Math.min(w*.40f,h*.38f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(13));p.setStrokeCap(Paint.Cap.ROUND);oval.set(cx-radius,cy-radius,cx+radius,cy+radius);
            drawArc(c,Color.rgb(244,67,54),180,42);drawArc(c,Color.rgb(255,193,7),222,54);drawArc(c,green(),276,84);
            double pct=signalPercent(value,min,max);int color=signalColor(value,name);float sweep=(float)(Math.max(0,Math.min(1,pct/100))*180);p.setColor(color);p.setStrokeWidth(dp(4));c.drawArc(oval,180,sweep,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(13));c.drawText(name,cx,dp(18),p);p.setTextSize(dp(9));p.setColor(Color.LTGRAY);c.drawText(sub,cx,dp(34),p);
            String val=Double.isNaN(value)?"—":formatSignal(value);p.setColor(Color.WHITE);p.setTextSize(dp(23));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText(val,cx,cy+dp(7),p);p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(dp(10));p.setColor(Color.LTGRAY);c.drawText(unit,cx,cy+dp(25),p);
            p.setColor(color);p.setTextSize(dp(15));c.drawText(Math.round(pct)+"%",cx,cy+dp(52),p);p.setTextSize(dp(9));c.drawText(signalLabel(value,name),cx,cy+dp(67),p);
        }
        private void drawArc(Canvas c,int color,float start,float sweep){p.setColor(color);c.drawArc(oval,start,sweep,false,p);}
        private double signalPercent(double v,double mn,double mx){if(Double.isNaN(v))return 0;return Math.max(0,Math.min(100,(v-mn)/(mx-mn)*100));}
        private int signalColor(double v,String n){if(Double.isNaN(v))return Color.GRAY;if(n.equals("RSRP"))return v>=-90?green():v>=-105?yellow():red();if(n.equals("RSRQ"))return v>=-10?green():v>=-15?yellow():red();return v>=20?green():v>=5?yellow():red();}
        private String signalLabel(double v,String n){if(Double.isNaN(v))return "لا توجد قراءة";int col=signalColor(v,n);return col==green()?"جيدة":col==yellow()?"متوسطة":"ضعيفة";}
        private String formatSignal(double v){return Math.abs(v-Math.round(v))<.05?String.format(Locale.US,"%.0f",v):String.format(Locale.US,"%.1f",v);}
    }

    private HttpResult request(String method,String path,String body,boolean write)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base()+path).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestMethod(method);c.setUseCaches(false);c.setRequestProperty("X-Requested-With","XMLHttpRequest");c.setRequestProperty("Accept","*/*");c.setRequestProperty("Referer",base()+"/");if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        if(write){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");if(!token.isEmpty())c.setRequestProperty("__RequestVerificationToken",firstToken(token));}if(body!=null){c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));c.getOutputStream().close();}
        int code=c.getResponseCode();String nt=c.getHeaderField("__RequestVerificationToken"),n1=c.getHeaderField("__RequestVerificationTokenone"),n2=c.getHeaderField("__RequestVerificationTokentwo"),sc=c.getHeaderField("Set-Cookie");if(nt!=null&&!nt.isEmpty()){String[]p=nt.trim().split("#");token=p[0].trim();tokenOne=p.length>1?p[1].trim():"";tokenTwo=p.length>2?p[2].trim():"";}if(n1!=null&&!n1.isEmpty()&&tokenOne.isEmpty())tokenOne=firstToken(n1);if(n2!=null&&!n2.isEmpty()&&tokenTwo.isEmpty())tokenTwo=firstToken(n2);if(sc!=null&&!sc.isEmpty())cookie=extractCookie(sc);InputStream in=code>=400?c.getErrorStream():c.getInputStream();String text=read(in);c.disconnect();return new HttpResult(code,text,sc,nt,n1,n2);
    }
    private String read(InputStream in)throws Exception{if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);r.close();return b.toString();}
    private static class HttpResult{int code;String body,setCookie,tokenHeader,tokenOne,tokenTwo;HttpResult(int c,String b,String s,String t,String o,String w){code=c;body=b;setCookie=s;tokenHeader=t;tokenOne=o;tokenTwo=w;}}
    private static String tag(String xml,String name){if(xml==null)return "";Matcher m=Pattern.compile("<"+Pattern.quote(name)+">(.*?)</"+Pattern.quote(name)+">",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(xml);return m.find()?m.group(1).trim():"";}
    private static String errorCode(String xml){String c=tag(xml,"code");return c.isEmpty()?"":c;}
    private static String firstNonEmpty(String a,String b){return a!=null&&!a.isEmpty()?a:(b==null?"":b);}
    private static String firstToken(String s){if(s==null)return "";String x=s.trim();int p=x.indexOf('#');if(p>=0)x=x.substring(0,p);return x.trim();}
    private static String extractCookie(String s){if(s==null)return "";Matcher m=Pattern.compile("(?:^|;\\s*)(SessionID=[^;]+)",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group(1):s.split(";",2)[0].trim();}
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private static String randomHex(int n){byte[]b=new byte[n];new SecureRandom().nextBytes(b);return hex(b);}
    private static byte[]hexToBytes(String s){if(s.length()%2!=0)throw new IllegalArgumentException("salt غير صالح");byte[]r=new byte[s.length()/2];for(int i=0;i<r.length;i++)r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return r;}
    private static String hex(byte[]b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    private static byte[]sha256(byte[]b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
    private static byte[]hmac(byte[]key,String msg)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(msg.getBytes(StandardCharsets.UTF_8));}
    private static byte[]pbkdf2(String pass,byte[]salt,int it)throws Exception{PBEKeySpec s=new PBEKeySpec(pass.toCharArray(),salt,it,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(s).getEncoded();}finally{s.clearPassword();}}
    private static String base64Sha256(String s)throws Exception{byte[]d=sha256(s.getBytes(StandardCharsets.UTF_8));String h=hex(d);return android.util.Base64.encodeToString(h.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){loginScreen();}
}
