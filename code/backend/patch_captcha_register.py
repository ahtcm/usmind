# -*- coding: utf-8 -*-
# 修复注册验证码链路：Controller 需把 captchaUuid/captchaCode 传给 service
import io, sys

base = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind'

# 1) AuthService 接口增加 RegisterRequest 重载
p1 = base + r'\service\AuthService.java'
s1 = io.open(p1, encoding='utf-8').read()
old1 = "    Long register(String username, String password, String nickname);"
new1 = "    Long register(String username, String password, String nickname);\n\n    Long register(com.usmind.dto.RegisterRequest req);"
if old1 not in s1:
    print('P1 NOT FOUND'); sys.exit(1)
s1 = s1.replace(old1, new1, 1)
io.open(p1, 'w', encoding='utf-8', newline='\n').write(s1)
print('P1 OK')

# 2) AuthServiceImpl 3 参方法改调 req 版本时保留验证码字段（构造双参 req 即可），并给 req 版本加 @Override
p2 = base + r'\service\impl\AuthServiceImpl.java'
s2 = io.open(p2, encoding='utf-8').read()
old2 = """    @Override
    public Long register(String username, String password, String nickname) {
        return register(new com.usmind.dto.RegisterRequest() {{
            setUsername(username);
            setPassword(password);
            setNickname(nickname);
        }});
    }

    public Long register(com.usmind.dto.RegisterRequest req) {"""
new2 = """    @Override
    public Long register(String username, String password, String nickname) {
        return register(new com.usmind.dto.RegisterRequest() {{
            setUsername(username);
            setPassword(password);
            setNickname(nickname);
        }});
    }

    @Override
    public Long register(com.usmind.dto.RegisterRequest req) {"""
if old2 not in s2:
    print('P2 NOT FOUND'); sys.exit(1)
s2 = s2.replace(old2, new2, 1)
io.open(p2, 'w', encoding='utf-8', newline='\n').write(s2)
print('P2 OK')

# 3) AuthController 注册接口改传完整 req
p3 = base + r'\controller\AuthController.java'
s3 = io.open(p3, encoding='utf-8').read()
old3 = "        Long id = authService.register(req.getUsername(), req.getPassword(), req.getNickname());"
new3 = "        Long id = authService.register(req);"
if old3 not in s3:
    print('P3 NOT FOUND'); sys.exit(1)
s3 = s3.replace(old3, new3, 1)
io.open(p3, 'w', encoding='utf-8', newline='\n').write(s3)
print('P3 OK')
