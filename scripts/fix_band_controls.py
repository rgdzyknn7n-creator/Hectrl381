from pathlib import Path
import re

p=Path('app/src/main/java/com/hectrl381/router/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Do not save an incorrect password before authentication succeeds.
s=s.replace('username=userInput.getText().toString().trim();password=passInput.getText().toString();\n        saveCredentials();', 'username=userInput.getText().toString().trim();password=passInput.getText().toString();', 1)
s=s.replace('if(password.isEmpty())throw new Exception("أدخل كلمة مرور الإدارة");login();runOnUiThread', 'if(password.isEmpty())throw new Exception("أدخل كلمة مرور الإدارة");login();saveCredentials();runOnUiThread', 1)

# Add a real LTE-band aggregation button to the dashboard without depending on
# the existing visual layout. It uses Huawei's standard /api/net/net-mode API.
needle='        scroll.addView(root);setContentView(scroll);'
button=r'''        Button bandButton=actionButton("📶 دمج ترددات 4G",Color.rgb(109,40,217));
        bandButton.setOnClickListener(v->showBandDialog());
        root.addView(bandButton,new LinearLayout.LayoutParams(-1,dp(54)));
'''
if needle in s and 'showBandDialog()' not in s:
    s=s.replace(needle,button+needle,1)

methods=r'''    private void showBandDialog(){
        final String[] bands={"B1","B3","B7","B8","B20","B28","B38","B40"};
        final int[] nums={1,3,7,8,20,28,38,40};
        boolean[] checked=new boolean[bands.length];
        try{
            String current=lastData.get("LTEBand");
            if(current!=null&&!current.isEmpty()){
                long mask=Long.parseUnsignedLong(current.trim().replace("0x","").replace("0X",""),16);
                for(int i=0;i<nums.length;i++)checked[i]=(mask&(1L<<(nums[i]-1)))!=0;
            }
        }catch(Exception ignored){}
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("اختيار ترددات 4G للدمج")
            .setMultiChoiceItems(bands,checked,null)
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("تطبيق",null)
            .create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                long mask=0;
                android.widget.ListView list=dialog.getListView();
                for(int i=0;i<nums.length;i++)if(list.isItemChecked(i))mask|=(1L<<(nums[i]-1));
                if(mask==0){toast("اختر ترددًا واحدًا على الأقل");return;}
                String lte=Long.toHexString(mask);
                setLteBands(lte,dialog);
            });
        });
        dialog.show();
    }

    private void setLteBands(String lteMask,AlertDialog dialog){
        io.execute(()->{
            try{
                String mode=lastData.get("NetworkMode");
                String networkBand=lastData.get("NetworkBand");
                if(mode==null||mode.isEmpty())mode="03";
                if(networkBand==null||networkBand.isEmpty())networkBand="3FFFFFFF";
                String body="<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><NetworkMode>"+xml(mode)+"</NetworkMode><NetworkBand>"+xml(networkBand)+"</NetworkBand><LTEBand>"+xml(lteMask)+"</LTEBand></request>";
                HttpResult r=request("POST","/api/net/net-mode",body,true);
                String err=errorCode(r.body);
                if(!err.isEmpty())throw new Exception("رمز الراوتر "+err);
                if(r.code>=400)throw new Exception("فشل تطبيق الترددات HTTP "+r.code);
                captureTokens(r);
                runOnUiThread(()->{dialog.dismiss();toast("تم تطبيق ترددات 4G: "+lteMask.toUpperCase(Locale.US));refreshData();});
            }catch(Exception e){
                runOnUiThread(()->toast("تعذر تطبيق الترددات: "+e.getMessage()));
            }
        });
    }

'''
# Insert before the first onBackPressed method, near the end of the class.
pos=s.rfind('    @Override public void onBackPressed()')
if pos<0: raise SystemExit('onBackPressed not found')
if 'private void showBandDialog()' not in s:
    s=s[:pos]+methods+s[pos:]

p.write_text(s,encoding='utf-8')
print('band controls applied')
