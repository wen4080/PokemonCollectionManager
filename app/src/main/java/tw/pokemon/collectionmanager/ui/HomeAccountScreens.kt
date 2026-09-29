package tw.pokemon.collectionmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.pokemon.collectionmanager.data.local.AccountGroupEntity
import tw.pokemon.collectionmanager.data.local.AccountSummaryRow

@Composable
fun AccountsScreen(viewModel: CollectionViewModel, onOpenAccount: (String) -> Unit) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountSummaryRow?>(null) }
    var showGroups by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountSummaryRow?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            overscrollEffect = null,
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PageTitle(
                    "帳號",
                    subtitle = "區分收藏版本數量與實際 Pokémon 總隻數。封存帳號不會出現在預設首頁。",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = null; showEditor = true }) { Text("＋ 新增帳號") }
                    OutlinedButton(onClick = { showGroups = true }) { Text("管理群組") }
                }
            }
            if (accounts.isEmpty()) {
                item { EmptyState("還沒有帳號", "先建立主帳或小帳，收藏才知道屬於哪個帳號。", "建立帳號") { showEditor = true } }
            } else {
                items(accounts, key = { it.id }) { account ->
                    AccountListCard(
                        account = account,
                        onClick = { if (!account.isArchived) onOpenAccount(account.id) },
                        onEdit = { editing = account; showEditor = true },
                        onArchive = { viewModel.archiveAccount(account.id, !account.isArchived) },
                        onDelete = { deleting = account },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; showEditor = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Text("＋", style = MaterialTheme.typography.headlineSmall) }
    }

    if (showEditor) {
        AccountEditorDialog(
            account = editing,
            groups = groups,
            onDismiss = { showEditor = false },
            onSave = { id, name, nickname, groupId ->
                if (id == null) viewModel.addAccount(name, nickname, groupId)
                else viewModel.updateAccount(id, name, nickname, groupId)
                showEditor = false
            },
        )
    }
    if (showGroups) GroupManagerDialog(viewModel, groups) { showGroups = false }
    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除 ${account.name}？") },
            text = { Text("此帳號目前有 ${account.variantCount} 種收藏版本、${account.pokemonCount} 隻 Pokémon。刪除後會一併移除其佔有數量，無法復原。") },
            confirmButton = {
                Button(onClick = { viewModel.deleteAccount(account.id); deleting = null }) { Text("確定刪除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AccountListCard(
    account: AccountSummaryRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !account.isArchived, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (account.isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(account.groupName ?: "未分組", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (account.isArchived) Text("已封存", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text("${account.variantCount} 種收藏版本 · ${account.pokemonCount} 隻 Pokémon", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("編輯") }
                OutlinedButton(onClick = onArchive) { Text(if (account.isArchived) "恢復" else "封存") }
                TextButton(onClick = onDelete) { Text("刪除") }
            }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    account: AccountSummaryRow?,
    groups: List<AccountGroupEntity>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String?, String?) -> Unit,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var nickname by remember(account?.id) { mutableStateOf(account?.nickname.orEmpty()) }
    var groupId by remember(account?.id) { mutableStateOf(account?.groupId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新增帳號" else "編輯帳號") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("帳號名稱") }, singleLine = true)
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("暱稱（選填）") }, singleLine = true)
                Text("帳號群組", style = MaterialTheme.typography.labelLarge)
                HorizontalChoices {
                    ChoiceChip("未分組", groupId == null) { groupId = null }
                    groups.forEach { group -> ChoiceChip(group.name, groupId == group.id) { groupId = group.id } }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.trim().isNotEmpty(), onClick = { onSave(account?.id, name, nickname, groupId) }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GroupManagerDialog(viewModel: CollectionViewModel, groups: List<AccountGroupEntity>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("帳號群組") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("新群組名稱") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.addGroup(name); name = "" }, enabled = name.trim().isNotEmpty()) { Text("新增") }
                }
                groups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.deleteGroup(group.id) }) { Text("刪除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
fun EmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

