# 大猪吉他练习助手 - PowerShell 一键启动
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  大猪吉他练习助手 - 一键启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Java
try {
    $javaVersion = java -version 2>&1
    Write-Host "[OK] Java 已安装" -ForegroundColor Green
} catch {
    Write-Host "[错误] 未检测到 Java，请先安装 Java 8 或更高版本" -ForegroundColor Red
    Write-Host "下载地址: https://www.oracle.com/java/technologies/downloads/" -ForegroundColor Yellow
    Read-Host "按回车键退出"
    exit 1
}

# 检查 Maven
$mavenCmd = $null
try {
    $mvnVersion = mvn -version 2>&1
    $mavenCmd = "mvn"
    Write-Host "[OK] Maven 已安装" -ForegroundColor Green
} catch {
    if (Test-Path "mvnw.cmd") {
        $mavenCmd = ".\mvnw.cmd"
        Write-Host "[提示] 使用 Maven Wrapper" -ForegroundColor Yellow
    } else {
        Write-Host "[错误] 未找到 Maven，请安装 Maven 或下载 mvnw" -ForegroundColor Red
        Read-Host "按回车键退出"
        exit 1
    }
}

Write-Host ""
Write-Host "[1/3] 正在清理旧文件..." -ForegroundColor Cyan
& $mavenCmd clean | Out-Null

Write-Host "[2/3] 正在编译项目..." -ForegroundColor Cyan
$compileResult = & $mavenCmd compile -q 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[错误] 编译失败，请检查代码是否有误" -ForegroundColor Red
    Write-Host $compileResult -ForegroundColor Red
    Write-Host ""
    Read-Host "按回车键退出"
    exit 1
}

Write-Host "[3/3] 启动服务..." -ForegroundColor Cyan
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  服务已启动！" -ForegroundColor Green
Write-Host "  主页: http://localhost:8080" -ForegroundColor Yellow
Write-Host "  调音器: http://localhost:8080/tuner" -ForegroundColor Yellow
Write-Host "  按 Ctrl+C 可停止服务" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# 启动 Spring Boot
& $mavenCmd spring-boot:run

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[错误] 服务启动失败" -ForegroundColor Red
    Write-Host ""
    Read-Host "按回车键退出"
}
