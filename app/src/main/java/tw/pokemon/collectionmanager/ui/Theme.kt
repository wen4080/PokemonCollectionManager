package tw.pokemon.collectionmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import tw.pokemon.collectionmanager.data.local.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF635BFF),
    secondary = Color(0xFF00796B),
    tertiary = Color(0xFFE66B2E),
    background = Color(0xFFF9F8FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B3FF),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFB38A),
)

@Composable
fun CollectionTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // 收藏牆、背景清單與設定頁在快速滑到底部時不需要邊界拉伸效果。
    // 將過度滑動關閉，避免畫面先被拉伸再回彈，造成不穩定的視覺與觸控感受。
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

