# -*- coding: utf-8 -*-
import os, sys, json, time, threading, urllib.request
sys.path.insert(0, r'C:\Users\25750\Desktop\usmind\code\usmind')
os.chdir(r'C:\Users\25750\Desktop\usmind\code\usmind')

from usmind_log import init_logger, log
init_logger()
from usmind_server import Server, handle_save
from usmind_sync import build_repo

# 1. 启动服务
srv = Server(8866)
assert srv.start(), 'server start fail'
time.sleep(0.5)

# 2. health
r = urllib.request.urlopen('http://127.0.0.1:8866/api/health')
print('health:', json.loads(r.read()))

# 3. 首页可访问
r = urllib.request.urlopen('http://127.0.0.1:8866/')
html = r.read().decode('utf-8')
print('index ok:', 'USMIND' in html or '<!DOCTYPE' in html, 'len=', len(html))

# 4. 模拟 web 端保存
payload = {
    'tabs': [{
        'name': 'EXE测试', 'layout': 'logicalStructure', 'theme': 'dark2',
        'data': {'root': {'data': {'text': '中心主题'}, 'children': [{'data': {'text': '子主题A'}, 'children': []}]}},
        'viewData': None
    }],
    'docIndex': 0
}
req = urllib.request.Request('http://127.0.0.1:8866/api/save',
    data=json.dumps(payload).encode('utf-8'), headers={'Content-Type': 'application/json'})
r = urllib.request.urlopen(req)
print('save resp:', r.read().decode('utf-8'))

# 5. 验证仓库产物
repo = r'C:\Users\25750\Desktop\usmind\usmind'
time.sleep(1)
print('index exists:', os.path.isfile(os.path.join(repo, 'index.html')))
print('assets exists:', os.path.isdir(os.path.join(repo, 'assets')))
sh = open(os.path.join(repo, 'share.html'), encoding='utf-8').read()
print('share injected:', '__USMIND_INIT__' in sh, 'EXE测试' in sh)
print('data exists:', os.path.isfile(os.path.join(repo, 'data', 'usmind.json')))
# 服务器 serve share.html(仓库产物)
r = urllib.request.urlopen('http://127.0.0.1:8866/share.html')
print('share served:', 'EXE测试' in r.read().decode('utf-8'))
# git 状态
import subprocess
st = subprocess.run(['git', 'status', '--short'], cwd=repo, capture_output=True, text=True)
print('git status lines:', len(st.stdout.strip().splitlines()) if st.stdout.strip() else 0)

# 等防抖 push(无 token 预期失败日志)
time.sleep(6.5)
srv.stop()
print('DONE')
