// usmind 本地版 - 原生能力实现
#include "native.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shlobj.h>
#include <commdlg.h>
#include <shobjidl.h>
#include <shellapi.h>
#include <objbase.h>
#include <cstdio>
#include <ctime>
#include <sstream>
#include <algorithm>
#include <cstdint>

namespace usmind {

std::string base64Encode(const std::string& in) {
    static const char tbl[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    int val = 0, valb = -6;
    for (unsigned char c : in) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            out.push_back(tbl[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) out.push_back(tbl[((val << 8) >> (valb + 8)) & 0x3F]);
    while (out.size() % 4) out.push_back('=');
    return out;
}

// base64 解码（容忍空白，忽略非法字符）
static std::string base64Decode(const std::string& in) {
    static int tbl[256] = { 0 };
    static bool init = false;
    if (!init) {
        init = true;
        const char* src = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; src[i]; i++) tbl[(unsigned char)src[i]] = i + 1; // 1..64
    }
    std::string out;
    int val = 0, bits = 0;
    for (unsigned char c : in) {
        if (c == '=' || c == '\r' || c == '\n' || c == ' ' || c == '\t') continue;
        int d = tbl[c];
        if (d <= 0) continue;
        val = (val << 6) | (d - 1);
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out.push_back((char)((val >> bits) & 0xFF));
        }
    }
    return out;
}

static std::wstring g_appDir;

std::wstring appDir() {
    if (g_appDir.empty()) {
        wchar_t buf[MAX_PATH] = {0};
        DWORD n = GetModuleFileNameW(NULL, buf, MAX_PATH);
        if (n > 0) {
            std::wstring p(buf);
            size_t pos = p.find_last_of(L"\\/");
            if (pos != std::wstring::npos) g_appDir = p.substr(0, pos);
        }
    }
    return g_appDir;
}

std::wstring appVersion() { return L"1.0.0"; }

static std::wstring utf8ToWide(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), NULL, 0);
    std::wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &w[0], n);
    return w;
}

static std::string wideToUtf8(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), NULL, 0, NULL, NULL);
    std::string s(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, NULL, NULL);
    return s;
}

// ---------------- 设置 ----------------
// settings.json 与 exe 同目录；轻量 JSON（扁平键值，值均为字符串/数字）
static std::wstring settingsPath() { return appDir() + L"\\settings.json"; }

static std::string readTextFile(const std::wstring& path, bool& ok) {
    ok = false;
    HANDLE h = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (h == INVALID_HANDLE_VALUE) return "";
    ok = true;
    DWORD size = GetFileSize(h, NULL);
    std::string data;
    if (size > 0) {
        data.resize(size);
        DWORD rd = 0;
        if (!ReadFile(h, &data[0], size, &rd, NULL)) { ok = false; data.clear(); }
        data.resize(rd);
    }
    CloseHandle(h);
    return data;
}

// 提取 JSON 中 "key": "value"（value 可能为数字）
static std::wstring jsonGet(const std::string& j, const char* key) {
    std::string k = std::string("\"") + key + "\"";
    size_t p = j.find(k);
    if (p == std::string::npos) return L"";
    p = j.find(':', p + k.size());
    if (p == std::string::npos) return L"";
    p = j.find_first_not_of(" \t\r\n", p + 1);
    if (p == std::string::npos) return L"";
    if (j[p] == '"') {
        size_t e = j.find('"', p + 1);
        if (e == std::string::npos) return L"";
        std::string v = j.substr(p + 1, e - p - 1);
        // 转义还原（\uXXXX 与 \" 简化处理）
        std::string out;
        for (size_t i = 0; i < v.size(); ++i) {
            if (v[i] == '\\' && i + 1 < v.size()) {
                char c = v[++i];
                if (c == 'n') out += '\n';
                else if (c == 't') out += '\t';
                else if (c == 'r') out += '\r';
                else if (c == 'u' && i + 4 < v.size()) {
                    wchar_t wc = 0;
                    for (int kk = 0; kk < 4; ++kk) {
                        char h = v[i + 1 + kk];
                        wc <<= 4;
                        if (h >= '0' && h <= '9') wc |= (h - '0');
                        else if (h >= 'a' && h <= 'f') wc |= (h - 'a' + 10);
                        else if (h >= 'A' && h <= 'F') wc |= (h - 'A' + 10);
                    }
                    i += 4;
                    char buf[4];
                    int n = WideCharToMultiByte(CP_UTF8, 0, &wc, 1, buf, 4, NULL, NULL);
                    out.append(buf, n);
                } else out += c;
            } else out += v[i];
        }
        return utf8ToWide(out);
    }
    // 数字
    size_t e = j.find_first_of(",}\r\n", p);
    return utf8ToWide(j.substr(p, e - p));
}

static std::string jsonEscape(const std::wstring& w) {
    std::string s = wideToUtf8(w);
    std::string out;
    for (char c : s) {
        if (c == '"') out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c == '\n') out += "\\n";
        else if (c == '\t') out += "\\t";
        else if (c == '\r') out += "\\r";
        else out += c;
    }
    return out;
}

Settings loadSettings() {
    Settings s;
    bool ok = false;
    std::string j = readTextFile(settingsPath(), ok);
    if (!ok) return s;
    s.historyDir    = jsonGet(j, "historyDir");
    s.defaultTheme  = jsonGet(j, "defaultTheme");
    s.registerAssoc = (int)std::wcstol(jsonGet(j, "registerAssoc").c_str(), NULL, 10);
    s.lastFile      = jsonGet(j, "lastFile");
    return s;
}

void saveSettings(const Settings& s) {
    std::string j = "{";
    j += "\"historyDir\":\"" + jsonEscape(s.historyDir) + "\",";
    j += "\"defaultTheme\":\"" + jsonEscape(s.defaultTheme) + "\",";
    j += "\"registerAssoc\":" + std::to_string(s.registerAssoc) + ",";
    j += "\"lastFile\":\"" + jsonEscape(s.lastFile) + "\"";
    j += "}";
    writeFile(settingsPath(), j);
}

// ---------------- 文件 ----------------
bool readFile(const std::wstring& path, std::string& out) {
    bool ok = false;
    out = readTextFile(path, ok);
    return ok;
}

bool writeFile(const std::wstring& path, const std::string& data) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return false;
    DWORD wr = 0;
    BOOL r = WriteFile(h, data.data(), (DWORD)data.size(), &wr, NULL);
    CloseHandle(h);
    return r && wr == (DWORD)data.size();
}

bool fileExists(const std::wstring& path) {
    DWORD a = GetFileAttributesW(path.c_str());
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

std::wstring fileNameOf(const std::wstring& path) {
    size_t p = path.find_last_of(L"\\/");
    return p == std::wstring::npos ? path : path.substr(p + 1);
}

std::wstring dirOf(const std::wstring& path) {
    size_t p = path.find_last_of(L"\\/");
    return p == std::wstring::npos ? std::wstring() : path.substr(0, p + 1);
}

// ---------------- 项目文件保存位置 ----------------
std::wstring projectDir() {
    Settings s = loadSettings();
    if (!s.historyDir.empty()) {
        // 相对路径基于 exe 目录
        if (s.historyDir.find(L":") == std::wstring::npos && !s.historyDir.empty() && s.historyDir[0] != L'\\')
            return appDir() + L"\\" + s.historyDir;
        return s.historyDir;
    }
    return appDir() + L"\\history";
}

bool ensureProjectDir() {
    std::wstring d = projectDir();
    DWORD a = GetFileAttributesW(d.c_str());
    if (a != INVALID_FILE_ATTRIBUTES && (a & FILE_ATTRIBUTE_DIRECTORY)) return true;
    return CreateDirectoryW(d.c_str(), NULL) != 0 || GetFileAttributesW(d.c_str()) != INVALID_FILE_ATTRIBUTES;
}

static std::wstring nowStamp() {
    SYSTEMTIME st;
    GetLocalTime(&st);
    wchar_t b[64];
    swprintf(b, 64, L"%04d-%02d-%02d %02d:%02d:%02d", st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
    return std::wstring(b);
}

bool setProjectDir(const std::wstring& dir) {
    // 相对路径改为基于 exe 的相对路径（设置内存储相对形式更便携）
    Settings s = loadSettings();
    std::wstring base = appDir();
    if (dir.size() >= base.size() && dir.compare(0, base.size(), base) == 0) {
        s.historyDir = dir.substr(base.size() + 1); // 相对
    } else {
        s.historyDir = dir;
    }
    saveSettings(s);
    return ensureProjectDir();
}

// ---------------- 对话框 ----------------
bool pickOpenFile(std::wstring& outPath, std::string& outData) {
    IFileOpenDialog* pDlg = NULL;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDlg));
    if (FAILED(hr)) return false;
    COMDLG_FILTERSPEC filters[] = {
        { L"USMIND 导图 (*.usmind)", L"*.usmind" },
        { L"JSON 文件 (*.json)", L"*.json" },
        { L"所有文件 (*.*)", L"*.*" }
    };
    pDlg->SetFileTypes(3, filters);
    pDlg->SetTitle(L"打开导图");
    bool got = false;
    if (SUCCEEDED(pDlg->Show(NULL))) {
        IShellItem* item = NULL;
        if (SUCCEEDED(pDlg->GetResult(&item))) {
            wchar_t* path = NULL;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                outPath = path;
                CoTaskMemFree(path);
                got = readFile(outPath, outData);
            }
            item->Release();
        }
    }
    pDlg->Release();
    return got;
}

bool pickAnyFile(std::wstring& outName, std::string& outB64) {
    IFileOpenDialog* pDlg = NULL;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDlg));
    if (FAILED(hr)) return false;
    pDlg->SetTitle(L"选择附件文件");
    bool got = false;
    if (SUCCEEDED(pDlg->Show(NULL))) {
        IShellItem* item = NULL;
        if (SUCCEEDED(pDlg->GetResult(&item))) {
            wchar_t* path = NULL;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                std::wstring wp = path;
                CoTaskMemFree(path);
                outName = fileNameOf(wp);
                std::string raw;
                if (readFile(wp, raw)) {
                    outB64 = base64Encode(raw);
                    got = true;
                }
            }
            item->Release();
        }
    }
    pDlg->Release();
    return got;
}

// ---- 附件外置存储前置声明（copyAttachmentFile/openAttachment 使用）----
static bool safeRelPath(const std::wstring& rel);
std::wstring attachmentDir();

// 选择任意文件并直接复制到外置附件目录（不经 base64，大文件也稳）
// 成功返回相对路径 attachments/att_<毫秒>_<原名>；用户取消返回 false
bool copyAttachmentFile(std::wstring& outName, std::wstring& outRelPath) {
    IFileOpenDialog* pDlg = NULL;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDlg));
    if (FAILED(hr)) return false;
    pDlg->SetTitle(L"选择附件文件");
    bool got = false;
    if (SUCCEEDED(pDlg->Show(NULL))) {
        IShellItem* item = NULL;
        if (SUCCEEDED(pDlg->GetResult(&item))) {
            wchar_t* path = NULL;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                std::wstring wp = path;
                CoTaskMemFree(path);
                outName = fileNameOf(wp);
                wchar_t ts[40] = { 0 };
                swprintf(ts, 40, L"%lld", (long long)GetTickCount64());
                std::wstring rel = L"attachments/att_" + std::wstring(ts) + L"_" + outName;
                if (safeRelPath(rel)) {
                    std::wstring full = attachmentDir() + L"\\" + rel.substr(12);
                    if (CopyFileW(wp.c_str(), full.c_str(), FALSE)) {
                        outRelPath = rel;
                        got = true;
                    }
                }
            }
            item->Release();
        }
    }
    pDlg->Release();
    return got;
}

// ---- 附件外置存储前置声明（openAttachment 使用）----
static bool safeRelPath(const std::wstring& rel);
std::wstring attachmentDir();

// 用系统默认程序打开 base64 附件：解码 → 临时目录 → ShellExecute
// 兼容外置模式：b64 参数若为 "attachments/xxx" 相对路径，则直接打开 exe/data/attachments 下文件
bool openAttachment(const std::string& b64, const std::wstring& name) {
    if (b64.empty()) return false;
    std::wstring rel = utf8ToWide(b64);
    if (rel.compare(0, 12, L"attachments/") == 0 && safeRelPath(rel)) {
        std::wstring full = attachmentDir() + L"\\" + rel.substr(12);
        if (fileExists(full)) {
            HINSTANCE r = ShellExecuteW(NULL, L"open", full.c_str(), NULL, NULL, SW_SHOWNORMAL);
            return (INT_PTR)r > 32;
        }
        return false;
    }
    std::string bytes = base64Decode(b64);
    if (bytes.empty()) return false;
    wchar_t tmp[MAX_PATH] = { 0 };
    if (!GetTempPathW(MAX_PATH, tmp)) return false;
    std::wstring dir = std::wstring(tmp) + L"usmind_att\\";
    CreateDirectoryW(dir.c_str(), NULL);
    std::wstring safe = fileNameOf(name); // 防目录穿越：只取文件名
    if (safe.empty()) safe = L"attachment.bin";
    std::wstring path = dir + safe;
    if (!writeFile(path, bytes)) return false;
    HINSTANCE r = ShellExecuteW(NULL, L"open", path.c_str(), NULL, NULL, SW_SHOWNORMAL);
    return (INT_PTR)r > 32;
}

// ---- 附件外置存储（exe/data/attachments，media.local 虚拟主机映射到 exe/data） ----
std::wstring attachmentDir() {
    std::wstring base = appDir() + L"\\data";
    CreateDirectoryW(base.c_str(), NULL);
    std::wstring d = base + L"\\attachments";
    CreateDirectoryW(d.c_str(), NULL);
    return d;
}

// 校验相对路径：必须 "attachments/" 前缀、正斜杠、无 .. 与冒号穿越
static bool safeRelPath(const std::wstring& rel) {
    if (rel.size() < 13) return false;
    if (rel.compare(0, 12, L"attachments/") != 0) return false;
    if (rel.find(L"..") != std::wstring::npos) return false;
    if (rel.find(L":") != std::wstring::npos) return false;
    if (rel.find(L"\\") != std::wstring::npos) return false;
    return true;
}

// 写入外置附件（relPath="attachments/xxx.mp4"，b64 内容）
bool writeAttachment(const std::wstring& relPath, const std::string& b64) {
    if (!safeRelPath(relPath)) return false;
    std::string bytes = base64Decode(b64);
    if (bytes.empty()) return false;
    std::wstring full = attachmentDir() + L"\\" + relPath.substr(12);
    return writeFile(full, bytes);
}

// 外置附件是否存在
bool attachmentExists(const std::wstring& relPath) {
    if (!safeRelPath(relPath)) return false;
    std::wstring full = attachmentDir() + L"\\" + relPath.substr(12);
    return fileExists(full);
}

// ---------------- 自包含包 v3（USMD\x01：header + manifest + 命名媒体，二进制直拷，零 zip 依赖） ----------------
static void putLE32(std::string& out, uint32_t v) {
    out.push_back((char)(v & 0xFF));
    out.push_back((char)((v >> 8) & 0xFF));
    out.push_back((char)((v >> 16) & 0xFF));
    out.push_back((char)((v >> 24) & 0xFF));
}
static uint32_t getLE32(const std::string& s, size_t& p) {
    if (p + 4 > s.size()) return 0;
    uint32_t v = (uint32_t)(unsigned char)s[p]
        | ((uint32_t)(unsigned char)s[p + 1] << 8)
        | ((uint32_t)(unsigned char)s[p + 2] << 16)
        | ((uint32_t)(unsigned char)s[p + 3] << 24);
    p += 4;
    return v;
}
static const char kUsmdMagic[5] = { 'U', 'S', 'M', 'D', 1 };

// 组装自包含包：manifest JSON + 命名媒体列表（rel="attachments/xxx"，从附件目录直读）
bool buildSelfContained(const std::string& manifest, const std::vector<std::wstring>& rels, std::string& out) {
    out.clear();
    out.append(kUsmdMagic, 5);
    putLE32(out, (uint32_t)manifest.size());
    out += manifest;
    for (const auto& rel : rels) {
        if (!safeRelPath(rel)) continue;
        bool ok = false;
        std::string raw = readTextFile(attachmentDir() + L"\\" + rel.substr(12), ok);
        if (!ok) continue; // 缺失媒体跳过（引用保留但缺失）
        std::string name = wideToUtf8(rel);
        putLE32(out, (uint32_t)name.size());
        out += name;
        putLE32(out, (uint32_t)raw.size());
        out += raw;
    }
    return true;
}

// 解析自包含包：拆出 manifest；命名媒体逐个写回附件目录（同名，引用无需改写）
bool extractSelfContained(const std::string& in, std::string& manifest) {
    if (in.size() < 9) return false;
    if (memcmp(in.data(), kUsmdMagic, 5) != 0) return false;
    size_t p = 5;
    uint32_t ml = getLE32(in, p);
    if (p + ml > in.size()) return false;
    manifest = in.substr(p, ml);
    p += ml;
    while (p + 4 <= in.size()) {
        uint32_t nl = getLE32(in, p);
        if (p + nl > in.size()) return false;
        std::string name = in.substr(p, nl);
        p += nl;
        uint32_t dl = getLE32(in, p);
        if (p + dl > in.size()) return false;
        std::string bytes = in.substr(p, dl);
        p += dl;
        std::wstring rel = utf8ToWide(name);
        if (safeRelPath(rel)) writeFile(attachmentDir() + L"\\" + rel.substr(12), bytes);
    }
    return true;
}

// 读任意文件为 base64（二进制安全，用于自包含 zip 打开）
bool readFileB64(const std::wstring& path, std::string& outB64) {
    bool ok = false;
    std::string raw = readTextFile(path, ok);
    if (!ok) return false;
    outB64 = base64Encode(raw);
    return true;
}

// base64 → 写二进制文件（用于自包含 zip 保存）
bool writeFileB64(const std::wstring& path, const std::string& b64) {
    std::string bytes = base64Decode(b64);
    if (bytes.empty()) return false;
    return writeFile(path, bytes);
}

bool pickSaveFile(const std::wstring& suggested, std::wstring& outPath) {
    IFileSaveDialog* pDlg = NULL;
    HRESULT hr = CoCreateInstance(CLSID_FileSaveDialog, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDlg));
    if (FAILED(hr)) return false;
    COMDLG_FILTERSPEC filters[] = { { L"USMIND 导图 (*.usmind)", L"*.usmind" } };
    pDlg->SetFileTypes(1, filters);
    pDlg->SetDefaultExtension(L"usmind");
    pDlg->SetTitle(L"保存导图");
    if (!suggested.empty()) {
        pDlg->SetFileName(suggested.c_str());
    }
    bool got = false;
    if (SUCCEEDED(pDlg->Show(NULL))) {
        IShellItem* item = NULL;
        if (SUCCEEDED(pDlg->GetResult(&item))) {
            wchar_t* path = NULL;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                outPath = path;
                CoTaskMemFree(path);
                got = true;
            }
            item->Release();
        }
    }
    pDlg->Release();
    return got;
}

bool pickSaveFileAny(const std::wstring& defDir, const std::wstring& defName, std::wstring& outPath) {
    IFileSaveDialog* pDlg = NULL;
    HRESULT hr = CoCreateInstance(CLSID_FileSaveDialog, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDlg));
    if (FAILED(hr)) return false;
    COMDLG_FILTERSPEC filters[] = { { L"所有文件 (*.*)", L"*.*" } };
    pDlg->SetFileTypes(1, filters);
    pDlg->SetTitle(L"导出文件");
    if (!defDir.empty()) {
        IShellItem* folder = NULL;
        if (SUCCEEDED(SHCreateItemFromParsingName(defDir.c_str(), NULL, IID_PPV_ARGS(&folder)))) {
            pDlg->SetFolder(folder);
            folder->Release();
        }
    }
    if (!defName.empty()) pDlg->SetFileName(defName.c_str());
    bool got = false;
    if (SUCCEEDED(pDlg->Show(NULL))) {
        IShellItem* item = NULL;
        if (SUCCEEDED(pDlg->GetResult(&item))) {
            wchar_t* path = NULL;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                outPath = path;
                CoTaskMemFree(path);
                got = true;
            }
            item->Release();
        }
    }
    pDlg->Release();
    return got;
}

bool pickFolder(std::wstring& out) {
    IFileOpenDialog* pDlg = NULL;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDlg));
    if (FAILED(hr)) return false;
    DWORD opts = 0;
    pDlg->GetOptions(&opts);
    pDlg->SetOptions(opts | FOS_PICKFOLDERS);
    pDlg->SetTitle(L"选择项目文件保存目录");
    bool got = false;
    if (SUCCEEDED(pDlg->Show(NULL))) {
        IShellItem* item = NULL;
        if (SUCCEEDED(pDlg->GetResult(&item))) {
            wchar_t* path = NULL;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                out = path;
                CoTaskMemFree(path);
                got = true;
            }
            item->Release();
        }
    }
    pDlg->Release();
    return got;
}

// ---------------- 文件关联 ----------------
void registerFileAssoc() {
    // 用户级关联，无需管理员；写入 HKCU\Software\Classes
    std::wstring exe = appDir() + L"\\" + L"usmind.exe";
    HKEY hk;

    // .usmind → usmind.mindmap
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"Software\\Classes\\.usmind", 0, NULL, 0, KEY_SET_VALUE, NULL, &hk, NULL) == ERROR_SUCCESS) {
        const wchar_t* v = L"usmind.mindmap";
        RegSetValueExW(hk, NULL, 0, REG_SZ, (const BYTE*)v, (DWORD)((wcslen(v) + 1) * sizeof(wchar_t)));
        RegCloseKey(hk);
    }
    // 命令
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"Software\\Classes\\usmind.mindmap\\shell\\open\\command", 0, NULL, 0, KEY_SET_VALUE, NULL, &hk, NULL) == ERROR_SUCCESS) {
        std::wstring cmd = L"\"" + exe + L"\" \"%1\"";
        RegSetValueExW(hk, NULL, 0, REG_SZ, (const BYTE*)cmd.c_str(), (DWORD)((cmd.size() + 1) * sizeof(wchar_t)));
        RegCloseKey(hk);
    }
    // 图标
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"Software\\Classes\\usmind.mindmap\\DefaultIcon", 0, NULL, 0, KEY_SET_VALUE, NULL, &hk, NULL) == ERROR_SUCCESS) {
        std::wstring ico = exe + L",0";
        RegSetValueExW(hk, NULL, 0, REG_SZ, (const BYTE*)ico.c_str(), (DWORD)((ico.size() + 1) * sizeof(wchar_t)));
        RegCloseKey(hk);
    }
    // 友好名称
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"Software\\Classes\\usmind.mindmap", 0, NULL, 0, KEY_SET_VALUE, NULL, &hk, NULL) == ERROR_SUCCESS) {
        const wchar_t* v = L"USMIND 思维导图";
        RegSetValueExW(hk, NULL, 0, REG_SZ, (const BYTE*)v, (DWORD)((wcslen(v) + 1) * sizeof(wchar_t)));
        RegCloseKey(hk);
    }
    // 刷新资源管理器图标缓存
    SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, NULL, NULL);
}

// ---------------- 单实例 ----------------
bool notifyExistingInstance(const std::wstring& fileArg) {
    HANDLE m = CreateMutexW(NULL, FALSE, L"Local\\USMIND_SINGLE_INSTANCE");
    if (GetLastError() == ERROR_ALREADY_EXISTS) {
        // 找到已运行窗口并发送打开请求
        HWND w = FindWindowW(L"UsmindMainWnd", NULL);
        if (w) {
            COPYDATASTRUCT cds;
            std::string utf8 = wideToUtf8(fileArg);
            cds.dwData = 1;
            cds.cbData = (DWORD)utf8.size() + 1;
            cds.lpData = (void*)utf8.c_str();
            SendMessageW(w, WM_COPYDATA, (WPARAM)NULL, (LPARAM)&cds);
            // 激活窗口
            if (IsIconic(w)) ShowWindow(w, SW_RESTORE);
            SetForegroundWindow(w);
        }
        CloseHandle(m);
        return true;
    }
    return false; // 持有互斥体，作为主实例继续
}

} // namespace usmind
