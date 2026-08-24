from pathlib import Path
import re

p = Path('app/src/main/java/com/hectrl381/router/MainActivity.java')
s = p.read_text(encoding='utf-8')

# H158-381 uses Huawei password_type=4. Do not call state-login between
# SesTokInfo and login: some firmware revisions rotate/reject the CSRF token.
login = r'''    private void login() throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            try{
                initSession();
                legacyLogin();
                verifyLoggedIn();
                return;
            }catch(Exception e){
                last=e;
                String msg=e.getMessage()==null?"":e.getMessage();
                if(msg.contains("125003")||msg.contains("125002")||msg.contains("125001")||msg.toLowerCase(Locale.US).contains("token")){
                    continue;
                }
                throw e;
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

# Keep Huawei's WebUI-10 password encoding exactly as used by current
# Huawei HiLink implementations: URL-safe Base64 over the hex SHA-256.
# Do not change NO_WRAP|URL_SAFE to standard Base64.

# Keep the canonical type-4 password derivation in legacyLogin().
old = 'String hashed=base64Sha256(hexSha256(password));String passwordValue=base64Sha256(username+hashed+token);'
new = 'String hashed=base64Sha256(password);String passwordValue=base64Sha256(username+hashed+token);'
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('H158-381 login fix applied')
