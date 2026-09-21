package com.wangye.tftbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangye.tftbox.data.Lineup
import com.wangye.tftbox.util.Clip
import com.wangye.tftbox.util.LineupShareParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    initial: Lineup,
    onBack: () -> Unit,
    onSave: (Lineup) -> Unit,
) {
    val context = LocalContext.current
    val isNew = initial.id == 0L

    var title by remember { mutableStateOf(initial.title) }
    var code by remember { mutableStateOf(initial.code) }
    var season by remember { mutableStateOf(initial.season) }
    var tags by remember { mutableStateOf(initial.tags) }
    var note by remember { mutableStateOf(initial.note) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        when {
            title.isBlank() -> error = "起个名字吧，不然列表里认不出是哪套阵容"
            code.isBlank() -> error = "阵容码不能是空的"
            else -> {
                error = null
                onSave(
                    initial.copy(
                        title = title.trim(),
                        code = code.trim(),
                        season = season.trim(),
                        tags = tags.trim(),
                        note = note.trim(),
                    )
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新增阵容" else "编辑阵容", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("阵容名") },
                placeholder = { Text("比如：玉剑决斗九五") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("阵容码") },
                placeholder = { Text("从网上抄来的那串码") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${code.trim().length} 个字",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        // 整段分享文本也能贴：会自动抠出码、名字、作者
                        when (val parsed = LineupShareParser.parse(Clip.read(context))) {
                            null -> error = "剪贴板里没找到阵容码"
                            else -> {
                                code = parsed.code
                                // 只在用户还没填名字的时候才自动填，别覆盖人家手打的
                                if (title.isBlank()) parsed.title?.let { title = it }
                                if (note.isBlank()) parsed.author?.let { note = "来源：$it" }
                                error = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("从剪贴板粘贴")
                }
            }

            OutlinedTextField(
                value = season,
                onValueChange = { season = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("赛季 / 版本（选填）") },
                placeholder = { Text("比如：S13") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标签（选填）") },
                placeholder = { Text("逗号分隔，比如：决斗,九五,天选") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（选填）") },
                placeholder = { Text("装备、运营节奏、什么时候上分…") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
