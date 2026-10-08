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
    L"setHistoryDir",   // 13
    L"registerAssoc",   // 14
    L"notifyDocumentState", // 15 （前端上报文件名变化，用于标题栏）
    L"pickFolder",      // 16 （目录选择，历史目录设置用）
    L"pickAnyFile",     // 17 （选任意文件：写入外置附件返回相对路径）
    L"openAttachment",  // 18 （附件用系统默认程序打开：base64 或 外置相对路径）
    L"writeAttachment", // 19 （写入外置附件 relPath+b64）
    L"openPathBinary",  // 20 （读任意文件→base64，自包含 zip 打开用）
    L"saveBinary"       // 21 （base64→写二进制文件，自包含 zip 保存用）
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
// 生成 JSON 返回（BSTR）
static VARIANT MakeJsonResult(const std::string& json) {
    VARIANT v;
    VariantInit(&v);
    v.vt = VT_BSTR;
    v.bstrVal = SysAllocStringLen(NULL, (UINT)json.size());
    MultiByteToWideChar(CP_UTF8, 0, json.c_str(), (int)json.size(), v.bstrVal, (int)json.size());
    return v;
}
static VARIANT MakeBoolResult(bool ok) {
    VARIANT v;
    VariantInit(&v);
    v.vt = VT_BSTR;
    std::string j = ok ? "{\"ok\":true}" : "{\"ok\":false}";
    v.bstrVal = SysAllocStringLen(NULL, (UINT)j.size());
    MultiByteToWideChar(CP_UTF8, 0, j.c_str(), (int)j.size(), v.bstrVal, (int)j.size());
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
                        "\"historyDir\":\"" + JsEscape(historyDir()) + "\"}";
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
        std::wstring name = ParamStr(p, 2);
        bool ok = false;
        if (!path.empty()) {
            snapshotHistory(path, name.empty() ? fileNameOf(path) : name);
            ok = writeFile(path, wideToUtf8_2(content));
        }
        if (result) *result = MakeBoolResult(ok);
        break;
    }
    case 8: { // saveCurrent(content, name) → {ok, path?, name?}
        std::wstring content = ParamStr(p, 0);
        std::wstring name = ParamStr(p, 1);
        if (!currentPath.empty()) {
            snapshotHistory(currentPath, currentName.empty() ? name : currentName);
            bool ok = writeFile(currentPath, wideToUtf8_2(content));
            if (result) {
                std::string j = ok ? "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}"
                                   : "{\"ok\":false}";
                *result = MakeJsonResult(j);
            }
        } else {
            // 未关联文件：弹另存为
            std::wstring path;
            if (pickSaveFile(name.empty() ? L"未命名导图.usmind" : name + L".usmind", path)) {
                snapshotHistory(path, name);
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
    case 10: { // getHistory → [{id,time,name,size}]
        auto items = listHistory();
        std::string j = "{\"ok\":true,\"items\":[";
        bool first = true;
        for (auto& it : items) {
            if (!first) j += ",";
            first = false;
            j += "{\"id\":\"" + JsEscape(it.id) + "\",\"time\":\"" + JsEscape(it.time) + "\","
                 "\"name\":\"" + JsEscape(it.name) + "\",\"size\":" + std::to_string(it.size) + "}";
        }
        j += "]}";
        if (result) *result = MakeJsonResult(j);
        break;
    }
    case 11: { // restoreHistory(id) → {ok,data}
        std::wstring id = ParamStr(p, 0);
        std::string data;
        bool ok = readHistoryItem(id, data);
        if (result) {
            if (ok) {
                std::string j = "{\"ok\":true,\"data\":\"" + JsEscape(utf8ToWide2(data)) + "\"}";
                *result = MakeJsonResult(j);
            } else {
                *result = MakeJsonResult("{\"ok\":false}");
            }
        }
        break;
    }
    case 12: { // clearHistory
        bool ok = clearHistory();
        if (result) *result = MakeBoolResult(ok);
        break;
    }
    case 13: { // setHistoryDir(dir) → {ok,dir}
        std::wstring dir = ParamStr(p, 0);
        bool ok = setHistoryDir(dir);
        if (result) {
            std::string j = ok ? "{\"ok\":true,\"dir\":\"" + JsEscape(historyDir()) + "\"}" : "{\"ok\":false}";
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
    case 17: { // pickAnyFile → {ok, name, relPath?, b64?}（外置模式：直接写 attachments 返回相对路径）
        std::wstring name;
        std::string b64;
        bool ok = pickAnyFile(name, b64);
        if (result) {
            std::string j;
            if (ok) {
                // 生成唯一相对路径 att_<毫秒>_<原名>，写入外置附件目录
                wchar_t ts[40] = { 0 };
                swprintf(ts, 40, L"%lld", (long long)GetTickCount64());
                std::wstring rel = L"attachments/att_" + std::wstring(ts) + L"_" + fileNameOf(name);
                if (writeAttachment(rel, b64)) {
                    j = "{\"ok\":true,\"name\":\"" + JsEscape(name) + "\",\"relPath\":\"" + JsEscape(rel) + "\"}";
                } else {
                    j = "{\"ok\":true,\"name\":\"" + JsEscape(name) + "\",\"b64\":\"" + b64 + "\"}";
                }
            } else j = "{\"ok\":false}";
            *result = MakeJsonResult(j);
        }
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
    case 21: { // saveBinary(b64, path, name) → {ok, path?}（自包含 zip 保存）
        std::wstring b64 = ParamStr(p, 0);
        std::wstring path = ParamStr(p, 1);
        std::wstring name = ParamStr(p, 2);
        bool ok = false;
        if (!path.empty()) {
            snapshotHistory(path, name.empty() ? fileNameOf(path) : name);
            ok = writeFileB64(path, wideToUtf8_2(b64));
        } else {
            std::wstring sp;
            if (pickSaveFile(name.empty() ? L"未命名导图.usmind" : name + L".usmind", sp)) {
                snapshotHistory(sp, name);
                ok = writeFileB64(sp, wideToUtf8_2(b64));
                if (ok) { currentPath = sp; currentName = fileNameOf(sp); }
            } else {
                if (result) *result = MakeJsonResult("{\"ok\":false,\"cancel\":true}");
                break;
            }
        }
        if (result) {
            std::string j = ok ? "{\"ok\":true,\"path\":\"" + JsEscape(currentPath) + "\",\"name\":\"" + JsEscape(currentName) + "\"}"
                               : "{\"ok\":false,\"error\":\"保存失败\"}";
            *result = MakeJsonResult(j);
        }
        break;
    }
    default:
        if (result) *result = MakeBoolResult(false);
        break;
    }
}
