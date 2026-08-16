# DSH Mobile 发布脚本：构建最新 APK 并同步到 HTTP 下载服务器
# 用法: powershell -ExecutionPolicy Bypass -File scripts\publish.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

Write-Host "=== 1/3 构建 Debug APK ==="
& .gradlew.bat :androidApp:assembleDebug --console=plain | Out-Null
if ($LASTEXITCODE -ne 0) { throw "构建失败" }

$apk = "$root\androidApp\build\outputs\apk\debug"
Copy-Item "$apk\androidApp-debug.apk" "$apk\dsh-mobile.apk" -Force

Write-Host "=== 2/3 检查下载服务器 (8899) ==="
$check = try {
    (Invoke-WebRequest -Uri "http://127.0.0.1:8899/dsh-mobile.apk" -Method Head -UseBasicParsing -TimeoutSec 5).Headers["Content-Length"]
} catch { $null }
if ($check -eq $null) {
    Write-Host "下载服务器未运行，启动 node 服务器…"
    $job = Start-Process -FilePath "node" -ArgumentList "-e", "const http=require('http'),fs=require('fs');const path='$apk/dsh-mobile.apk';http.createServer((req,res)=>{if(req.url==='/dsh-mobile.apk'){res.writeHead(200,{'Content-Type':'application/vnd.android.package-archive','Content-Length':fs.statSync(path).size});fs.createReadStream(path).pipe(res);}else{res.writeHead(404);res.end();}}).listen(8899,'0.0.0.0');" -WindowStyle Hidden -PassThru
    Write-Host "服务器已启动 (PID $($job.Id))"
} else {
    Write-Host "服务器正常，已提供最新版: $check bytes"
}

$size = (Get-Item "$apk\dsh-mobile.apk").Length
Write-Host "=== 3/3 完成 ==="
Write-Host "手机下载: http://<Tailscale-IP>:8899/dsh-mobile.apk  ($size bytes)"