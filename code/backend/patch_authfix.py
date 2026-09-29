# -*- coding: utf-8 -*-
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\AuthServiceImpl.java'
s = open(p, encoding='utf-8').read()

old1 = """    @Override
    public Long register(com.usmind.dto.RegisterRequest req) {"""
new1 = """    public Long register(com.usmind.dto.RegisterRequest req) {"""
assert old1 in s, 'OVERRIDE NOT FOUND'
s = s.replace(old1, new1, 1)

old2 = """        u.setNickname(nickname == null || nickname.isBlank() ? username : nickname);"""
new2 = """        u.setNickname(req.getNickname() == null || req.getNickname().isBlank() ? username : req.getNickname());"""
assert old2 in s, 'NICKNAME NOT FOUND'
s = s.replace(old2, new2, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('authfix OK')
