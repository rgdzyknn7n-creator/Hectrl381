from pathlib import Path
import re

p = Path('app/src/main/java/com/hectrl381/router/MainActivity.java')
s = p.read_text(encoding='utf-8')

# 1) Persist credentials locally and automatically create a fresh Huawei session
# after the Android process is restarted. The router session/token itself cannot
# reliably survive an app restart, so we persist only the local credentials.
s = s.replace(
    'private String cookie = "", token = "", tokenOne = "", tokenTwo = "";\n',
    'private String cookie = "", token = "", tokenOne = "", tokenTwo = "";\n    private android.content.SharedPreferences prefs;\n',
    1,
)

s = s.replace(
    '@Override public void onCreate(Bundle state){super.onCreate(state);loginScreen();}',
    '''@Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("router_manager",MODE_PRIVATE);
        router=prefs.getString("router",router);
        username=prefs.getString("username",username);
        password=prefs.getString("password","");
        if(!password.isEmpty()) autoConnectSaved(); else loginScreen();
    }''',
    1,
)

s = s.replace(
    'username=userInput.getText().toString().trim();password=passInput.getText().toString();\n',
    '''username=userInput.getText().toString().trim();password=passInput.getText().toString();
        saveCredentials();
''',
    1,
)

needle = '    private String base(){return "http://"+router;}\n'
insert = '''    private void saveCredentials(){
        if(prefs!=null) prefs.edit().putString("router",router).putString("username",username).putString("password",password).apply();
    }
    private void autoConnectSaved(){
        setContentView(tv("جاري الاتصال المحفوظ بالراوتر...",16));
        io.execute(()->{
            try{
                login();
                runOnUiThread(()->{dashboard();startAutoRefresh();});
            }catch(Exception e){
                runOnUiThread(()->{loginScreen();toast("انتهت جلسة الراوتر، أدخل كلمة المرور مرة أخرى: "+e.getMessage());});
            }
        });
    }

'''
if needle not in s:
    raise SystemExit('base needle not found')
s = s.replace(needle, needle + insert, 1)

# 2) Replace the refresh implementation with H158-381 tolerant endpoint/field aliases.
new_refresh = r'''    private void refreshData(){
        if(refreshInFlight)return;
        refreshInFlight=true;
        io.execute(()->{
            try{
                Map<String,String> m=new LinkedHashMap<>();
                String status=getQuiet("/api/monitoring/status");
                String signal=getQuiet("/api/device/signal");
                String signal2=getQuiet("/api/monitoring/signal");
                String plmn=getQuiet("/api/net/current-plmn");
                String traffic=getQuiet("/api/monitoring/traffic-statistics");
                String netmode=getQuiet("/api/net/net-mode");
                String info=getQuiet("/api/device/information");

                alias(m,signal,"rsrp","rsrp","RSRP","lte_rsrp","LTE_RSRP");
                alias(m,signal,"rsrq","rsrq","RSRQ","lte_rsrq","LTE_RSRQ");
                alias(m,signal,"sinr","sinr","SINR","lte_sinr","LTE_SINR");
                alias(m,signal,"rssi","rssi","RSSI","lte_rssi","LTE_RSSI");
                alias(m,signal,"nrrsrp","nrrsrp","NRRSRP","nr_rsrp","NR_RSRP","5g_rsrp");
                alias(m,signal,"nrrsrq","nrrsrq","NRRSRQ","nr_rsrq","NR_RSRQ","5g_rsrq");
                alias(m,signal,"nrsinr","nrsinr","NRSINR","nr_sinr","NR_SINR","5g_sinr");
                alias(m,signal,"nrrssi","nrrssi","NRRSSI","nr_rssi","NR_RSSI","5g_rssi");
                alias(m,signal,"band","band","Band","CurrentBand","LTEBand","NetworkBand");
                alias(m,signal,"pci","pci","PCI","cell_pci","PCellID");
                alias(m,signal,"cell_id","cell_id","CellID","cellid","CellId","Cell_ID");
                alias(m,signal,"enodeb_id","enodeb_id","ENodeB","eNodeB","enodeb","EnodeBID");
                alias(m,signal,"nrearfcn","nrearfcn","NRARFCN","nr_earfcn","NREarfcn");
                alias(m,signal,"lteearfcn","lteearfcn","EARFCN","earfcn","LTEEARFCN");

                // Some H158-381 firmware exposes the same values through monitoring/status.
                aliasIfMissing(m,status,"rsrp","rsrp","RSRP","lte_rsrp","LTE_RSRP");
                aliasIfMissing(m,status,"rsrq","rsrq","RSRQ","lte_rsrq","LTE_RSRQ");
                aliasIfMissing(m,status,"sinr","sinr","SINR","lte_sinr","LTE_SINR");
                aliasIfMissing(m,status,"nrrsrp","nrrsrp","NRRSRP","nr_rsrp","NR_RSRP");
                aliasIfMissing(m,status,"nrrsrq","nrrsrq","NRRSRQ","nr_rsrq","NR_RSRQ");
                aliasIfMissing(m,status,"nrsinr","nrsinr","NRSINR","nr_sinr","NR_SINR");
                aliasIfMissing(m,status,"band","band","Band","CurrentBand","LTEBand","NetworkBand");
                aliasIfMissing(m,status,"pci","pci","PCI","PCellID");
                aliasIfMissing(m,status,"cell_id","cell_id","CellID","cellid","CellId");
                aliasIfMissing(m,status,"enodeb_id","enodeb_id","ENodeB","eNodeB","enodeb","EnodeBID");

                alias(m,signal2,"rsrp","rsrp","RSRP","lte_rsrp","LTE_RSRP");
                alias(m,signal2,"rsrq","rsrq","RSRQ","lte_rsrq","LTE_RSRQ");
                alias(m,signal2,"sinr","sinr","SINR","lte_sinr","LTE_SINR");
                alias(m,signal2,"nrrsrp","nrrsrp","NRRSRP","nr_rsrp","NR_RSRP");
                alias(m,signal2,"nrrsrq","nrrsrq","NRRSRQ","nr_rsrq","NR_RSRQ");
                alias(m,signal2,"nrsinr","nrsinr","NRSINR","nr_sinr","NR_SINR");

                alias(m,plmn,"FullName","FullName","NetworkName","networkname","ShortName","Operator");
                alias(m,plmn,"Numeric","Numeric","PLMN","plmn","numeric");
                alias(m,plmn,"Rat","Rat","RAT","CurrentNetworkType","NetworkType");
                alias(m,status,"ConnectionStatus","ConnectionStatus","connectionstatus");
                alias(m,status,"CurrentNetworkType","CurrentNetworkType","currentnetworktype","NetworkType");
                alias(m,status,"CurrentWifiUser","CurrentWifiUser","currentwifiuser","WifiUser");

                alias(m,traffic,"CurrentDownloadRate","CurrentDownloadRate","currentdownloadrate","DownloadRate");
                alias(m,traffic,"CurrentUploadRate","CurrentUploadRate","currentuploadrate","UploadRate");
                alias(m,traffic,"TotalDownload","TotalDownload","totaldownload");
                alias(m,traffic,"TotalUpload","TotalUpload","totalupload");
                alias(m,netmode,"NetworkMode","NetworkMode","networkmode");
                alias(m,netmode,"LTEBand","LTEBand","lteband");
                alias(m,netmode,"NRBand","NRBand","nrbands","nrbands");
                alias(m,netmode,"NetworkBand","NetworkBand","networkband");
                alias(m,info,"DeviceName","DeviceName","devicename");
                alias(m,info,"SoftwareVersion","SoftwareVersion","softwareversion");

                lastData=m;
                runOnUiThread(()->{dashboardWith(m);if(autoRefresh)refreshHandler.postDelayed(refreshLoop,1000);});
            }catch(Exception e){
                // If Huawei invalidates the local session, transparently rebuild it.
                try{
                    if(isSessionError(e)){
                        login();
                        if(autoRefresh)refreshHandler.postDelayed(refreshLoop,150);
                    }else if(autoRefresh)refreshHandler.postDelayed(refreshLoop,1000);
                }catch(Exception ignored){
                    if(autoRefresh)refreshHandler.postDelayed(refreshLoop,1000);
                }
            }finally{refreshInFlight=false;}
        });
    }

    private boolean isSessionError(Exception e){
        String x=e.getMessage()==null?"":e.getMessage().toLowerCase(Locale.US);
        return x.contains("125003")||x.contains("125002")||x.contains("125001")||x.contains("session")||x.contains("token");
    }
    private String getQuiet(String p){try{return get(p);}catch(Exception e){return "";}}
    private void alias(Map<String,String>m,String xml,String canonical,String...names){
        if(xml==null||xml.isEmpty())return;
        for(String n:names){String x=tagCI(xml,n);if(!x.isEmpty()){m.put(canonical,x);return;}}
    }
    private void aliasIfMissing(Map<String,String>m,String xml,String canonical,String...names){
        if(m.get(canonical)==null||m.get(canonical).isEmpty())alias(m,xml,canonical,names);
    }

'''
pattern = r'    private void refreshData\(\)\{.*?    private void add\('
s, n = re.subn(pattern, new_refresh + '    private void add(', s, count=1, flags=re.S)
if n != 1:
    raise SystemExit('refresh block not found')

p.write_text(s, encoding='utf-8')
print('session persistence and H158-381 reading aliases applied')
