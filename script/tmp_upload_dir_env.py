# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\resources\application.yml'
s = io.open(p, encoding='utf-8').read()
old = '''file:
  upload-dir: ./uploads'''
new = '''file:
  # 上传目录：默认 ./uploads（相对工作目录），可用环境变量 USMIND_UPLOAD_DIR 覆盖（Docker 挂载卷）
  upload-dir: ${USMIND_UPLOAD_DIR:./uploads}'''
assert s.count(old) == 1, 'cnt=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('APPLICATION YML OK')
