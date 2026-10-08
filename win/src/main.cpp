// usmind 本地版 - Windows 桌面壳
// Win32 无边框窗口 + WebView2 内嵌本地前端；单实例；双击 .usmind 唤起
#include <windows.h>
#include <windowsx.h>
#include <WebView2.h>
#include <dwmapi.h>
#include <shellapi.h>
#include <objbase.h>

#include <string>
#include <vector>

#include "bridge.h"
#include "native.h"

using namespace usmind;

// ---------------- 全局 ----------------
std::wstring g_startupFile;
HWND g_hMainWnd = NULL;
ICoreWebView2* g_webview = NULL;
ICoreWebView2_4* g_webview4 = NULL; // 高级接口（虚拟主机映射等）
ICoreWebView2Controller* g_ctrl = NULL;
UsmindBridge* g_bridge = NULL;
static std::wstring g_pendingOpen; // 单实例转发来的文件路径

// 标题栏
const int TITLEBAR_H = 46;
const int BTN_W = 46;
static int g_hoverBtn = 0; // 0 无, 1 最小化, 2 最大化, 3 关闭

// 窗口图标资源
#define IDI_APP 101

// ---------------- 工具 ----------------
static std::string utf8FromWide(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), NULL, 0, NULL, NULL);
    std::string s(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, NULL, NULL);
    return s;
}
static std::wstring wideFromUtf8(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), NULL, 0);
    std::wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &w[0], n);
    return w;
}
// JSON 字符串转义（用于 PostWebMessageAsJson 消息体）
static std::wstring JsonEsc(const std::wstring& w) {
    std::wstring o;
    for (wchar_t c : w) {
        switch (c) {
        case L'"': o += L"\\\""; break;
        case L'\\': o += L"\\\\"; break;
        case L'\n': o += L"\\n"; break;
        case L'\r': o += L"\\r"; break;
        case L'\t': o += L"\\t"; break;
        default:
            if (c < 0x20) { wchar_t b[8]; swprintf(b, 8, L"\\u%04x", c); o += b; }
            else o += c;
        }
    }
    return o;
}

// 导出下载拦截：把引擎触发的下载改为"另存为"对话框（本地版导出文件路径可控）
class DownloadStartingHandler : public ICoreWebView2DownloadStartingEventHandler {
public:
    DownloadStartingHandler() : ref_(1) {}
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (!ppv) return E_POINTER;
        *ppv = nullptr;
        if (riid == IID_IUnknown) { *ppv = this; AddRef(); return S_OK; }
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return ++ref_; }
    STDMETHODIMP_(ULONG) Release() override { ULONG r = --ref_; if (r == 0) delete this; return r; }
    STDMETHODIMP Invoke(ICoreWebView2* sender, ICoreWebView2DownloadStartingEventArgs* args) override {
        if (!args) return S_OK;
        ICoreWebView2DownloadOperation* op = NULL;
        args->get_DownloadOperation(&op);
        std::wstring suggested;
        if (op) {
            wchar_t* rp = NULL;
            if (SUCCEEDED(op->get_ResultFilePath(&rp)) && rp) {
                suggested = rp;
                CoTaskMemFree(rp);
            }
        }
        std::wstring dir = dirOf(suggested);
        std::wstring name = suggested.empty() ? L"export" : fileNameOf(suggested);
        std::wstring chosen;
        if (pickSaveFileAny(dir, name, chosen)) {
            args->put_ResultFilePath(chosen.c_str());
            args->put_Handled(TRUE);
        } else {
            args->put_Cancel(TRUE);
        }
        if (op) op->Release();
        return S_OK;
    }
private:
    ULONG ref_;
};

// ---------------- WebView2 回调（先定义控制器回调） ----------------
class CtrlCompletedHandler : public ICoreWebView2CreateCoreWebView2ControllerCompletedHandler {
public:
    CtrlCompletedHandler(ICoreWebView2Environment* env) : ref_(1), env_(env) { if (env_) env_->AddRef(); }
    ~CtrlCompletedHandler() { if (env_) env_->Release(); }
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (!ppv) return E_POINTER;
        *ppv = nullptr;
        if (riid == IID_IUnknown) { *ppv = this; AddRef(); return S_OK; }
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return ++ref_; }
    STDMETHODIMP_(ULONG) Release() override { ULONG r = --ref_; if (r == 0) delete this; return r; }

    STDMETHODIMP Invoke(HRESULT result, ICoreWebView2Controller* controller) override {
        if (FAILED(result) || !controller) { DestroyWindow(g_hMainWnd); return S_OK; }
        g_ctrl = controller;
        g_ctrl->AddRef();
        if (FAILED(controller->get_CoreWebView2(&g_webview))) { DestroyWindow(g_hMainWnd); return S_OK; }

        // 设置（状态栏/右键菜单/开发者工具）
        ICoreWebView2Settings* wvSettings = NULL;
        g_webview->get_Settings(&wvSettings);
        if (wvSettings) {
            wvSettings->put_IsStatusBarEnabled(FALSE);
            wvSettings->put_AreDefaultContextMenusEnabled(TRUE);
            wvSettings->put_AreDevToolsEnabled(FALSE);
            wvSettings->Release();
        }

        // 升级到 ICoreWebView2_4（虚拟主机映射能力）
        // MinGW 无 __uuidof，手工构造接口 IID
        static const IID IID_WV2_4 = { 0x20d02d59, 0x6df2, 0x42dc,
            { 0xbd, 0x06, 0xf9, 0x8a, 0x69, 0x4b, 0x13, 0x02 } };
        g_webview4 = NULL;
        g_webview->QueryInterface(IID_WV2_4, (void**)&g_webview4);

        // 虚拟主机映射：https://app.local → web 目录（exe 相对路径）
        std::wstring webDir = appDir() + L"\\web";
        if (g_webview4) {
            g_webview4->SetVirtualHostNameToFolderMapping(
                L"app.local", webDir.c_str(), COREWEBVIEW2_HOST_RESOURCE_ACCESS_KIND_ALLOW);
            // 虚拟主机映射：https://media.local → data 目录（附件外置存储，浏览器流式加载本地媒体）
            std::wstring dataDir = appDir() + L"\\data";
            CreateDirectoryW(dataDir.c_str(), NULL);
            CreateDirectoryW((dataDir + L"\\attachments").c_str(), NULL);
            g_webview4->SetVirtualHostNameToFolderMapping(
                L"media.local", dataDir.c_str(), COREWEBVIEW2_HOST_RESOURCE_ACCESS_KIND_ALLOW);
            // 导出下载拦截（弹"另存为"对话框，本地导出路径可控）
            EventRegistrationToken dlTok;
            g_webview4->add_DownloadStarting(new DownloadStartingHandler(), &dlTok);
        }

        // 暴露宿主对象（VARIANT 包装 IDispatch）
        g_bridge = new UsmindBridge();
        {
            VARIANT hv;
            VariantInit(&hv);
            hv.vt = VT_DISPATCH;
            hv.pdispVal = g_bridge;
            g_bridge->AddRef();
            g_webview->AddHostObjectToScript(L"usmind", &hv);
            VariantClear(&hv);
        }

        // 初始布局
        RECT rc;
        GetClientRect(g_hMainWnd, &rc);
        RECT bounds = { 0, TITLEBAR_H, rc.right, rc.bottom };
        controller->put_Bounds(bounds);

        // 打开初始文件：命令行文件 或 上次文件
        std::wstring initFile = g_startupFile;
        if (initFile.empty()) {
            Settings s = loadSettings();
            if (!s.lastFile.empty() && fileExists(s.lastFile)) initFile = s.lastFile;
        }
        if (!initFile.empty()) {
            g_pendingOpen = initFile;
        }

        g_webview->Navigate(L"https://app.local/index.html");
        return S_OK;
    }
private:
    ULONG ref_;
    ICoreWebView2Environment* env_;
};

class EnvCompletedHandler : public ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
public:
    EnvCompletedHandler() : ref_(1) {}
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (!ppv) return E_POINTER;
        *ppv = nullptr;
        if (riid == IID_IUnknown) { *ppv = this; AddRef(); return S_OK; }
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return ++ref_; }
    STDMETHODIMP_(ULONG) Release() override { ULONG r = --ref_; if (r == 0) delete this; return r; }
    STDMETHODIMP Invoke(HRESULT result, ICoreWebView2Environment* env) override {
        if (FAILED(result) || !env) { DestroyWindow(g_hMainWnd); return S_OK; }
        HRESULT hr = env->CreateCoreWebView2Controller(g_hMainWnd, new CtrlCompletedHandler(env));
        if (FAILED(hr)) DestroyWindow(g_hMainWnd);
        return S_OK;
    }
private:
    ULONG ref_;
};

// ---------------- 标题栏绘制 ----------------
static void DrawTitleBar(HWND hWnd, HDC hdc) {
    RECT rc;
    GetClientRect(hWnd, &rc);
    int w = rc.right;
    int h = TITLEBAR_H;

    // 背景：白色透明（与画布融合）
    COLORREF bg = RGB(0xff, 0xff, 0xff);
    HBRUSH brbg = CreateSolidBrush(bg);
    RECT full = { 0, 0, w, h };
    FillRect(hdc, &full, brbg);
    DeleteObject(brbg);

    // 图标
    HICON icon = LoadIconW(GetModuleHandleW(NULL), MAKEINTRESOURCEW(IDI_APP));
    if (icon) DrawIconEx(hdc, 12, (h - 20) / 2, icon, 20, 20, 0, NULL, DI_NORMAL);

    // 标题
    std::wstring title = L"USMIND 思维导图";
    SetBkMode(hdc, TRANSPARENT);
    HFONT font = CreateFontW(-13, 0, 0, 0, FW_SEMIBOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, 0, 0, L"Microsoft YaHei UI");
    HFONT old = (HFONT)SelectObject(hdc, font);
    SetTextColor(hdc, RGB(0x33, 0x38, 0x48));
    RECT tr = { 42, 0, w - BTN_W * 3 - 8, h };
    DrawTextW(hdc, title.c_str(), -1, &tr, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    // 窗口按钮（Segoe MDL2 Assets 字形）
    struct Btn { int x; const wchar_t* glyph; int hover; };
    Btn btns[3] = {
        { w - BTN_W * 3, L"\uE921", 1 }, // 最小化
        { w - BTN_W * 2, L"\uE922", 2 }, // 最大化
        { w - BTN_W,     L"\uE8BB", 3 }  // 关闭
    };
    for (int i = 0; i < 3; ++i) {
        RECT br = { btns[i].x, 0, btns[i].x + BTN_W, h };
        if (g_hoverBtn == btns[i].hover) {
            HBRUSH hb = CreateSolidBrush(i == 2 ? RGB(0xC4, 0x2B, 0x1C) : RGB(0xe0, 0xe3, 0xec));
            FillRect(hdc, &br, hb);
            DeleteObject(hb);
        }
        // 窗口按钮用 Segoe MDL2 Assets 字体
        HFONT btnFont = CreateFontW(-13, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, 0, 0, L"Segoe MDL2 Assets");
        HFONT oldBtn = (HFONT)SelectObject(hdc, btnFont);
        SetTextColor(hdc, i == 2 && g_hoverBtn == 3 ? RGB(255,255,255) : RGB(0x44, 0x49, 0x58));
        RECT gr = { btns[i].x, 0, btns[i].x + BTN_W, h };
        DrawTextW(hdc, btns[i].glyph, -1, &gr, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        SelectObject(hdc, oldBtn);
        DeleteObject(btnFont);
    }
    SelectObject(hdc, old);
    DeleteObject(font);
}

// ---------------- 通知前端打开文件 ----------------
static void NotifyFrontendOpen(const std::wstring& path) {
    if (!g_webview) return;
    std::wstring msg = L"{\"type\":\"openRequest\",\"path\":\"" + JsonEsc(path) + L"\"}";
    g_webview->PostWebMessageAsJson(msg.c_str());
}

// ---------------- 窗口过程 ----------------
LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_NCHITTEST: {
        POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) };
        RECT wr;
        GetWindowRect(hWnd, &wr);
        int x = pt.x - wr.left, y = pt.y - wr.top;
        if (y >= 0 && y < TITLEBAR_H) {
            int w = wr.right - wr.left;
            if (x >= w - BTN_W * 3) return HTCLIENT; // 按钮区，交给 WM_LBUTTONDOWN
            return HTCAPTION;                        // 可拖拽
        }
        return HTCLIENT;
    }

    case WM_LBUTTONDOWN: {
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
        if (y < TITLEBAR_H) {
            RECT rc;
            GetClientRect(hWnd, &rc);
            int w = rc.right;
            if (x >= w - BTN_W * 3 && x < w - BTN_W * 2) SendMessageW(hWnd, WM_SYSCOMMAND, SC_MINIMIZE, 0);
            else if (x >= w - BTN_W * 2 && x < w - BTN_W) SendMessageW(hWnd, WM_SYSCOMMAND, IsZoomed(hWnd) ? SC_RESTORE : SC_MAXIMIZE, 0);
            else if (x >= w - BTN_W) PostMessageW(hWnd, WM_CLOSE, 0, 0);
        }
        return 0;
    }

    case WM_MOUSEMOVE: {
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
        int hover = 0;
        if (y < TITLEBAR_H) {
            RECT rc;
            GetClientRect(hWnd, &rc);
            int w = rc.right;
            if (x >= w - BTN_W * 3 && x < w - BTN_W * 2) hover = 1;
            else if (x >= w - BTN_W * 2 && x < w - BTN_W) hover = 2;
            else if (x >= w - BTN_W) hover = 3;
        }
        if (hover != g_hoverBtn) {
            g_hoverBtn = hover;
            RECT rc;
            GetClientRect(hWnd, &rc);
            rc.bottom = TITLEBAR_H;
            InvalidateRect(hWnd, &rc, FALSE);
        }
        TRACKMOUSEEVENT tme = { sizeof(tme), TME_LEAVE, hWnd, 0 };
        TrackMouseEvent(&tme);
        return 0;
    }

    case WM_MOUSELEAVE:
        if (g_hoverBtn) {
            g_hoverBtn = 0;
            RECT rc;
            GetClientRect(hWnd, &rc);
            rc.bottom = TITLEBAR_H;
            InvalidateRect(hWnd, &rc, FALSE);
        }
        return 0;

    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hWnd, &ps);
        DrawTitleBar(hWnd, hdc);
        EndPaint(hWnd, &ps);
        return 0;
    }

    case WM_ERASEBKGND:
        return 1; // 背景由 WM_PAINT 绘制

    case WM_SIZE: {
        if (g_ctrl) {
            RECT rc;
            GetClientRect(hWnd, &rc);
            RECT bounds = { 0, TITLEBAR_H, rc.right, rc.bottom };
            g_ctrl->put_Bounds(bounds);
        }
        return 0;
    }

    case WM_GETMINMAXINFO: {
        MINMAXINFO* mmi = (MINMAXINFO*)lp;
        mmi->ptMinTrackSize.x = 900;
        mmi->ptMinTrackSize.y = 600;
        return 0;
    }

    // 单实例：接收打开文件请求
    case WM_COPYDATA: {
        COPYDATASTRUCT* cds = (COPYDATASTRUCT*)lp;
        if (cds && cds->dwData == 1 && cds->lpData && cds->cbData > 0) {
            std::string u8((const char*)cds->lpData, cds->cbData - 1);
            std::wstring path = wideFromUtf8(u8);
            if (!path.empty()) NotifyFrontendOpen(path);
        }
        return TRUE;
    }

    case WM_DESTROY: {
        if (g_webview4) { g_webview4->Release(); g_webview4 = NULL; }
        if (g_webview) { g_webview->Release(); g_webview = NULL; }
        if (g_ctrl) { g_ctrl->Release(); g_ctrl = NULL; }
        PostQuitMessage(0);
        return 0;
    }

    case WM_CLOSE:
        DestroyWindow(hWnd);
        return 0;
    }
    return DefWindowProcW(hWnd, msg, wp, lp);
}

// ---------------- 入口 ----------------
int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, PWSTR cmdLine, int nCmdShow) {
    // 解析命令行参数（第一个参数为 .usmind 路径）
    std::wstring fileArg;
    if (cmdLine && *cmdLine) {
        std::wstring cl = cmdLine;
        size_t p = cl.find_first_not_of(L" \t");
        if (p != std::wstring::npos) {
            if (cl[p] == L'"') {
                size_t e = cl.find(L'"', p + 1);
                fileArg = cl.substr(p + 1, e == std::wstring::npos ? std::wstring::npos : e - p - 1);
            } else {
                size_t e = cl.find_first_of(L" \t", p);
                fileArg = cl.substr(p, e == std::wstring::npos ? std::wstring::npos : e - p);
            }
        }
    }

    // 单实例：若已有实例在运行，转发文件并退出
    if (notifyExistingInstance(fileArg)) return 0;
    g_startupFile = fileArg;

    // 启动时注册文件关联（幂等，用户级）
    Settings st = loadSettings();
    registerFileAssoc();
    st.registerAssoc = 1;
    saveSettings(st);

    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);

    WNDCLASSEXW wc = { sizeof(wc) };
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hIcon = LoadIconW(hInst, MAKEINTRESOURCEW(IDI_APP));
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.lpszClassName = L"UsmindMainWnd";
    RegisterClassExW(&wc);

    int sw = GetSystemMetrics(SM_CXSCREEN), sh = GetSystemMetrics(SM_CYSCREEN);
    int ww = 1440, wh = 900;
    if (sw < ww) ww = sw - 40;
    if (sh < wh) wh = sh - 60;
    int x = (sw - ww) / 2, y = (sh - wh) / 2;
    g_hMainWnd = CreateWindowExW(WS_EX_APPWINDOW, L"UsmindMainWnd", L"USMIND 思维导图",
                                 WS_POPUP, x, y, ww, wh, NULL, NULL, hInst, NULL);
    if (!g_hMainWnd) return 1;

    // Win11 圆角
    {
        const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        int pref = 2; // DWMWCP_ROUND
        DwmSetWindowAttribute(g_hMainWnd, DWMWA_WINDOW_CORNER_PREFERENCE, &pref, sizeof(pref));
    }

    ShowWindow(g_hMainWnd, nCmdShow);
    UpdateWindow(g_hMainWnd);

    HRESULT hr = CreateCoreWebView2EnvironmentWithOptions(
        nullptr, nullptr, nullptr, new EnvCompletedHandler());
    if (FAILED(hr)) {
        MessageBoxW(g_hMainWnd,
                    L"未能初始化 WebView2 运行时。\n请确认系统已安装 Microsoft Edge WebView2 Runtime（Win11 自带，Win10 可从微软官网免费安装）。",
                    L"USMIND", MB_OK | MB_ICONERROR);
        DestroyWindow(g_hMainWnd);
    }

    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    CoUninitialize();
    return (int)msg.wParam;
}
