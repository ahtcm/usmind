# -*- coding: utf-8 -*-
import io

# ===== start-backend.bat：加 LOG_PATH =====
p = r'C:\Users\25750\Desktop\usmind\script\bat\start-backend.bat'
s = io.open(p, encoding='utf-8').read()
old = 'java -jar "target\\usmind-backend.jar" --spring.profiles.active=dev --file.upload-dir="%ROOT%\\uploads"'
new = 'java -DLOG_PATH="%ROOT%\\logs" -jar "target\\usmind-backend.jar" --spring.profiles.active=dev --file.upload-dir="%ROOT%\\uploads"'
assert s.count(old) == 1, 'bat=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('BAT OK')

# ===== start-backend.sh：加 LOG_PATH =====
p2 = r'C:\Users\25750\Desktop\usmind\script\shell\start-backend.sh'
s2 = io.open(p2, encoding='utf-8').read()
old2 = '''exec "$JAVA_BIN" -jar "target/usmind-backend.jar" \\
     --spring.profiles.active=dev \\
     --file.upload-dir="$ROOT/uploads"'''
new2 = '''exec "$JAVA_BIN" -DLOG_PATH="$ROOT/logs" -jar "target/usmind-backend.jar" \\
     --spring.profiles.active=dev \\
     --file.upload-dir="$ROOT/uploads"'''
assert s2.count(old2) == 1, 'sh=' + str(s2.count(old2))
s2 = s2.replace(old2, new2)
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('SH OK')

# ===== start-monitor.bat：加 LOG_PATH =====
p3 = r'C:\Users\25750\Desktop\usmind\script\bat\start-monitor.bat'
s3 = io.open(p3, encoding='utf-8').read()
old3 = 'java -jar "target\\usmind-backend.jar" --spring.profiles.active=dev --file.upload-dir="%ROOT%\\uploads"'
new3 = 'java -DLOG_PATH="%ROOT%\\logs" -jar "target\\usmind-backend.jar" --spring.profiles.active=dev --file.upload-dir="%ROOT%\\uploads"'
assert s3.count(old3) == 1, 'mon=' + str(s3.count(old3))
s3 = s3.replace(old3, new3)
io.open(p3, 'w', encoding='utf-8', newline='').write(s3)
print('MONITOR OK')
