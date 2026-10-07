// usmind 本地版 - WebView2 宿主对象桥接
// 实现 IDispatch，将 C++ 能力以 window.chrome.webview.hostObjects.usmind.* 暴露给前端
#pragma once

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <objbase.h>
#include <oaidl.h>
#include <string>

// 由 main.cpp 定义
extern std::wstring g_startupFile; // 命令行传入的 .usmind 路径
extern HWND g_hMainWnd;            // 主窗口（更新标题用）
extern void (*g_onFileOpenRequest)(const std::wstring& path);

class UsmindBridge : public IDispatch {
public:
    UsmindBridge();
    virtual ~UsmindBridge();

    // IUnknown
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override;
    STDMETHODIMP_(ULONG) AddRef() override;
    STDMETHODIMP_(ULONG) Release() override;

    // IDispatch
    STDMETHODIMP GetTypeInfoCount(UINT* pctinfo) override;
    STDMETHODIMP GetTypeInfo(UINT iTInfo, LCID lcid, ITypeInfo** ppTInfo) override;
    STDMETHODIMP GetIDsOfNames(REFIID riid, LPOLESTR* rgszNames, UINT cNames, LCID lcid, DISPID* rgDispId) override;
    STDMETHODIMP Invoke(DISPID dispIdMember, REFIID riid, LCID lcid, WORD wFlags,
                        DISPPARAMS* pDispParams, VARIANT* pVarResult,
                        EXCEPINFO* pExcepInfo, UINT* puArgErr) override;

    // 当前打开的文件
    std::wstring currentPath;   // 空 = 未保存新文档
    std::wstring currentName;   // 文件名（不含路径）

private:
    ULONG refCount_;

    // 方法分发（dispid 1..N）
    void DoInvoke(int method, DISPPARAMS* p, VARIANT* result);
};
