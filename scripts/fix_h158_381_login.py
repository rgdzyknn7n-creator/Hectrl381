from pathlib import Path
import re

p = Path('app/src/main/java/com/hectrl381/router/MainActivity.java')
s = p.read_text(encoding='utf-8')

login = r'''    private void login() throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            try{
                initSession();
                HttpResult state=request("GET","/api/user/state-login",null,false);
                String st=firstNonEmpty(tag(state.body,"State"),tag(state.body,"state"));
                if("0".equals(st)){
                    String u=firstNonEmpty(tag(state.body,"Username"),tag(state.body,"username"));
                    if(u.isEmpty()||username.equalsIgnoreCase(u))return;
                }

                try{
                    scramLogin();
                    verifyLoggedIn();
                    return;
                }catch(Exception scramError){
                    last=scramError;
                }

                try{
                    initSession();
                    legacyLogin();
                    verifyLoggedIn();
                    return;
                }catch(Exception legacyError){
                    last=legacyError;
                }
            }catch(Exception e){
                last=e;
            }
        }
        throw last==null?new Exception("تعذر تسجيل الدخول"):last;
    }

'''

pattern = r'    private void login\(\) throws Exception\{.*?    private void scramLogin\(\) throws Exception\{'
replacement = login + '    private void scramLogin() throws Exception{'
s, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit('login block not found')

old = 'String hashed=base64Sha256(password);String passwordValue=base64Sha256(username+hashed+token);'
new = 'String hashed=base64Sha256(hexSha256(password));String passwordValue=base64Sha256(username+hashed+token);'
if old in s:
    s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('H158-381 login fallback applied')
