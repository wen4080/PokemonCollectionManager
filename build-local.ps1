$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) {
    throw '請先設定 JAVA_HOME（JDK 17）。'
}
& '.\gradlew.bat' testDebugUnitTest assembleDebug lintDebug --console=plain
