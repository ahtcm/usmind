# -*- coding: utf-8 -*-
import sys
sys.path.insert(0, r'C:\Users\25750\Desktop\usmind\code\usmind')
import usmind_app

app = usmind_app.App()
app.root.update_idletasks()
app.root.update()

def walk(w, depth=0):
    try:
        kids = w.winfo_children()
    except Exception:
        return
    for k in kids:
        cls = k.winfo_class()
        txt = ''
        try:
            if cls in ('TEntry', 'Entry'):
                txt = k.get()
            elif cls in ('TLabel', 'Label'):
                txt = k.cget('text')
        except Exception:
            pass
        if cls in ('TEntry', 'Entry') or (txt and 'Ollama' in txt):
            gi = k.grid_info() if k.winfo_manager() == 'grid' else {}
            print('  ' * depth + f'{cls} text={txt!r} manager={k.winfo_manager()} grid={gi.get("column")}')
        walk(k, depth + 1)

print('=== AI 页内容 ===')
walk(app.f_ai)
app.root.destroy()
print('DONE')
