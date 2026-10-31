# Envenom 26.1.2 离线构建脚本（javac + jar，不依赖 Gradle/MDG）
#
# 需要 JDK 25 和 libs-26.1.2\ 目录，编译类路径包括：
#   - minecraft-client-patched-26.1.2.122.jar（NeoForge 的 joined 客户端）
#   - neoforge-26.1.2.122-universal.jar
#   - 官方 libraries + mixinextras
# 这些 jar 不随仓库分发（Mojang / NeoForge 的二进制），请从本地
# NeoForge 开发环境或启动器目录里收集。若网络可用，直接
#   ./gradlew build
# 即可。
#
# 用法：
#   .\build-26.1.2.ps1              # 增量编译 → build-26.1.2\libs\envenom-<ver>+mc26.1.2.jar
#   .\build-26.1.2.ps1 -Clean
#   .\build-26.1.2.ps1 -Clean -Verify

param(
    [switch]$Clean,
    [switch]$Verify,
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'

if (-not $JdkHome) { throw '未设置 JAVA_HOME，也未传 -JdkHome' }
if (-not (Test-Path -LiteralPath $JdkHome)) {
    throw "找不到 JDK 25：$JdkHome（设置环境变量 JAVA_HOME，或传 -JdkHome <路径>）"
}
$Javac   = Join-Path $JdkHome 'bin\javac.exe'
$JarTool = Join-Path $JdkHome 'bin\jar.exe'

$root    = $PSScriptRoot
$libs    = Join-Path $root 'libs-26.1.2'
$build   = Join-Path $root 'build-26.1.2'
$classes = Join-Path $build 'classes'
$outDir  = Join-Path $build 'libs'
$srcJava = Join-Path $root 'src\main\java'
$srcRes  = Join-Path $root 'src\main\resources'
$srcTpl  = Join-Path $root 'src\main\templates'

$props = @{}
Get-Content (Join-Path $root 'gradle.properties') | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.+?)\s*$') { $props[$matches[1]] = $matches[2] }
}
$modId      = $props['mod_id']
$modVersion = $props['mod_version']
$jarPath    = Join-Path $outDir "$modId-$modVersion+mc26.1.2.jar"

if ($Clean -and (Test-Path $build)) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force $classes, $outDir | Out-Null

$libJars = Get-ChildItem $libs -Filter '*.jar' -File
if ($libJars.Count -lt 10) { throw "libs-26.1.2\ 里的 jar 太少（$($libJars.Count) 个）" }
$cp = ($libJars | ForEach-Object FullName) -join ';'
Write-Host "[build] classpath jars = $($libJars.Count)" -ForegroundColor Cyan

$sources = Get-ChildItem $srcJava -Recurse -Filter '*.java' -File | ForEach-Object FullName
Write-Host "[build] sources = $($sources.Count)" -ForegroundColor Cyan

# ── 1) javac（26.1.2 目标 Java 25）─────────────────────────────────────
& $Javac --release 25 -encoding UTF-8 -proc:none -Xlint:all -cp $cp -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac 失败，退出码 $LASTEXITCODE" }

# ── 2) 资源 ────────────────────────────────────────────────────────────
Copy-Item (Join-Path $srcRes '*') $classes -Recurse -Force

# ── 3) 展开 neoforge.mods.toml ─────────────────────────────────────────
$toml = Get-Content (Join-Path $srcTpl 'META-INF\neoforge.mods.toml') -Raw
foreach ($key in $props.Keys) { $toml = $toml.Replace('${' + $key + '}', $props[$key]) }
if ($toml -match '\$\{') { throw "mods.toml 里还有未提供的占位符：$($matches[0])" }
New-Item -ItemType Directory -Force (Join-Path $classes 'META-INF') | Out-Null
Set-Content (Join-Path $classes 'META-INF\neoforge.mods.toml') $toml -Encoding UTF8

# ── 4) MANIFEST ────────────────────────────────────────────────────────
$manifest = Join-Path $build 'MANIFEST.MF'
@(
    'Manifest-Version: 1.0'
    'Specification-Title: Envenom'
    'Specification-Vendor: terminalfoam'
    'Specification-Version: 1'
    'Implementation-Title: envenom'
    "Implementation-Version: $modVersion+mc26.1.2"
    'Implementation-Vendor: terminalfoam'
    ''
) | Set-Content $manifest -Encoding ASCII

# ── 5) jar ─────────────────────────────────────────────────────────────
& $JarTool --create --file $jarPath --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar 打包失败，退出码 $LASTEXITCODE" }

Write-Host ""
Write-Host "[build] 产物：$jarPath" -ForegroundColor Green
Write-Host "[build] 大小：$((Get-Item $jarPath).Length) 字节" -ForegroundColor Green

# ── 6) 自检 ────────────────────────────────────────────────────────────
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    $names = $zip.Entries | ForEach-Object FullName
    foreach ($required in 'META-INF/neoforge.mods.toml', 'META-INF/MANIFEST.MF', 'envenom.mixins.json') {
        if (-not ($names -contains $required)) { throw "jar 缺少 $required" }
    }
    $classCount = ($zip.Entries | Where-Object { $_.FullName -like '*.class' }).Count
    Write-Host "[verify] classes = $classCount, entries = $($zip.Entries.Count)"
} finally { $zip.Dispose() }

if ($Verify) {
    $tomlOut = Get-Content (Join-Path $classes 'META-INF\neoforge.mods.toml') -Raw
    if ($tomlOut -notmatch 'versionRange\s*=\s*"\[26\.1\.2\.122,\)"') {
        throw "mods.toml 的 neoforge 依赖不是 [26.1.2.122,)：`n$tomlOut"
    }
    Write-Host "[verify] neoforge 依赖 = [26.1.2.122,)"

    $mixins = Get-Content (Join-Path $classes 'envenom.mixins.json') -Raw
    if ($mixins -notmatch 'BuiltinTestFunctionsMixin') { throw "mixins.json 缺少 BuiltinTestFunctionsMixin" }
    Write-Host "[verify] mixins.json 含 5 个 mixin"

    $tmp = Join-Path $build 'verify'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    New-Item -ItemType Directory -Force $tmp | Out-Null
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $e = $zip.Entries | Where-Object { $_.FullName -eq 'com/terminalfoam/envenom/Envenom.class' } | Select-Object -First 1
        if (-not $e) { throw "缺少 Envenom.class" }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $tmp 'Envenom.class'), $true)
    } finally { $zip.Dispose() }
    $bytes = [System.IO.File]::ReadAllBytes((Join-Path $tmp 'Envenom.class'))
    $major = [int]$bytes[6] * 256 + [int]$bytes[7]
    Write-Host "[verify] class major version = $major (26.1.2 期望 69)"
    if ($major -ne 69) { throw "class 版本不是 Java 25" }
    Write-Host "[verify] OK" -ForegroundColor Green
}
