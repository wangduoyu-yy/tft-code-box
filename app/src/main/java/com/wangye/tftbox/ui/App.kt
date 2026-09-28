package com.wangye.tftbox.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangye.tftbox.data.Lineup
import com.wangye.tftbox.util.Clip
import com.wangye.tftbox.util.LineupShareParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private sealed interface Screen {
    data object List : Screen
    data object Permissions : Screen
    data class Edit(val lineup: Lineup) : Screen
}

@Composable
fun AppRoot(
    viewModel: MainViewModel,
    // 分享进来的文本消费掉之后要清空，所以这里要的是可写的 MutableStateFlow
    sharedText: MutableStateFlow<String?>,
    clipboardText: StateFlow<String?>,
) {
    val needModeChoice by viewModel.needModeChoice.collectAsState()
    val appMode by viewModel.appMode.collectAsState()

    // 第一次打开先让用户挑一个模式，选完记住，之后不再打扰
    if (needModeChoice) {
        ModeChooserScreen(onPick = viewModel::setMode)
        return
    }

    when (appMode) {
        AppMode.TFT -> TftRoot(viewModel, sharedText, clipboardText)

        AppMode.SUDOKU -> {
            var showSettings by remember { mutableStateOf(false) }
            BackHandler(enabled = showSettings) { showSettings = false }
            if (showSettings) {
                PermissionsScreen(viewModel = viewModel, onBack = { showSettings = false })
            } else {
                SudokuScreen(onOpenSettings = { showSettings = true })
            }
        }
    }
}

/** 金铲铲模式下的界面树。原来整个 AppRoot 就是它。 */
@Composable
private fun TftRoot(
    viewModel: MainViewModel,
    sharedText: MutableStateFlow<String?>,
    clipboardText: StateFlow<String?>,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var suggestion by remember { mutableStateOf<LineupShareParser.Parsed?>(null) }

    // 已经提示过、用户关掉了的阵容码，本次会话内不再重复提示
    val dismissed = remember { mutableSetOf<String>() }

    val shared by sharedText.collectAsState()
    val clipboard by clipboardText.collectAsState()

    // 从抖音/小红书「分享」进来的整段文本：解析出阵容名和作者，直接开编辑页
    LaunchedEffect(shared) {
        val parsed = LineupShareParser.parse(shared) ?: return@LaunchedEffect
        screen = Screen.Edit(
            Lineup(
                title = parsed.title.orEmpty(),
                code = parsed.code,
                note = parsed.author?.let { "来源：$it" }.orEmpty(),
            )
        )
        sharedText.value = null
    }

    // 剪贴板里像是阵容码，且库里还没有 → 顶部提示一条
    LaunchedEffect(clipboard) {
        val parsed = LineupShareParser.parse(clipboard) ?: return@LaunchedEffect
        if (parsed.code in dismissed) return@LaunchedEffect
        if (viewModel.existsByCode(parsed.code)) return@LaunchedEffect
        suggestion = parsed
    }

    BackHandler(enabled = screen !is Screen.List) { screen = Screen.List }

    when (val current = screen) {
        is Screen.List -> LineupListScreen(
            viewModel = viewModel,
            suggestion = suggestion,
            onSuggestionDismiss = {
                suggestion?.let { dismissed += it.code }
                suggestion = null
            },
            onSuggestionAccept = {
                val parsed = suggestion
                suggestion = null
                if (parsed != null) {
                    screen = Screen.Edit(
                        Lineup(
                            title = parsed.title.orEmpty(),
                            code = parsed.code,
                            note = parsed.author?.let { "来源：$it" }.orEmpty(),
                        )
                    )
                }
            },
            onAdd = { screen = Screen.Edit(Lineup(title = "", code = "")) },
            onEdit = { screen = Screen.Edit(it) },
            onOpenPermissions = { screen = Screen.Permissions },
        )

        is Screen.Edit -> EditScreen(
            initial = current.lineup,
            onBack = { screen = Screen.List },
            onSave = { viewModel.save(it) { screen = Screen.List } },
        )

        is Screen.Permissions -> PermissionsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.List },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineupListScreen(
    viewModel: MainViewModel,
    suggestion: LineupShareParser.Parsed?,
    onSuggestionDismiss: () -> Unit,
    onSuggestionAccept: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Lineup) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val lineups by viewModel.lineups.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    var pendingDelete by remember { mutableStateOf<Lineup?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "臭宝今天金铲铲了吗",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置与权限")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增阵容")
            }
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize()) {

            suggestion?.let { parsed ->
                SuggestionBanner(
                    parsed = parsed,
                    onAccept = onSuggestionAccept,
                    onDismiss = onSuggestionDismiss,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜名称 / 标签 / 赛季") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            if (lineups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "还没有阵容\n点右下角 + 把阵容码存进来"
                        } else {
                            "没有匹配「$query」的阵容"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lineups, key = { it.id }) { lineup ->
                        LineupCard(
                            lineup = lineup,
                            onEdit = { onEdit(lineup) },
                            onCopy = {
                                Clip.copy(context, lineup.code)
                                Clip.toastCopied(context, lineup.title)
                                Toast.makeText(context, "已复制，回游戏粘贴即可", Toast.LENGTH_SHORT)
                                    .show()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(lineup) },
                            onDelete = { pendingDelete = lineup },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除阵容") },
            text = { Text("确定要删掉「${target.title}」吗？删了就找不回来了。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SuggestionBanner(
    parsed: LineupShareParser.Parsed,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 12.dp, 16.dp, 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parsed.title ?: "剪贴板里发现阵容码",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        parsed.author?.let { append("来源：").append(it).append("  ·  ") }
                        append(parsed.code.take(20))
                        if (parsed.code.length > 20) append("…")
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onAccept) { Text("保存") }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "忽略",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun LineupCard(
    lineup: Lineup,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lineup.favorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = lineup.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCopy) { Text("复制") }
            }

            if (lineup.subtitle.isNotBlank()) {
                Text(
                    text = lineup.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lineup.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = lineup.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = lineup.codePreview,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = if (lineup.favorite) "取消收藏" else "收藏",
                        tint = if (lineup.favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
