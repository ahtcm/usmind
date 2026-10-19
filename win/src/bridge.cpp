// usmind 本地版 - WebView2 桥接实现
#include "bridge.h"
#include "native.h"

#include <objbase.h>
#include <oleauto.h>
#include <windows.h>
#include <cstdio>
#include <vector>
#include <sstream>

// 方法名表（dispid = 下标 + 1）
static const wchar_t* const kMethods[] = {
    L"getAppInfo",      // 1
    L"getSettings",     // 2
    L"saveSettings",    // 3
    L"getStartupFile",  // 4
    L"openWithDialog",  // 5
    L"openPath",        // 6
    L"saveTo",          // 7
    L"saveCurrent",     // 8
    L"hasCurrentFile",  // 9
    L"getHistory",      // 10
    L"restoreHistory",  // 11
    L"clearHistory",    // 12
    L"setProjectDir",   // 13（项目文件保存位置）
    L"registerAssoc",   // 14
    L"notifyDocumentState", // 15 （前端上报文件名变化，用于标题栏）
    L"pickFolder",      // 16 （目录选择，历史目录设置用）
    L"pickAnyFile",     // 17 （选任意文件：写入外置附件返回相对路径）
    L"openAttachment",  // 18 （附件用系统默认程序打开：base64 或 外置相对路径）
    L"writeAttachment", // 19 （写入外置附件 relPath+b64）
    L"openPathBinary",  // 20 （读任意文件→base64，自包含 zip 打开用）
    L"saveBinary",      // 21 （base64→写二进制文件，自包含 zip 保存用）
    L"saveSelfContained", // 22 （自包含 v3 保存：manifest + 媒体rel列表，二进制直拷）
    L"openSelfContained",  // 23 （自包含 v3 打开：拆 manifest + 媒体写回附件目录）
    L"restoreMainWindow",  // 24 （启动特效结束：小窗恢复为主窗口大小）
    L"toggleMaximize",     // 25 （窗口最大化/还原切换，前端全屏按钮用）
    L"minimizeWindow",     // 26 （窗口最小化）
    L"closeWindow",        // 27 （窗口关闭）
    L"startDragWindow"     // 28 （从 topBar 空白区拖动窗口）
};
static const int kMethodCount = sizeof(kMethods) / sizeof(kMethods[0]);

// 前端"打开文件"通知回调（由 main.cpp 设置）
void (*g_onFileOpenRequest)(const std::wstring& path) = nullptr;

// ---------- 小工具 ----------
static std::wstring utf8ToWide2(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), NULL, 0);
    std::wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &w[0], n);
    return w;
}
static std::string wideToUtf8_2(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), NULL, 0, NULL, NULL);
    std::string s(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, NULL, NULL);
    return s;
}
static std::string JsEscape(const std::wstring& w) {
    std::string s = wideToUtf8_2(w);
    std::string out;
    for (char c : s) {
        if (c == '"') out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c == '\n') out += "\\n";
        else if (c == '\r') out += "\\r";
        else if (c == '\t') out += "\\t";
        else out += c;
    }
    return out;
}
// 生成 JSON 返回（BSTR）：先按 UTF-8→UTF-16 的实际长度分配，避免中文字符转换后 BSTR 尾部残留空字符导致 JS JSON.parse 失败
static VARIANT MakeJsonResult(const std::string& json) {
    VARIANT v;
    VariantInit(&v);
    v.vt = VT_BSTR;
    int len = MultiByteToWideChar(CP_UTF8, 0, json.c_str(), (int)json.size(), NULL, 0);
    if (len <= 0) len = 1;
    v.bstrVal = SysAllocStringLen(NULL, (UINT)len);
    MultiByteToWideChar(CP_UTF8, 0, json.c_str(), (int)json.size(), v.bstrVal, len);
    return v;
}
static VARIANT MakeBoolResult(bool ok) {
    VARIANT v;
    VariantInit(&v);
    v.vt = VT_BSTR;
    std::string j = ok ? "{\"ok\":true}" : "{\"ok\":false}";
    int len = MultiByteToWideChar(CP_UTF8, 0, j.c_str(), (int)j.size(), NULL, 0);
    if (len <= 0) len = 1;
    v.bstrVal = SysAllocStringLen(NULL, (UINT)len);
    MultiByteToWideChar(CP_UTF8, 0, j.c_str(), (int)j.size(), v.bstrVal, len);
    return v;
}
// 取第 idx 个参数（DISPPARAMS 逆序存储）
static std::wstring ParamStr(DISPPARAMS* p, unsigned idx, const std::wstring& def = L"") {
    if (!p || idx >= p->cArgs) return def;
    VARIANT& v = p->rgvarg[p->cArgs - 1 - idx];
    if (v.vt == VT_BSTR && v.bstrVal) return std::wstring(v.bstrVal);
    if (v.vt == VT_I4) return std::to_wstring(v.lVal);
    if (v.vt == VT_R8) { wchar_t b[64]; swprintf(b, 64, L"%g", v.dblVal); return b; }
    if (v.vt == VT_EMPTY || v.vt == VT_NULL) return def;
    return def;
}
static int ParamInt(DISPPARAMS* p, unsigned idx, int def = 0) {
    if (!p || idx >= p->cArgs) return def;
    VARIANT& v = p->rgvarg[p->cArgs - 1 - idx];
    if (v.vt == VT_I4) return v.lVal;
    if (v.vt == VT_BSTR && v.bstrVal) return (int)wcstol(v.bstrVal, NULL, 10);
    return def;
}

// ---------- IDispatch ----------
UsmindBridge::UsmindBridge() : refCount_(1) {}
UsmindBridge::~UsmindBridge() {}

STDMETHODIMP UsmindBridge::QueryInterface(REFIID riid, void** ppv) {
    if (!ppv) return E_POINTER;
    *ppv = nullptr;
    if (riid == IID_IUnknown || riid == IID_IDispatch) {
        *ppv = this;
        AddRef();
        return S_OK;
    }
    return E_NOINTERFACE;
}
STDMETHODIMP_(ULONG) UsmindBridge::AddRef() { return ++refCount_; }
STDMETHODIMP_(ULONG) UsmindBridge::Release() {
    ULONG r = --refCount_;
    if (r == 0) delete this;
    return r;
}
STDMETHODIMP UsmindBridge::GetTypeInfoCount(UINT* p) { if (p) *p = 0; return S_OK; }
STDMETHODIMP UsmindBridge::GetTypeInfo(UINT, LCID, ITypeInfo** pp) { if (pp) *pp = nullptr; return E_NOTIMPL; }

STDMETHODIMP UsmindBridge::GetIDsOfNames(REFIID, LPOLESTR* names, UINT count, LCID, DISPID* ids) {
    if (!names || !ids || count == 0) return E_INVALIDARG;
    for (UINT i = 0; i < count; ++i) {
        ids[i] = DISPID_UNKNOWN;
        std::wstring n = names[i] ? names[i] : L"";
        for (int k = 0; k < kMethodCount; ++k) {
            if (n == kMethods[k]) { ids[i] = k + 1; break; }
        }
        if (ids[i] == DISPID_UNKNOWN) return DISP_E_UNKNOWNNAME;
    }
    return S_OK;
}

STDMETHODIMP UsmindBridge::Invoke(DISPID dispId, REFIID, LCID, WORD flags,
                                  DISPPARAMS* p, VARIANT* result,
                                  EXCEPINFO*, UINT*) {
    if (result) VariantInit(result);
    // 仅 DISPID_VALUE(0) 支持默认属性；方法 dispid 在 propertyget 时必须返回未找到，
    // 否则 WebView2 会把方法名解析成字符串属性，导致 JS 端报 "non-function"
    if (flags & DISPATCH_PROPERTYGET) {
        if (dispId == DISPID_VALUE && result) {
            *result = MakeBoolResult(true);
            return S_OK;
        }
        return DISP_E_MEMBERNOTFOUND;
    }
    if (dispId >= 1 && dispId <= kMethodCount) {
        DoInvoke((int)dispId, p, result);
        return S_OK;
    }
    return DISP_E_MEMBERNOTFOUND;
}

void UsmindBridge::DoInvoke(int method, DISPPARAMS* p, VARIANT* result) {
    using namespace usmind;
    switch (method) {
    case 1: { // getAppInfo
        std::string j = "{\"ok\":true,\"version\":\"" + wideToUtf8_2(appVersion()) + "\","
                        "\"projectDir\":\"" + JsEscape(projectDir()) + "\"}";
        if (result) *result = MakeJsonResult(j);
        break;
    }
    case 2: { // getSettings
        Settings s = loadSettings();
        std::string j = "{\"ok\":true,\"historyDir\":\"" + JsEscape(s.historyDir) + "\","
                        "\"defaultTheme\":\"" + JsEscape(s.defaultTheme) + "\","
                        "\"registerAssoc\":" + std::to_string(s.registerAssoc) + ","
                        "\"lastFile\":\"" + JsEscape(s.lastFile) + "\"}";
        if (result) *result = MakeJsonResult(j);
        break;
    }
    case 3: { // saveSettings(json)
        std::wstring json = ParamStr(p, 0);
        // 前端传入 {"historyDir":..,"defaultTheme":..,"registerAssoc":..}
        Settings s = loadSettings();
        std::string j8 = wideToUtf8_2(json);
        auto has = [&](const char* k) { return j8.find(std::string("\"") + k + "\"") != std::string::npos; };
        // 简式解析（原生层有完整 jsonGet；这里直接让 native 处理关键字段）
        // 提取字符串值
        auto grab = [&](const char* key) -> std::wstring {
            std::string k = std::string("\"") + key + "\":\"";
            size_t p0 = j8.find(k);
            if (p0 == std::string::npos) return L"";
            p0 += k.size();
            std::string out;
            while (p0 < j8.size() && j8[p0] != '"') {
                if (j8[p0] == '\\' && p0 + 1 < j8.size()) { out += j8[p0 + 1]; p0 += 2; continue; }
                out += j8[p0++];
            }
            return utf8ToWide2(out);
        };
        auto grabInt = [&](const char* key, int defv) -> int {
            std::string k = std::string("\"") + key + "\":";
            size_t p0 = j8.find(k);
            if (p0 == std::string::npos) return defv;
            p0 += k.size();
            return (int)strtol(j8.c_str() + p0, NULL, 10);
        };
        if (has("historyDir")) s.historyDir = grab("historyDir");
        if (has("defaultTheme")) s.defaultTheme = grab("defaultTheme");
        if (has("registerAssoc")) s.registerAssoc = grabInt("registerAssoc", s.registerAssoc);
        if (has("lastFile")) s.lastFile = grab("lastFile");
        saveSettings(s);
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 4: { // getStartupFile
        if (result) {
            std::string j = "{\"ok\":true,\"path\":\"" + JsEscape(g_startupFile) + "\"}";
            *result = MakeJsonResult(j);
        }
        break;
    }
    case 5: { // openWithDialog → {ok,path,name,data}（zip 自包含包 → {ok,isZip,path,name,b64}）
        std::wstring path;
        std::string data;
        bool ok = pickOpenFile(path, data);
        if (result) {
            if (ok) {
                bool isZip = data.size() >= 4 && (unsigned char)data[0] == 0x50 && (unsigned char)data[1] == 0x4B
                             && (unsigned char)data[2] == 0x03 && (unsigned char)data[3] == 0x04;
                if (isZip) {
                    std::string j = "{\"ok\":true,\"isZip\":true,\"path\":\"" + JsEscape(path) + "\","
                                    "\"name\":\"" + JsEscape(fileNameOf(path)) + "\","
                                    "\"b64\":\"" + base64Encode(data) + "\"}";
                    *result = MakeJsonResult(j);
                } else {
                    std::string j = "{\"ok\":true,\"path\":\"" + JsEscape(path) + "\","
                                    "\"name\":\"" + JsEscape(fileNameOf(path)) + "\","
                                    "\"data\":" + (data.empty() ? std::string("null") : std::string("\"") + JsEscape(utf8ToWide2(data)) + "\"") + "}";
                    *result = MakeJsonResult(j);
                }
            } else {
                *result = MakeJsonResult("{\"ok\":false}");
            }
        }
        break;
    }
    case 6: { // openPath(path) → {ok,path,name,data}（zip 自包含包 → {ok,isZip,path,name,b64}）
        std::wstring path = ParamStr(p, 0);
        std::string data;
        bool ok = !path.empty() && readFile(path, data);
        if (result) {
            if (ok) {
                currentPath = path; currentName = fileNameOf(path); // 打开即记住工作文件
                bool isZip = data.size() >= 4 && (unsigned char)data[0] == 0x50 && (unsigned char)data[1] == 0x4B
                             && (unsigned char)data[2] == 0x03 && (unsigned char)data[3] == 0x04;
                if (isZip) {
                    std::string j = "{\"ok\":true,\"isZip\":true,\"path\":\"" + JsEscape(path) + "\","
                                    "\"name\":\"" + JsEscape(fileNameOf(path)) + "\","
                                    "\"b64\":\"" + base64Encode(data) + "\"}";
                    *result = MakeJsonResult(j);
                } else {
                    std::string j = "{\"ok\":true,\"path\":\"" + JsEscape(path) + "\","
                                    "\"name\":\"" + JsEscape(fileNameOf(path)) + "\","
                                    "\"data\":" + std::string("\"") + JsEscape(utf8ToWide2(data)) + "\"}";
                    *result = MakeJsonResult(j);
                }
            } else {
                *result = MakeJsonResult("{\"ok\":false,\"error\":\"无法读取文件\"}");
            }
        }
        break;
    }
    case 7: { // saveTo(path, content, name) → {ok}
        std::wstring path = ParamStr(p, 0);
        std::wstring content = ParamStr(p, 1);
        bool ok = false;
        if (!path.empty()) {
            ok = writeFile(path, wideToUtf8_2(content));
        }
        if (result) *result = MakeBoolResult(ok);
        break;
    }
    case 8: { // saveCurrent(content, name) → {ok, path?, name?}
        std::wstring content = ParamStr(p, 0);
        std::wstring name = ParamStr(p, 1);
        if (!currentPath.empty()) {
            bool ok = writeFile(currentPath, wideToUtf8_2(content));
            if (result) {
                std::string j = ok ? "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}"
                                   : "{\"ok\":false}";
                *result = MakeJsonResult(j);
            }
        } else {
            // 未关联文件：优先静默保存到项目目录（同名才弹框确认）
            std::wstring nm = name.empty() ? L"未命名导图" : name;
            std::wstring target = projectDir() + L"\\" + nm;
            if (fileExists(target)) {
                // 项目目录已有同名文件：弹框确认（另存为）
                std::wstring path;
                if (pickSaveFile(nm, path)) {
                    bool ok = writeFile(path, wideToUtf8_2(content));
                    if (ok) { currentPath = path; currentName = fileNameOf(path); }
                    if (result) {
                        std::string j = ok ? "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}"
                                           : "{\"ok\":false}";
                        *result = MakeJsonResult(j);
                    }
                } else {
                    if (result) *result = MakeJsonResult("{\"ok\":false,\"cancel\":true}");
                }
            } else {
                // 无同名：直接保存到项目目录
                ensureProjectDir();
                bool ok = writeFile(target, wideToUtf8_2(content));
                if (ok) { currentPath = target; currentName = fileNameOf(target); }
                if (result) {
                    std::string j = ok ? "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}"
                                       : "{\"ok\":false}";
                    *result = MakeJsonResult(j);
                }
            }
        }
        break;
    }
    case 9: { // hasCurrentFile
        if (result) {
            std::string j = "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}";
            *result = MakeJsonResult(j);
        }
        break;
    }
    case 10: { // getHistory —— 已移除历史版本功能
        if (result) *result = MakeJsonResult("{\"ok\":false}");
        break;
    }
    case 11: { // restoreHistory —— 已移除历史版本功能
        if (result) *result = MakeJsonResult("{\"ok\":false}");
        break;
    }
    case 12: { // clearHistory —— 已移除历史版本功能
        if (result) *result = MakeBoolResult(false);
        break;
    }
    case 13: { // setProjectDir(dir) → {ok,dir}
        std::wstring dir = ParamStr(p, 0);
        bool ok = setProjectDir(dir);
        if (result) {
            std::string j = ok ? "{\"ok\":true,\"dir\":\"" + JsEscape(projectDir()) + "\"}" : "{\"ok\":false}";
            *result = MakeJsonResult(j);
        }
        break;
    }
    case 14: { // registerAssoc
        registerFileAssoc();
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 15: { // notifyDocumentState(name) —— 仅更新标题（前端已显示，无需处理）
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 16: { // pickFolder → {ok, dir}
        std::wstring dir;
        bool ok = pickFolder(dir);
        if (result) {
            std::string j = ok ? "{\"ok\":true,\"dir\":\"" + JsEscape(dir) + "\"}" : "{\"ok\":false}";
            *result = MakeJsonResult(j);
        }
        break;
    }
    case 17: { // pickAnyFile → {ok, name, relPath?, b64?}（优先直接复制到附件目录，大文件稳）
        std::wstring name, rel;
        std::string b64;
        std::string j;
        if (copyAttachmentFile(name, rel)) {
            j = "{\"ok\":true,\"name\":\"" + JsEscape(name) + "\",\"relPath\":\"" + JsEscape(rel) + "\"}";
        } else {
            bool ok = pickAnyFile(name, b64);
            if (ok && !b64.empty()) {
                wchar_t ts[40] = { 0 };
                swprintf(ts, 40, L"%lld", (long long)GetTickCount64());
                std::wstring rel2 = L"attachments/att_" + std::wstring(ts) + L"_" + fileNameOf(name);
                if (writeAttachment(rel2, b64)) {
                    j = "{\"ok\":true,\"name\":\"" + JsEscape(name) + "\",\"relPath\":\"" + JsEscape(rel2) + "\"}";
                } else {
                    j = "{\"ok\":true,\"name\":\"" + JsEscape(name) + "\",\"b64\":\"" + b64 + "\"}";
                }
            } else j = "{\"ok\":false}";
        }
        if (result) *result = MakeJsonResult(j);
        break;
    }
    case 18: { // openAttachment(b64, name) → {ok, path}
        std::wstring b64 = ParamStr(p, 0);
        std::wstring name = ParamStr(p, 1);
        bool ok = openAttachment(wideToUtf8_2(b64), name);
        if (result) {
            *result = MakeJsonResult(ok ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"无法打开附件\"}");
        }
        break;
    }
    case 19: { // writeAttachment(relPath, b64) → {ok}
        std::wstring rel = ParamStr(p, 0);
        std::wstring b64 = ParamStr(p, 1);
        bool ok = writeAttachment(rel, wideToUtf8_2(b64));
        if (result) {
            *result = MakeJsonResult(ok ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"写入附件失败\"}");
        }
        break;
    }
    case 20: { // openPathBinary(path) → {ok, b64}（自包含 zip 打开）
        std::wstring path = ParamStr(p, 0);
        std::string b64;
        bool ok = !path.empty() && readFileB64(path, b64);
        if (result) {
            if (ok) {
                std::string j = "{\"ok\":true,\"b64\":\"" + b64 + "\"}";
                *result = MakeJsonResult(j);
            } else {
                *result = MakeJsonResult("{\"ok\":false,\"error\":\"无法读取文件\"}");
            }
        }
        break;
    }
    case 21: { // saveBinary(b64, path, name) → {ok, path?}（自包含 zip 保存；.json 导出用任意扩展名）
        std::wstring b64 = ParamStr(p, 0);
        std::wstring path = ParamStr(p, 1);
        std::wstring name = ParamStr(p, 2);
        bool ok = false;
        if (!path.empty()) {
            ok = writeFileB64(path, wideToUtf8_2(b64));
        } else {
            std::wstring sp;
            bool isJson = name.size() >= 5 && name.compare(name.size() - 5, 5, L".json") == 0;
            if (isJson) {
                if (pickSaveFileAny(L"", name, sp)) {
                    ok = writeFileB64(sp, wideToUtf8_2(b64));
                } else {
                    if (result) *result = MakeJsonResult("{\"ok\":false,\"cancel\":true}");
                    break;
                }
            } else if (pickSaveFile(name.empty() ? L"未命名导图" : name, sp)) {
                ok = writeFileB64(sp, wideToUtf8_2(b64));
            } else {
                if (result) *result = MakeJsonResult("{\"ok\":false,\"cancel\":true}");
                break;
            }
        }
        if (result) {
            std::string j;
            if (ok) {
                j = "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}";
            } else {
                std::string eb = std::to_string((unsigned long)GetLastError());
                j = "{\"ok\":false,\"error\":\"保存失败\",\"errno\":" + eb + "}";
            }
            *result = MakeJsonResult(j);
        }
        break;
    }
    case 22: { // saveSelfContained(manifestJson, mediaRelJson, saveAs, name, path) → {ok, path?, name?}
        std::wstring manifest = ParamStr(p, 0);
        std::wstring mediaJson = ParamStr(p, 1);
        bool saveAs = ParamInt(p, 2) == 1;
        std::wstring nmArg = ParamStr(p, 3);
        std::wstring pathArg = ParamStr(p, 4);
        // 解析 mediaJson 中的 rel 列表（["attachments/x","attachments/y"...]）
        std::vector<std::wstring> rels;
        {
            std::string mj8 = wideToUtf8_2(mediaJson);
            size_t q = 0;
            while ((q = mj8.find("attachments/", q)) != std::string::npos) {
                size_t e = mj8.find('"', q);
                if (e == std::string::npos) break;
                rels.push_back(utf8ToWide2(mj8.substr(q, e - q)));
                q = e + 1;
            }
        }
        std::string bin;
        if (!buildSelfContained(wideToUtf8_2(manifest), rels, bin)) {
            if (result) *result = MakeJsonResult("{\"ok\":false,\"error\":\"打包失败\"}");
            break;
        }
        bool ok = false;
        std::wstring target; // 最终写入路径
        if (saveAs) {
            std::wstring nm = currentName.empty() ? nmArg : currentName;
            std::wstring sp;
            if (pickSaveFile(nm.empty() ? L"未命名导图" : nm, sp)) {
                target = sp;
            } else {
                if (result) *result = MakeJsonResult("{\"ok\":false,\"cancel\":true}");
                break;
            }
        } else if (!pathArg.empty()) {
            // 前端已打开/保存过的文件：直接覆盖该路径（重启后 currentPath 丢失也可靠）
            target = pathArg;
        } else if (!currentPath.empty()) {
            target = currentPath;
        } else {
            // 新文档：优先静默保存到项目目录，目录已有同名文件才弹框确认
            std::wstring nm = nmArg.empty() ? L"未命名导图" : nmArg;
            std::wstring cand = projectDir() + L"\\" + nm;
            if (!fileExists(cand)) {
                ensureProjectDir();
                target = cand;
            } else {
                std::wstring sp;
                if (pickSaveFile(nm, sp)) {
                    target = sp;
                } else {
                    if (result) *result = MakeJsonResult("{\"ok\":false,\"cancel\":true}");
                    break;
                }
            }
        }
        ok = writeFile(target, bin);
        // 仅普通保存（非显式另存为导出）记住为工作文件，避免后续 Ctrl+S 覆盖到导出文件
        if (ok && !saveAs) { currentPath = target; currentName = fileNameOf(target); }
        if (result) {
            std::string j;
            if (ok) {
                j = "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}";
            } else {
                std::string eb = std::to_string((unsigned long)GetLastError());
                j = "{\"ok\":false,\"error\":\"保存失败\",\"errno\":" + eb + "}";
            }
            *result = MakeJsonResult(j);
        }
        break;
    }
    case 23: { // openSelfContained(path) → {ok, manifest?, name?}；非 USMD → {ok:false, notUsmd:true}
        std::wstring path = ParamStr(p, 0);
        std::string data;
        bool ok = !path.empty() && readFile(path, data);
        if (result) {
            if (ok) {
                std::string manifest;
                if (extractSelfContained(data, manifest)) {
                    std::string j = "{\"ok\":true,\"manifest\":\"" + JsEscape(utf8ToWide2(manifest)) + "\","
                                    "\"name\":\"" + JsEscape(fileNameOf(path)) + "\"}";
                    *result = MakeJsonResult(j);
                } else {
                    *result = MakeJsonResult("{\"ok\":false,\"notUsmd\":true}");
                }
            } else {
                *result = MakeJsonResult("{\"ok\":false,\"error\":\"无法读取文件\"}");
            }
        }
        break;
    }
    case 24: { // restoreMainWindow（启动特效结束：小窗恢复为主窗口大小）
        extern void restoreMainWindow();
        restoreMainWindow();
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 25: { // toggleMaximize（窗口最大化/还原切换）
        extern void toggleMaximize();
        toggleMaximize();
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 26: { // minimizeWindow（窗口最小化）
        if (g_hMainWnd) ShowWindow(g_hMainWnd, SW_MINIMIZE);
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 27: { // closeWindow（窗口关闭）
        if (g_hMainWnd) PostMessage(g_hMainWnd, WM_CLOSE, 0, 0);
        if (result) *result = MakeBoolResult(true);
        break;
    }
    case 28: { // startDragWindow（从 topBar 空白区开始拖动窗口）
        if (g_hMainWnd) {
            ReleaseCapture();
            SendMessage(g_hMainWnd, WM_NCLBUTTONDOWN, HTCAPTION, 0);
        }
        if (result) *result = MakeBoolResult(true);
        break;
    }
    default:
        if (result) *result = MakeBoolResult(false);
        break;
    }
}
