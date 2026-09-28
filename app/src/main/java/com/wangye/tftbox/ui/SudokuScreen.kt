package com.wangye.tftbox.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 数独模式的主页。
 *
 * 这个模式真正干活的是悬浮球，App 本身只是在游戏外面待着。
 * 所以这一页不讲功能，只讲怎么用 —— 用户装完之后大概率只来这儿看一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuScreen(onOpenSettings: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppMode.SUDOKU.label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置与权限",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "怎么用",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    listOf(
                        "① 右上角设置里开启「截图服务」",
                        "② 进游戏，把棋盘打开",
                        "③ 点一下悬浮球 —— 下一步该画哪，会直接框在棋盘上",
                        "④ 再点一下球就把框收起来；再点又会重新算一次",
                        "⑤ 长按球能打开详细面板",
                    ).forEach { StepLine(it) }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "框上的标记",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )

                    LegendRow(
                        swatch = { LegendSwatch(Color(0xFFFFD54F), filled = true) },
                        text = "实心块 —— 这一格要填满",
                    )
                    LegendRow(
                        swatch = { LegendSwatch(Color(0xFFEDEDF2), filled = false) },
                        text = "叉 —— 这一格要留空",
                    )
                    LegendRow(
                        swatch = { LegendSwatch(Color(0xFFFF5252), filled = true) },
                        text = "红色 —— 你之前这格可能画错了",
                    )

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "一次最多框 6 处，只画最前面几个 —— 满屏都是框反而看不清。" +
                            "剩下的可以再点一次球接着看。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "几点说明",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    listOf(
                        "棋盘多大都能认（10×10、12×12、15×15…），不用手动设置，也不用标定。",
                        "游戏里线索变黄只代表「这一行/列的数字对上了」，不代表全局正确 —— 我判断的是全局。",
                        "推不出来的时候我会明说「这步得猜」，不会硬给你一个不确定的答案。",
                    ).forEach {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepLine(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
}

/** 图例：一个小色块 + 一句说明。色块的样子跟游戏里实际画出来的一致 */
@Composable
private fun LegendRow(swatch: @Composable () -> Unit, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        swatch()
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

/** 图例色块。样子跟游戏画面上实际画出来的一致：实心块 / 叉 */
@Composable
private fun LegendSwatch(color: Color, filled: Boolean) {
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        } else {
            Text(
                text = "✕",
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
