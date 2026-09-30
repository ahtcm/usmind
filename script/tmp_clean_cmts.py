# -*- coding: utf-8 -*-
import subprocess
sql = "DELETE FROM node_comment WHERE id IN (65,66,67,68); SELECT COUNT(*) AS c FROM node_comment WHERE content LIKE '%验证%';"
p = subprocess.run([r'C:\Users\25750\Desktop\usmind\tools\mysql-8.0.36-winx64\bin\mysql.exe',
                    '-uroot', '-proot', 'usmind_db', '-e', sql],
                   capture_output=True, text=True)
print(p.stdout)
if p.stderr:
    print('ERR:', p.stderr[:300])
