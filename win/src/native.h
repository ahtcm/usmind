// usmind 本地版 - 原生能力层
// 文件读写 / 历史版本 / 设置 / 对话框 / 文件关联 / 单实例
#pragma once

#include <string>
#include <vector>

namespace usmind {

struct FileInfo {
    std::wstring path;
    std::wstring name;
};

struct Settings {
    std::wstring historyDir;    // 历史目录（空 = 默认 exe/history）
    std::wstring defaultTheme;  // 默认主题名
    int          registerAssoc; // 1 = 已注册 .usmind 文件关联
    std::wstring lastFile;      // 上次打开的文件
};

struct HistoryItem {
    std::wstring id;    // 文件名（不含扩展名）
    std::wstring time;  // 可读时间
    std::wstring name;  // 对应导图名
    long long    size;  // 字节
};

// 应用根目录（exe 所在目录，运行时计算，代码内无绝对路径）
std::wstring appDir();
// 版本号
std::wstring appVersion();

// ---- 设置 ----
Settings loadSettings();
void saveSettings(const Settings& s);

// ---- 文件 ----
bool readFile(const std::wstring& path, std::string& out); // UTF-8
bool writeFile(const std::wstring& path, const std::string& data);
bool fileExists(const std::wstring& path);
std::wstring fileNameOf(const std::wstring& path);

// ---- 历史 ----
std::wstring historyDir();
bool ensureHistoryDir();
// 保存前把 srcPath 现有内容快照到历史目录（新文件无旧内容则跳过）
bool snapshotHistory(const std::wstring& srcPath, const std::wstring& docName);
std::vector<HistoryItem> listHistory();
bool readHistoryItem(const std::wstring& id, std::string& out);
bool clearHistory();
bool setHistoryDir(const std::wstring& dir);

// ---- 原生对话框 ----
bool pickOpenFile(std::wstring& outPath, std::string& outData); // 打开 .usmind
bool pickAnyFile(std::wstring& outName, std::string& outB64); // 选任意文件返回base64
bool pickSaveFile(const std::wstring& suggested, std::wstring& outPath);
// 通用另存为（导出用）：初始目录+文件名，任意扩展名
bool pickSaveFileAny(const std::wstring& defDir, const std::wstring& defName, std::wstring& outPath);
// 目录选择（历史目录设置用）
bool pickFolder(std::wstring& out);
std::wstring dirOf(const std::wstring& path);

// ---- 文件关联 ----
void registerFileAssoc();

// ---- 单实例 ----
// 若已有实例在运行：向其发送打开请求并返回 true；否则返回 false
bool notifyExistingInstance(const std::wstring& fileArg);

} // namespace usmind
