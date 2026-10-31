# Envenom 1.21.1 离线构建脚本（javac + jar，不依赖 Gradle/MDG）
#
# 需要 libs-1.21.1\ 目录，里面放编译类路径：
#   - NeoForge 21.1.248 的 merged jar（MC + NeoForge 类）
#   - 21.1 服务端 libraries（authlib / mixin / log4j 等）
# 这些 jar 不随仓库分发（Mojang / NeoForge 的二进制），请从本地
# NeoForge 开发环境或已安装的 21.1 服务端里收集。若网络可用，直接用
#   ./gradlew build
# 即可，不需要本脚本。
#
# 用法：
#   .\build-1.21.1.ps1              # 增量编译 → build-1.21.1\libs\envenom-<ver>.jar
#   .\build-1.21.1.ps1 -Clean
#   .\build-1.21.1.ps1 -Verify

param(
    [switch]$Clean,
    [switch]$Verify,
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'

if (-not $JdkHome) { throw '未设置 JAVA_HOME，也未传 -JdkHome' }
if (-not (Test-Path -LiteralPath $JdkHome)) {
    throw "找不到 JDK：$JdkHome（设置环境变量 JAVA_HOME，或传 -JdkHome <路径>）"
}
$Javac   = Join-Path $JdkHome 'bin\javac.exe'
$JarTool = Join-Path $JdkHome 'bin\jar.exe'

$root    = $PSScriptRoot
$libs    = Join-Path $root 'libs-1.21.1'
$build   = Join-Path $root 'build-1.21.1'
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
$jarPath    = Join-Path $outDir "$modId-$modVersion.jar"

if ($Clean -and (Test-Path $build)) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force $classes, $outDir | Out-Null

$libJars = Get-ChildItem $libs -Filter '*.jar' -File
if ($libJars.Count -lt 8) { throw "libs-1.21.1\ 里的 jar 太少（$($libJars.Count) 个）" }
$cp = ($libJars | ForEach-Object FullName) -join ';'
Write-Host "[build] classpath jars = $($libJars.Count)" -ForegroundColor Cyan

$sources = Get-ChildItem $srcJava -Recurse -Filter '*.java' -File | ForEach-Object FullName
Write-Host "[build] sources = $($sources.Count)" -ForegroundColor Cyan

# ── 1) javac（1.21.1 目标 Java 21）─────────────────────────────────────
& $Javac --release 21 -encoding UTF-8 -proc:none -Xlint:all -cp $cp -d $classes $sources
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
    "Implementation-Version: $modVersion"
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
    Write-Host "[verify] class major version = $major (1.21.1 期望 65)"
    if ($major -ne 65) { throw "class 版本不是 Java 21" }

    & (Join-Path $JdkHome 'bin\javap.exe') -p -cp $jarPath com.terminalfoam.envenom.handler.CoatingEffectHandler |
        Select-String 'onLivingDamagePre|onAttackEntity' | ForEach-Object { "  " + $_.Line.Trim() }
    Write-Host "[verify] OK" -ForegroundColor Green
}
