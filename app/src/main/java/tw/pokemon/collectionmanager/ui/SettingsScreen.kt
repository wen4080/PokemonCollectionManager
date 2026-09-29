package tw.pokemon.collectionmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.pokemon.collectionmanager.data.local.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: CollectionViewModel,
    appVersion: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportMasterData: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val species by viewModel.species.collectAsStateWithLifecycle()
    val backgrounds by viewModel.backgrounds.collectAsStateWithLifecycle()
    val costumes by viewModel.costumes.collectAsStateWithLifecycle()
    val masterDataMeta by viewModel.masterDataMeta.collectAsStateWithLifecycle()
    val savedUpdateUrl by viewModel.masterDataUpdateUrl.collectAsStateWithLifecycle()
    val automaticUpdate by viewModel.automaticMasterDataUpdateEnabled.collectAsStateWithLifecycle()
    val updateProgress by viewModel.masterDataUpdateProgress.collectAsStateWithLifecycle()
    val bundledBackgroundImageCount by viewModel.bundledBackgroundImageCount.collectAsStateWithLifecycle()
    var updateUrl by remember { mutableStateOf(savedUpdateUrl) }
    LaunchedEffect(savedUpdateUrl) { if (updateUrl != savedUpdateUrl) updateUrl = savedUpdateUrl }

    val speciesCount = masterDataMeta?.speciesCount ?: species.size
    val formCount = masterDataMeta?.formCount ?: 0
    val costumeCount = masterDataMeta?.costumeCount ?: costumes.size
    val backgroundCount = masterDataMeta?.backgroundCount ?: backgrounds.size
    val imageCount = masterDataMeta?.imageCount ?: 0
    val missingImageCount = masterDataMeta?.missingImageCount ?: 0
    val actualCostumeCount = costumes.count { it.costumeKey != "NONE" }
    val backgroundImageCount = backgrounds.count { !it.imageKey.isNullOrBlank() }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle("設定", subtitle = "所有資料預設只留在這台手機。")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("外觀", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.label) })
                    }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("備份 / 還原", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("備份會包含帳號、群組、收藏組合與數量，不會包含任何 Pokémon GO 登入資料。", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Text("匯出備份") }
                    OutlinedButton(onClick = onImport) { Text("匯入備份") }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("主資料與圖片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "目前資料：${speciesCount} 種 Pokémon、${formCount} 種型態；裝扮 ${costumeCount} 項（含無裝扮，實際裝扮 ${actualCostumeCount} 項）；背景 ${backgroundCount} 項（含無背景，${backgroundImageCount} 項有圖片索引，本機已收錄 ${bundledBackgroundImageCount} 項）。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "圖片索引：${imageCount} 筆；上游資料中無可用圖片對應：${missingImageCount} 筆。應用程式會依序使用本機圖片、手機快取及靜態圖片來源；更新主資料不會刪除既有收藏。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportMasterData) { Text("匯入主資料檔案") }
                }
                Text("自主更新", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "此欄需要可直接下載的主資料檔，不能貼一般網站首頁。PokeMiners、圖片站與百科的資料格式不同，仍須先由產生工具整理成 App 可驗證的格式；更新只增修主資料，不會刪除既有收藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "目前建置資料來源：PokeMiners 遊戲主資料、PokeMiners 二維圖片資產；完整背卡預覽另以 Dittobase 與 Bulbagarden Archives 交叉補充。這些是不同來源，必須先由 Windows 產生工具整理後才能供 App 更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "只需由資料維護者設定一個共用更新來源，其他使用者直接使用同一網址，不需要 GitHub 帳號。專案已附 GitHub Pages 每日產生流程；也可改用任何能提供 HTTPS 靜態檔案的空間。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = updateUrl,
                    onValueChange = { updateUrl = it },
                    label = { Text("主資料更新網址") },
                    placeholder = { Text("請貼上安全的主資料檔案網址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("每天自動檢查")
                        Text("只會在有設定網址時執行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = automaticUpdate, onCheckedChange = viewModel::setAutomaticMasterDataUpdateEnabled)
                }
                updateProgress?.let { progress ->
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(progress.stage, fontWeight = FontWeight.Bold)
                                Text(
                                    progress.fraction?.let { "${(it * 100).toInt()}%" } ?: "處理中",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (progress.fraction == null) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress.fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(progress.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.setMasterDataUpdateUrl(updateUrl) }) { Text("已儲存更新網址") }
                    Button(enabled = updateProgress == null, onClick = { viewModel.checkMasterDataUpdate(updateUrl) }) {
                        Text(if (updateProgress == null) "立即檢查更新" else "更新中…")
                    }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("帳號群組", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (groups.isEmpty()) Text("尚未建立群組", color = MaterialTheme.colorScheme.onSurfaceVariant)
                groups.forEach { Text("• ${it.name}") }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("資料庫資訊", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("資料庫結構版本：6")
                Text("主資料版本：${masterDataMeta?.masterVersion ?: "尚未匯入"}")
                Text("應用程式版本：$appVersion")
                Spacer(Modifier.padding(2.dp))
                Text("本應用程式不會自動登入、讀取或操作 Pokémon GO 帳號。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

