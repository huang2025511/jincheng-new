package com.processmanager.app.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.processmanager.app.models.ProcessCategory
import com.processmanager.app.models.ProcessInfo
import com.processmanager.app.viewmodels.ProcessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    viewModel: ProcessViewModel,
    onProcessClick: (ProcessInfo) -> Unit
) {
    val context = LocalContext.current
    val processes by viewModel.processes.collectAsState()
    val recentTasks by viewModel.recentTasks.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val needsUsagePermission by viewModel.needsUsagePermission.collectAsState()

    // 生命周期感知，当用户回到应用时重新检查权限和加载数据
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkUsagePermission(context)
                viewModel.loadRecentTasks(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("进程管理器") },
                actions = {
                    IconButton(onClick = { viewModel.loadProcesses(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Permission Card
            if (needsUsagePermission) {
                PermissionCard(
                    onGrantClick = { viewModel.openUsageStatsSettings(context) },
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Recent Tasks
            if (recentTasks.isNotEmpty()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Clock, contentDescription = "最近使用")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "最近使用",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentTasks, key = { it.packageName }) { task ->
                            RecentTaskItem(task = task, onClick = { onProcessClick(task) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索进程...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions.Default,
                shape = RoundedCornerShape(12.dp)
            )

            // Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == ProcessCategory.ALL,
                    onClick = { viewModel.setCategory(ProcessCategory.ALL) },
                    label = { Text("全部") },
                    leadingIcon = if (selectedCategory == ProcessCategory.ALL) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
                FilterChip(
                    selected = selectedCategory == ProcessCategory.USER,
                    onClick = { viewModel.setCategory(ProcessCategory.USER) },
                    label = { Text("用户") },
                    leadingIcon = if (selectedCategory == ProcessCategory.USER) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
                FilterChip(
                    selected = selectedCategory == ProcessCategory.SYSTEM,
                    onClick = { viewModel.setCategory(ProcessCategory.SYSTEM) },
                    label = { Text("系统") },
                    leadingIcon = if (selectedCategory == ProcessCategory.SYSTEM) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
                FilterChip(
                    selected = selectedCategory == ProcessCategory.SERVICE,
                    onClick = { viewModel.setCategory(ProcessCategory.SERVICE) },
                    label = { Text("服务") },
                    leadingIcon = if (selectedCategory == ProcessCategory.SERVICE) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val filteredProcesses = viewModel.getFilteredProcesses()

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                filteredProcesses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有找到进程",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredProcesses, key = { it.packageName }) { process ->
                            ProcessItem(
                                process = process,
                                viewModel = viewModel,
                                onClick = { onProcessClick(process) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "需要权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "授予应用使用情况访问权限以获取更完整的进程信息。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("前往设置")
            }
        }
    }
}

@Composable
private fun RecentTaskItem(
    task: ProcessInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            task.icon?.let { icon ->
                when {
                    icon is BitmapDrawable -> {
                        Image(
                            bitmap = icon.bitmap.asImageBitmap(),
                            contentDescription = task.appName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = task.appName.firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = task.appName.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = task.appName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProcessItem(
    process: ProcessInfo,
    viewModel: ProcessViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            process.icon?.let { icon ->
                when {
                    icon is BitmapDrawable -> {
                        Image(
                            bitmap = icon.bitmap.asImageBitmap(),
                            contentDescription = process.appName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = process.appName,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            } ?: Icon(
                imageVector = Icons.Default.Home,
                contentDescription = process.appName,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = process.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = process.processName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "内存: ${viewModel.formatMemory(process.memoryUsage)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "CPU: %.1f%%".format(process.cpuUsage),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(onClick = { viewModel.killProcess(context, process) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "结束进程",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
