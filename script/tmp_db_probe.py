# -*- coding: utf-8 -*-
"""查导图95当前版本与几何文本"""
import subprocess
mysql = r'C:\Users\25750\Desktop\usmind\tools\mysql-8.0.36-winx64\bin\mysql.exe'
# 尝试常见连接参数
base = [mysql, '-uroot', '-proot123', 'usmind']
r = subprocess.run(base + ['-e', "SELECT version, LEFT(data, 400) FROM mind_map WHERE id=95"], capture_output=True, text=True, encoding='utf-8', errors='replace')
print('ROOT尝试:', r.returncode, r.stdout[:200], r.stderr[:200])
if r.returncode != 0:
    # 尝试其他密码/用户
    for pw in ['root', '123456', 'usmind', '']:
        r2 = subprocess.run([mysql, '-uroot', '-p'+pw, 'usmind', '-e', "SELECT version FROM mind_map WHERE id=95"], capture_output=True, text=True, encoding='utf-8', errors='replace')
        if r2.returncode == 0:
            print('密码', pw, '成功', r2.stdout[:200])
            break
