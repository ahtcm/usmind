# USMIND 在线版组件打包脚本
# 作用：从 usmind-portable 提取组件 -> 压缩 -> 按 20MB 分片 -> 输出到 release/online-dist/
# 用法: powershell -ExecutionPolicy Bypass -File build_online.ps1

param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [int]$PartSizeMB = 20
)

$ErrorActionPreference = 'Stop'
$port = Join-Path $ProjectRoot 'release\usmind-portable'
$out  = Join-Path $ProjectRoot 'release\online-dist'
$tmp  = Join-Path $ProjectRoot 'release\_online_tmp'

# ---------- 清理 ----------
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Path $out, $tmp -Force | Out-Null

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$PartBytes = $PartSizeMB * 1MB
$manifest = @()

function Compress-Dir([string]$srcDir, [string]$zipPath) {
  if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
  [System.IO.Compression.ZipFile]::CreateFromDirectory($srcDir, $zipPath, [System.IO.Compression.CompressionLevel]::Optimal, $false)
}

function Split-Zip([string]$zipPath, [string]$compName) {
  $fs = [System.IO.File]::OpenRead($zipPath)
  $buf = New-Object byte[] (4MB)
  $idx = 0
  $parts = @()
  try {
    while ($true) {
      $partPath = Join-Path $out ($compName + '.' + $idx.ToString('000'))
      $outFs = [System.IO.File]::Create($partPath)
      $written = 0
      try {
        while ($written -lt $PartBytes) {
          $toRead = [int][Math]::Min($buf.Length, $PartBytes - $written)
          $n = $fs.Read($buf, 0, $toRead)
          if ($n -eq 0) { break }
          $outFs.Write($buf, 0, $n)
          $written += $n
        }
      } finally { $outFs.Close() }
      $parts += @{ file = $compName + '.' + $idx.ToString('000'); size = (Get-Item $partPath).Length }
      $idx++
      if ($written -lt $PartBytes) { break }
    }
  } finally { $fs.Close() }
  return ,$parts
}

Write-Host '== [1/6] 压缩 core (USMIND.exe + app) =='
$coreDir = Join-Path $tmp 'core'
New-Item -ItemType Directory -Path $coreDir -Force | Out-Null
Copy-Item (Join-Path $port 'USMIND.exe') $coreDir -Force
Copy-Item (Join-Path $port 'app') $coreDir -Recurse -Force
$coreZip = Join-Path $tmp 'core.zip'
Compress-Dir $coreDir $coreZip
$manifest += @{ comp='core'; dir='.'; parts=(Split-Zip $coreZip 'core') }

Write-Host '== [2/6] 压缩 jre (runtime) =='
$jreZip = Join-Path $tmp 'jre.zip'
Compress-Dir (Join-Path $port 'runtime') $jreZip
$manifest += @{ comp='jre'; dir='runtime'; parts=(Split-Zip $jreZip 'jre') }

Write-Host '== [3/6] 压缩 mysql (components/mysql) =='
$mysqlZip = Join-Path $tmp 'mysql.zip'
Compress-Dir (Join-Path $port 'components\mysql') $mysqlZip
$manifest += @{ comp='mysql'; dir='components\mysql'; parts=(Split-Zip $mysqlZip 'mysql') }

Write-Host '== [4/6] 压缩 redis =='
$redisZip = Join-Path $tmp 'redis.zip'
Compress-Dir (Join-Path $port 'components\redis') $redisZip
$manifest += @{ comp='redis'; dir='components\redis'; parts=(Split-Zip $redisZip 'redis') }

Write-Host '== [5/6] 压缩 nginx (含前端页面) =='
$nginxZip = Join-Path $tmp 'nginx.zip'
Compress-Dir (Join-Path $port 'components\nginx') $nginxZip
$manifest += @{ comp='nginx'; dir='components\nginx'; parts=(Split-Zip $nginxZip 'nginx') }

Write-Host '== [6/6] 压缩 backend (jar + init.sql) =='
$backendDir = Join-Path $tmp 'backend'
New-Item -ItemType Directory -Path $backendDir -Force | Out-Null
Copy-Item (Join-Path $port 'components\usmind-backend.jar') $backendDir -Force
Copy-Item (Join-Path $port 'components\init.sql') $backendDir -Force
$backendZip = Join-Path $tmp 'backend.zip'
Compress-Dir $backendDir $backendZip
$manifest += @{ comp='backend'; dir='components'; parts=(Split-Zip $backendZip 'backend') }

# ---------- manifest.json ----------
$manifestJson = $manifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path $out 'manifest.json'), $manifestJson, (New-Object System.Text.UTF8Encoding $false))

# ---------- 汇总 ----------
$totalBytes = 0
foreach ($m in $manifest) { foreach ($p in $m.parts) { $totalBytes += $p.size } }
Write-Host ('完成: {0} 个分片, 共 {1:N1} MB' -f ($manifest | ForEach-Object { $_.parts.Count } | Measure-Object -Sum).Sum, ($totalBytes / 1MB))

# ---------- 清理 ----------
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
