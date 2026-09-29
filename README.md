# Pokémon Collection Manager

Pokémon GO 多帳號稀有收藏管理器。這是一個完全 Local-First 的 Kotlin、Jetpack Compose、Room、DataStore 與 Material 3 Android App，不需要登入 Pokémon GO，也不依賴後端。

## 本機建置

本專案可使用 Android Studio 或 Windows 命令列建置。若工具鏈位置不同，請調整 `local.properties` 的 `sdk.dir`，並設定可用的 JDK 17 與 Gradle。

```powershell
gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

產物位於 `app/build/outputs/apk/debug/app-debug.apk`。

## 資料設計

`Account -> CollectionVariant -> OwnershipBucket -> quantity` 是核心關係。Variant 的 canonical `variantKey` 包含 species、form、costume、background、shiny、gender、shadow state 與 dynamax state；尺寸、特招與交換狀態只會形成 Ownership Bucket，不會拆出新的 Variant。
