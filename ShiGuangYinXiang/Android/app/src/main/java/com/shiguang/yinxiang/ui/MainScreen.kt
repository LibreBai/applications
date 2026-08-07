package com.shiguang.yinxiang.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shiguang.yinxiang.data.ExifParser
import com.shiguang.yinxiang.data.PhotoExif
import com.shiguang.yinxiang.data.WatermarkItem
import com.shiguang.yinxiang.util.ImageDecoder
import com.shiguang.yinxiang.util.MediaStoreSaver
import com.shiguang.yinxiang.util.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面：选图 → 解析 EXIF → 勾选水印项 → 实时预览 → 保存。
 * 水印印哪些内容由用户勾选决定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- 状态 ----
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var exif by remember { mutableStateOf<PhotoExif?>(null) }
    var availableItems by remember { mutableStateOf<List<WatermarkItem>>(emptyList()) }
    var selectedItems by remember { mutableStateOf(WatermarkItem.defaultSelected()) }
    var watermarkPosition by remember { mutableStateOf(WatermarkRenderer.Position.BOTTOM_LEFT) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // ---- 选图（Photo Picker，无需权限）----
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }
    fun openPicker() {
        pickMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // 选中图片后：IO 上解析 EXIF + 解码正立原图
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        isLoading = true
        error = null
        resultBitmap = null
        try {
            val (parsed, bitmap) = withContext(Dispatchers.IO) {
                ExifParser.parse(context, uri) to ImageDecoder.decode(context, uri)
            }
            exif = parsed
            baseBitmap = bitmap
            val available = parsed?.let { WatermarkItem.availableFor(it) } ?: emptyList()
            availableItems = available
            // 默认勾选与可用项取交集，避免勾上不存在的项
            selectedItems = WatermarkItem.defaultSelected().intersect(available.toSet())
            if (bitmap == null) error = "无法读取这张图片"
        } catch (t: Throwable) {
            error = "读取失败：${t.message}"
        }
        isLoading = false
    }

    // 勾选/位置/原图变化 → 重渲染水印（快，只重跑渲染段）
    LaunchedEffect(baseBitmap, exif, selectedItems, watermarkPosition) {
        val base = baseBitmap ?: return@LaunchedEffect
        val e = exif
        resultBitmap = withContext(Dispatchers.Default) {
            WatermarkRenderer.render(
                base,
                e?.let { WatermarkItem.linesFor(it, selectedItems) } ?: emptyList(),
                watermarkPosition,
            )
        }
    }

    // ---- 保存 ----
    fun performSave() {
        val bitmap = resultBitmap ?: return
        scope.launch {
            isSaving = true
            val result = withContext(Dispatchers.IO) { MediaStoreSaver.save(context, bitmap) }
            isSaving = false
            result.onSuccess { toast("已保存到相册「时光印象」") }
                .onFailure { toast("保存失败：${it.message}") }
        }
    }

    val requestWritePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) performSave() else toast("需要存储权限才能保存")
    }

    fun onSaveClicked() {
        // API 26-28 需要写权限；29+ 走 MediaStore 零权限
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performSave()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "时光印象",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "把时间印回照片",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                if (pickedUri == null) {
                    EmptyState(onPick = { openPicker() })
                } else {
                    PickedContent(
                        resultBitmap = resultBitmap,
                        isLoading = isLoading,
                        error = error,
                        availableItems = availableItems,
                        exif = exif,
                        selectedItems = selectedItems,
                        onToggleItem = { item, checked ->
                            selectedItems = if (checked) selectedItems + item else selectedItems - item
                        },
                        position = watermarkPosition,
                        onPositionChange = { watermarkPosition = it },
                        isSaving = isSaving,
                        onSave = { onSaveClicked() },
                        onRepick = { openPicker() },
                    )
                }
            }
        }
    }
}

/** 未选图时的引导页。 */
@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("🕰️", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "把时间印回照片",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "读取照片里的拍摄时间与位置，\n把它们作为水印印回照片上。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FeatureCard("📷", "读取本地图片", "从相册选择任意一张照片")
        FeatureCard("🕐", "解析拍摄信息", "拍摄时间 · 经纬度 · 设备型号")
        FeatureCard("🖋️", "水印回显", "勾选想印的内容，实时预览")
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onPick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("选择一张图片", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FeatureCard(emoji: String, title: String, desc: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 已选图时的主内容：预览 + 位置选择 + 信息面板 + 保存。 */
@Composable
private fun PickedContent(
    resultBitmap: Bitmap?,
    isLoading: Boolean,
    error: String?,
    availableItems: List<WatermarkItem>,
    exif: PhotoExif?,
    selectedItems: Set<WatermarkItem>,
    onToggleItem: (WatermarkItem, Boolean) -> Unit,
    position: WatermarkRenderer.Position,
    onPositionChange: (WatermarkRenderer.Position) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    onRepick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 预览卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = resultBitmap
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "水印预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                    isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("正在解析照片…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Text("无法预览", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        // 水印位置
        if (resultBitmap != null) {
            Text(
                "水印位置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PositionChip("左上", WatermarkRenderer.Position.TOP_LEFT, position, onPositionChange)
                PositionChip("右上", WatermarkRenderer.Position.TOP_RIGHT, position, onPositionChange)
                PositionChip("左下", WatermarkRenderer.Position.BOTTOM_LEFT, position, onPositionChange)
                PositionChip("右下", WatermarkRenderer.Position.BOTTOM_RIGHT, position, onPositionChange)
            }
            Spacer(Modifier.height(12.dp))
        }

        // 信息面板
        if (availableItems.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "要印到照片上的信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    availableItems.forEach { item ->
                        val value = exif?.let { item.extract(it) } ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(emojiFor(item), fontSize = 20.sp)
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = item in selectedItems,
                                onCheckedChange = { onToggleItem(item, it) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRepick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("换一张")
            }
            Button(
                onClick = onSave,
                enabled = resultBitmap != null && !isSaving,
                modifier = Modifier
                    .weight(2f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (isSaving) "保存中…" else "保存到相册",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PositionChip(
    label: String,
    value: WatermarkRenderer.Position,
    selected: WatermarkRenderer.Position,
    onSelect: (WatermarkRenderer.Position) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

private fun emojiFor(item: WatermarkItem): String = when (item) {
    WatermarkItem.DATE -> "📅"
    WatermarkItem.TIME -> "🕐"
    WatermarkItem.DATETIME -> "🕰️"
    WatermarkItem.GPS -> "📍"
    WatermarkItem.GPS_DECIMAL -> "🧭"
    WatermarkItem.DEVICE -> "📱"
}
