# usmind 本地版 - 一键构建脚本
# 用法：右键"使用 PowerShell 运行"，或 powershell -ExecutionPolicy Bypass -File build.ps1
# 全部使用相对路径（相对本脚本所在目录），代码中无绝对路径
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$toolchain = Join-Path $root "tools\w64devkit\w64devkit"
$gpp    = Join-Path $toolchain "bin\g++.exe"
$windres= Join-Path $toolchain "bin\windres.exe"
$sdkInc = Join-Path $root "tools\webview2-sdk\build\native\include"
$sdkLib = Join-Path $root "tools\webview2-sdk\build\native\x64\WebView2Loader.dll.lib"
$sdkDll = Join-Path $root "tools\webview2-sdk\build\native\x64\WebView2Loader.dll"

$src  = Join-Path $root "src"
$out  = Join-Path $root "build"
$web  = Join-Path $root "web"
$rel  = Join-Path $root "release"

if (-not (Test-Path $gpp)) { Write-Host "[错误] 未找到工具链，请先下载 w64devkit 到 tools\ 目录" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $sdkLib)) { Write-Host "[错误] 未找到 WebView2 SDK，请先放入 tools\webview2-sdk" -ForegroundColor Red; exit 1 }

New-Item -ItemType Directory -Force $out | Out-Null

# 生成 16 套内置主题（web/js/themes.js，需 Python 环境；若已存在则跳过）
$themesJs = Join-Path $web "js\themes.js"
if (-not (Test-Path $themesJs)) {
    Write-Host "==> 生成内置主题 (themes.js)" -ForegroundColor Cyan
    $py = Get-Command python -ErrorAction SilentlyContinue
    if ($py) {
        & python (Join-Path $root "build\gen_themes.py")
        if ($LASTEXITCODE -ne 0) { Write-Host "[警告] 主题生成失败，将沿用现有 themes.js（若无则主题为空）" -ForegroundColor Yellow }
    } else {
        Write-Host "[警告] 未找到 Python，跳过主题生成（需要 web/js/themes.js）" -ForegroundColor Yellow
    }
}

Write-Host "==> 编译资源文件" -ForegroundColor Cyan
& $windres (Join-Path $src "app.rc") -O coff -o (Join-Path $out "app.res")
if ($LASTEXITCODE -ne 0) { Write-Host "[错误] 资源编译失败" -ForegroundColor Red; exit 1 }

Write-Host "==> 编译 C++ 壳程序" -ForegroundColor Cyan
& $gpp -std=c++17 -O2 -municode -mwindows `
    (Join-Path $src "main.cpp") (Join-Path $src "bridge.cpp") (Join-Path $src "native.cpp") (Join-Path $out "app.res") `
    -I $sdkInc `
    $sdkLib `
    -lole32 -loleaut32 -lcomdlg32 -ladvapi32 -lshell32 -luuid -lshlwapi -ldwmapi -luser32 -lgdi32 `
    -o (Join-Path $out "usmind.exe")
if ($LASTEXITCODE -ne 0) { Write-Host "[错误] C++ 编译失败" -ForegroundColor Red; exit 1 }

Write-Host "==> 组装发布目录" -ForegroundColor Cyan
New-Item -ItemType Directory -Force $rel | Out-Null
Copy-Item -Path (Join-Path $out "usmind.exe") -Destination $rel -Force
Copy-Item -Path $sdkDll -Destination $rel -Force
if (Test-Path (Join-Path $rel "web")) { Remove-Item (Join-Path $rel "web") -Recurse -Force }
Copy-Item -Path $web -Destination $rel -Recurse -Force

$exeSize = (Get-Item (Join-Path $rel "usmind.exe")).Length
Write-Host ""
Write-Host "构建成功 ✓" -ForegroundColor Green
Write-Host "  可执行文件: $rel\usmind.exe  ($([math]::Round($exeSize/1KB)) KB)"
Write-Host "  前端资源:   $rel\web"
Write-Host ""
Write-Host "发布目录为绿色便携版：复制整个 release 目录即可分发，无需安装。"
