package com.wangye.tftbox.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangye.tftbox.R
import com.wangye.tftbox.overlay.FloatingService
import com.wangye.tftbox.util.Perms
import com.wangye.tftbox.util.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 权限与开关。
 *
 * 悬浮球这一路要的权限不少，而且没有一个是能一次性搞定的：
 * 悬浮窗要去系统设置手点、通知要运行时申请、电池优化又是另一个设置页。
 * 所以这里把状态全列出来，缺哪个点哪个，别让用户猜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 从系统设置页回来时要重新读一遍权限状态，用这个计数强制重组
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    val canOverlay = remember(refreshKey) { Perms.canDrawOverlay(context) }
    val ignoringBattery = remember(refreshKey) { Perms.isIgnoringBatteryOptimizations(context) }
    val hasNotification = remember(refreshKey) { Perms.hasNotificationPermission(context) }
    val serviceRunning = remember(refreshKey) { FloatingService.isRunning }
    val ballAttached = remember(refreshKey) { FloatingService.ballAttached }
    val lastError = remember(refreshKey) { FloatingService.lastError }

    var autoStart by remember { mutableStateOf(Prefs.isAutoStart(context)) }
    var watchClipboard by remember { mutableStateOf(Prefs.isWatchClipboard(context)) }

    val themeMode by viewModel.themeMode.collectAsState()
    val paletteId by viewModel.paletteId.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    fun openSafely(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置与权限", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            UsageCard()

            SectionTitle("开悬浮球之前")

            PermissionRow(
                title = "悬浮窗权限",
                desc = "必须。没有它悬浮球根本显示不出来。",
                granted = canOverlay,
                actionLabel = "去开启",
                onAction = { openSafely(Perms.overlaySettingsIntent(context)) },
            )

            PermissionRow(
                title = "通知权限",
                desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "悬浮球靠常驻通知保命，建议给。"
                } else {
                    "当前系统版本不需要单独申请。"
                },
                granted = hasNotification,
                actionLabel = "去开启",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            PermissionRow(
                title = "电池优化白名单",
                desc = "不加入的话，锁屏久了或切后台久了悬浮球会被系统清掉。",
                granted = ignoringBattery,
                actionLabel = "去设置",
                onAction = { openSafely(Perms.batterySettingsIntent(context)) },
            )

            if (Perms.isOemWithHiddenSettings) {
                OemHintCard(
                    onOpenAutostart = {
                        if (Perms.isXiaomi()) {
                            runCatching {
                                context.startActivity(Perms.xiaomiBackgroundStartIntent())
                            }.onFailure {
                                openSafely(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                )
                            }
                        } else {
                            openSafely(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    },
                )
            }

            SectionTitle("悬浮球")

            SwitchRow(
                title = "开启悬浮球",
                desc = if (canOverlay) {
                    "打开 App 自动挂上，把 App 从后台划掉自动收掉，不用每次手动点。"
                } else {
                    "需要先授予上面的悬浮窗权限。"
                },
                checked = serviceRunning,
                enabled = canOverlay,
                onCheckedChange = { want ->
                    // 这一个开关管两件事：现在开不开，以及下次打开 App 要不要自动开
                    Prefs.setAutoBall(context, want)
                    runCatching {
                        if (want) FloatingService.start(context) else FloatingService.stop(context)
                    }
                    // 启停是异步的：立刻读 isRunning 必然读到旧值，开关会自己弹回去。
                    // 轮询几次，等状态真的对上了再停。
                    scope.launch {
                        repeat(15) {
                            delay(200)
                            refreshKey++
                            if (FloatingService.isRunning == want) return@launch
                        }
                    }
                },
            )

            SwitchRow(
                title = "开机自动开启",
                desc = "重启手机后不用先开 App 就有球。一般用不上——打开 App 本来就会自动开。",
                checked = autoStart,
                enabled = true,
                onCheckedChange = {
                    autoStart = it
                    Prefs.setAutoStart(context, it)
                },
            )

            SectionTitle("外观")

            AppearanceCard(
                themeMode = themeMode,
                paletteId = paletteId,
                onModeChange = viewModel::setThemeMode,
                onPaletteChange = viewModel::setPaletteId,
            )

            SectionTitle("自检")

            DiagCard(
                rows = listOf(
                    "版本" to appVersion(context),
                    "系统" to "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}" +
                        " (API ${Build.VERSION.SDK_INT})",
                    "悬浮窗权限" to if (canOverlay) "已授予" else "未授予 ← 球出不来就是这个原因",
                    "通知权限" to if (hasNotification) "已授予" else "未授予（不影响球显示）",
                    "电池优化白名单" to if (ignoringBattery) "已加入" else "未加入（球容易被系统清掉）",
                    "前台服务" to if (serviceRunning) "运行中" else "未运行 ← 开关没真正打开",
                    "悬浮球挂载" to if (ballAttached) "已挂在屏幕上" else "未挂上",
                    "最后错误" to (lastError ?: "无"),
                ),
                ok = canOverlay && serviceRunning && ballAttached,
            )

            SectionTitle("导入")

            SwitchRow(
                title = "自动检测剪贴板",
                desc = "打开 App 时如果剪贴板里像是一串阵容码，顶部会提示你保存。" +
                    "（系统可能会弹一个「读取剪贴板」的提示，属正常）",
                checked = watchClipboard,
                enabled = true,
                onCheckedChange = {
                    watchClipboard = it
                    Prefs.setWatchClipboard(context, it)
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UsageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "怎么用",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            listOf(
                "① 回到上一页，用 + 把网上的阵容码存进来，起个好认的名字",
                "② 这里开好悬浮球，看到通知栏出现「悬浮球已开启」就成了",
            ).forEach { StepLine(it) }

            // ③ 直接在文字里嵌一颗悬浮球的小图，省得用户还要猜是哪个
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepLine("③ 进游戏，点屏幕边上的")
                Image(
                    painter = painterResource(R.drawable.ball_face),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(18.dp)
                        .clip(CircleShape),
                )
                StepLine("打开阵容列表")
            }

            listOf(
                "④ 在列表里点一下要用的阵容，阵容码就进剪贴板了",
                "⑤ 游戏内「阵容 → 导入阵容码」粘贴即可",
            ).forEach { StepLine(it) }
        }
    }
}

@Composable
private fun OemHintCard(onOpenAutostart: () -> Unit) {
    val brand = when {
        Perms.isXiaomi() -> "小米 / 红米"
        Perms.isHuawei() -> "华为 / 荣耀"
        Perms.isOppo() -> "OPPO / 一加 / realme"
        Perms.isVivo() -> "vivo / iQOO"
        else -> "你的手机"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "还差一步：$brand 的额外开关",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "国产系统会在应用详情里藏两个开关：「自启动」和「后台弹出界面」（小米叫这个名）。" +
                    "后者不开的话，点复制可能没反应——因为写剪贴板要借一个透明小窗口，" +
                    "被系统拦了就没法写入。两个都打开才稳。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenAutostart) { Text("打开应用设置") }
            }
        }
    }
}

/**
 * 外观设置。改完立刻全局换肤 —— 主题状态挂在 MainViewModel 上，
 * 包住整个 App 的 MaterialTheme，所以这里一点就生效，不用重启。
 */
@Composable
private fun AppearanceCard(
    themeMode: String,
    paletteId: String,
    onModeChange: (String) -> Unit,
    onPaletteChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 明暗模式 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "明暗模式",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ChoiceChip(
                            text = mode.label,
                            selected = mode.id == themeMode,
                            onClick = { onModeChange(mode.id) },
                        )
                    }
                }
            }

            // ── 主题色 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "主题色",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Palettes.forEach { palette ->
                        PaletteSwatch(
                            palette = palette,
                            selected = palette.id == paletteId,
                            onClick = { onPaletteChange(palette.id) },
                        )
                    }
                }
            }

            Text(
                text = "悬浮球和浮窗也会跟着换色。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.primary)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    }
                ),
        )
        Text(
            text = palette.name,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StepLine(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

private fun appVersion(context: android.content.Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName} (${info.longVersionCode})"
}.getOrDefault("未知")

/**
 * 自检。装到真机上出问题时，用户没法看 logcat，
 * 把关键状态直接摆出来，截个图就能定位。
 */
@Composable
private fun DiagCard(rows: List<Pair<String, String>>, ok: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (ok) "一切正常" else "有项目不正常，见下面标 ← 的行",
                color = if (ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (granted) "✓ " else "○ ",
                        color = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            if (!granted) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}
