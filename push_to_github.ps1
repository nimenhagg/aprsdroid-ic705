param (
    [string]$RemoteUrl = ""
)

$RepoDir = "C:\Users\Administrator\Documents\Codex\2026-08-20\android-1-ft8cn-https-github-com\work\sources\aprsdroid_repo"
Set-Location $RepoDir

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "   APRSdroid + IC-705 Wi-Fi 一键推送到 GitHub 脚本   " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($RemoteUrl)) {
    Write-Host "请在 GitHub 上 Fork 或新建仓库，然后输入您的仓库地址：" -ForegroundColor Yellow
    Write-Host "例如: git@github.com:your-username/aprsdroid.git" -ForegroundColor Gray
    Write-Host "或者: https://github.com/your-username/aprsdroid.git" -ForegroundColor Gray
    Write-Host ""
    $RemoteUrl = Read-Host "请输入您的 GitHub 仓库 Remote URL"
}

if ([string]::IsNullOrWhiteSpace($RemoteUrl)) {
    Write-Host "[错误] Remote URL 不能为空！" -ForegroundColor Red
    exit 1
}

$existing = git remote get-url myorigin 2>$null
if ($existing) {
    git remote set-url myorigin $RemoteUrl
} else {
    git remote add myorigin $RemoteUrl
}

Write-Host "`n[1/2] 正在配置远程仓库: myorigin -> $RemoteUrl" -ForegroundColor Green
Write-Host "[2/2] 正在推送当前分支到 GitHub (git push -u myorigin master)..." -ForegroundColor Green

git push -u myorigin master

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[成功] 代码已成功推送到您的 GitHub 仓库！" -ForegroundColor Cyan
} else {
    Write-Host "`n[提示] 推送遇到问题，请检查您的 GitHub 权限（SSH 密钥或 Personal Access Token）。" -ForegroundColor Yellow
}
