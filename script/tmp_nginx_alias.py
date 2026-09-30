# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\deploy\nginx\nginx.conf'
s = io.open(p, encoding='utf-8').read()

# 1) 80 server /admin/：alias -> root
old1 = '''    # 管理后台（静态，base=/admin/）
    location /admin/ {
        alias /usr/share/nginx/html/admin/;
        index index.html;
        try_files $uri $uri/ /admin/index.html;'''
new1 = '''    # 管理后台（静态，base=/admin/）
    location /admin/ {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /admin/index.html;'''
assert s.count(old1) == 1, 'a1=' + str(s.count(old1))
s = s.replace(old1, new1)

# 2) 8867 server /admin/：alias -> root
old2 = '''    location /admin/ {
        alias /usr/share/nginx/html/admin/;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }'''
new2 = '''    location /admin/ {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }'''
assert s.count(old2) == 1, 'a2=' + str(s.count(old2))
s = s.replace(old2, new2)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('NGINX CONF OK')
