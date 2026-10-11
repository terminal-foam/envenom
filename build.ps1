# 构建脚本（离线优先）
#
# 用法：
#   .\build.ps1              # 构建
#   .\build.ps1 -Clean       # 先清理再构建
#   .\build.ps1 -Online      # 允许联网（首次预热依赖时用）
#
# 为什么固定 JAVA_HOME：NeoForge moddev 插件要求 JDK 21，而系统 PATH 上的 java 未必是 21。
# 为什么默认 --offline：本机 Gradle 缓存已含全部依赖，离线构建可复现且不依赖网络。

param(
    [switch]$Clean,
    [switch]$Online
)

$ErrorActionPreference = 'Stop'

# ── 工具链（如换机器请改这两行）──
$JdkHome     = 'E:\JDK21'
$GradleUser  = Join-Path $env:USERPROFILE '.gradle'

if (-not (Test-Path -LiteralPath $JdkHome)) {
    throw "找不到 JDK：$JdkHome —— 请改本脚本顶部的 `$JdkHome"
}

$env:JAVA_HOME       = $JdkHome
$env:GRADLE_USER_HOME = $GradleUser

$root = $PSScriptRoot
Push-Location $root
try {
    # ⚠️ 任务必须在开关【之前】。之前把 'clean' 追加到末尾，Gradle 会把它当成
    # 紧跟在 'build' 后面的第二个任务，而 --offline 之类的开关插在中间会导致
    # 任务顺序不符合预期（实测出现 ':build UP-TO-DATE' 而 clean 反而删掉了产物）。
    $args = @()
    if ($Clean) { $args += 'clean' }
    $args += @('build', '--no-configuration-cache', '--console=plain')
    if (-not $Online) { $args += '--offline' }

    Write-Host "[build] JAVA_HOME        = $env:JAVA_HOME"       -ForegroundColor Cyan
    Write-Host "[build] GRADLE_USER_HOME = $env:GRADLE_USER_HOME" -ForegroundColor Cyan
    Write-Host "[build] 参数              = $($args -join ' ')"   -ForegroundColor Cyan

    & "$root\gradlew.bat" @args
    if ($LASTEXITCODE -ne 0) { throw "构建失败，退出码 $LASTEXITCODE" }

    # 自动找产物，不硬编码版本号 —— 改 gradle.properties 的 mod_version 后这里无需同步。
    # 排除 -sources / -javadoc 之类的附属 jar，只认主 jar。
    $jar = Get-ChildItem -LiteralPath (Join-Path $root 'build\libs') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
           Sort-Object LastWriteTime -Descending |
           Select-Object -First 1 -ExpandProperty FullName
    if ($jar -and (Test-Path -LiteralPath $jar)) {
        $item = Get-Item -LiteralPath $jar
        Write-Host ""
        Write-Host "[build] 产物：$jar" -ForegroundColor Green
        Write-Host "[build] 大小：$($item.Length) 字节" -ForegroundColor Green
    }
}
finally {
    Pop-Location
}
