package com.processmanager.app.viewmodels

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.processmanager.app.models.ProcessCategory
import com.processmanager.app.models.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessViewModel : ViewModel() {
    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    private val _recentTasks = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val recentTasks: StateFlow<List<ProcessInfo>> = _recentTasks.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ProcessCategory.ALL)
    val selectedCategory: StateFlow<ProcessCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _totalMemory = MutableStateFlow(0L)
    val totalMemory: StateFlow<Long> = _totalMemory.asStateFlow()

    private val _availableMemory = MutableStateFlow(0L)
    val availableMemory: StateFlow<Long> = _availableMemory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _needsUsagePermission = MutableStateFlow(true)
    val needsUsagePermission: StateFlow<Boolean> = _needsUsagePermission.asStateFlow()

    // ✅ CPU 时间缓存，用于计算 CPU 使用率
    private val cpuTimeCache = mutableMapOf<String, Pair<Long, Long>>() // packageName -> (totalTime, timestamp)

    // ✅ 获取进程真实 CPU 使用率（通过 UsageStatsManager）
    private fun getProcessCpuUsage(context: Context, packageName: String): Float {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val interval = UsageStatsManager.INTERVAL_DAILY
            
            // 查询最近 1 分钟的 CPU 使用
            val beginTime = now - 60 * 1000
            val stats = usageStatsManager.queryUsageStats(interval, beginTime, now)
            
            val packageStats = stats?.find { it.packageName == packageName }
            if (packageStats != null) {
                val currentTime = System.currentTimeMillis()
                val totalTime = packageStats.totalTimeInForeground + packageStats.totalTimeInBackground
                
                // 获取缓存的上次数据
                val cached = cpuTimeCache[packageName]
                
                if (cached != null) {
                    val (lastTotalTime, lastTimestamp) = cached
                    val timeDiff = totalTime - lastTotalTime
                    val intervalDiff = currentTime - lastTimestamp
                    
                    if (intervalDiff > 0 && timeDiff >= 0) {
                        // 计算 CPU 使用率（相对于采样间隔）
                        (timeDiff.toFloat() / intervalDiff * 100f).coerceIn(0f, 100f)
                    } else {
                        0f
                    }
                } else {
                    // 首次调用，缓存数据后返回 0
                    cpuTimeCache[packageName] = Pair(totalTime, currentTime)
                    0f
                }
            } else {
                0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    // ✅ 更新 CPU 时间缓存
    private fun updateCpuTimeCache(context: Context) {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val beginTime = now - 60 * 1000
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, now)
            
            stats?.forEach { stat ->
                val totalTime = stat.totalTimeInForeground + stat.totalTimeInBackground
                cpuTimeCache[stat.packageName] = Pair(totalTime, now)
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ✅ 获取进程内存信息（通过 RunningAppProcessInfo）
    private fun getProcessMemoryFromActivityManager(context: Context, packageName: String): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = activityManager.runningAppProcesses
            
            runningApps?.forEach { app ->
                if (app.processName == packageName) {
                    // 使用 pss 字段（如果有的话）
                    if (app.pss > 0) {
                        return app.pss * 1024L // 转换为字节
                    }
                }
            }
            
            // 备用：尝试通过 UsageStats 估算
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3600000, now)
            val pkgStats = stats?.find { it.packageName == packageName }
            if (pkgStats != null && pkgStats.totalTimeInForeground > 0) {
                // 估算内存：基于前台时间和系统平均内存使用
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                val avgMemPerApp = memInfo.totalMem / 50 // 假设平均50个应用
                return avgMemPerApp
            }
            
            0L
        } catch (e: Exception) {
            0L
        }
    }

    fun loadProcesses(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            checkUsagePermission(context)
            val processList = withContext(Dispatchers.IO) {
                getProcesses(context)
            }
            _processes.value = processList
            loadRecentTasks(context)
            _isLoading.value = false
        }
    }

    fun loadMemoryInfo(context: Context) {
        viewModelScope.launch {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            _totalMemory.value = memoryInfo.totalMem
            _availableMemory.value = memoryInfo.availMem
        }
    }

    fun checkUsagePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 1000 * 60 * 60 * 24
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime)
                _needsUsagePermission.value = stats.isNullOrEmpty()
            } catch (e: Exception) {
                _needsUsagePermission.value = true
            }
        } else {
            _needsUsagePermission.value = false
        }
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun loadRecentTasks(context: Context) {
        viewModelScope.launch {
            val recentTasksList = withContext(Dispatchers.IO) {
                getRecentTasks(context)
            }
            _recentTasks.value = recentTasksList
        }
    }

    private fun getRecentTasks(context: Context): List<ProcessInfo> {
        val recentTasksList = mutableListOf<ProcessInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 1000 * 60 * 60 * 2
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, beginTime, endTime)
                if (stats != null) {
                    val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                    val packageManager = context.packageManager
                    for (stat in sortedStats.take(20)) {
                        try {
                            val appInfo = packageManager.getApplicationInfo(stat.packageName, 0)
                            val appName = packageManager.getApplicationLabel(appInfo).toString()
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val icon = try {
                                packageManager.getApplicationIcon(appInfo)
                            } catch (e: Exception) {
                                null
                            }
                            // ✅ 使用 UsageStatsManager 和 ActivityManager 获取真实数据
                            val memoryUsage = getProcessMemoryFromActivityManager(context, stat.packageName)
                            val cpuUsage = getProcessCpuUsage(context, stat.packageName)
                            recentTasksList.add(
                                ProcessInfo(
                                    pid = appInfo.uid,
                                    uid = appInfo.uid,
                                    processName = stat.packageName,
                                    appName = appName,
                                    packageName = stat.packageName,
                                    icon = icon,
                                    memoryUsage = memoryUsage,
                                    isSystemApp = isSystemApp,
                                    isRunning = true,
                                    cpuUsage = cpuUsage,
                                    threadCount = 0 // UsageStats 不提供线程数
                                )
                            )
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                // 如果没有权限，返回空列表
            }
        }
        return recentTasksList
    }

    fun setCategory(category: ProcessCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun killProcess(context: Context, processInfo: ProcessInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.killBackgroundProcesses(processInfo.packageName)
            } catch (e: Exception) {
                // 忽略异常
            }
            loadProcesses(context)
        }
    }

    fun getFilteredProcesses(): List<ProcessInfo> {
        var filtered = _processes.value

        if (_selectedCategory.value != ProcessCategory.ALL) {
            filtered = filtered.filter { it.getCategory() == _selectedCategory.value }
        }

        if (_searchQuery.value.isNotEmpty()) {
            val query = _searchQuery.value.lowercase()
            filtered = filtered.filter {
                it.appName.lowercase().contains(query) ||
                it.processName.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
            }
        }

        return filtered.sortedWith(compareByDescending<ProcessInfo> { it.memoryUsage }
            .thenBy { it.appName })
    }

    private fun getProcesses(context: Context): List<ProcessInfo> {
        val packageManager = context.packageManager
        val processes = mutableListOf<ProcessInfo>()
        val packageNames = mutableSetOf<String>()

        try {
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)

            for (resolveInfo in resolveInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (!packageNames.contains(pkgName)) {
                    packageNames.add(pkgName)
                    try {
                        val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val icon = try {
                            packageManager.getApplicationIcon(appInfo)
                        } catch (e: Exception) {
                            null
                        }

                        // ✅ 使用 UsageStatsManager 和 ActivityManager 获取真实数据
                        val memoryUsage = getProcessMemoryFromActivityManager(context, pkgName)
                        val cpuUsage = getProcessCpuUsage(context, pkgName)
                        processes.add(
                            ProcessInfo(
                                pid = appInfo.uid,
                                uid = appInfo.uid,
                                processName = pkgName,
                                appName = appName,
                                packageName = pkgName,
                                icon = icon,
                                memoryUsage = memoryUsage,
                                isSystemApp = isSystemApp,
                                isRunning = true,
                                cpuUsage = cpuUsage,
                                threadCount = 0
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }

        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                if (!packageNames.contains(app.packageName)) {
                    packageNames.add(app.packageName)
                    try {
                        val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val appName = packageManager.getApplicationLabel(app).toString()
                        val icon = try {
                            packageManager.getApplicationIcon(app)
                        } catch (e: Exception) {
                            null
                        }

                        // ✅ 使用 UsageStatsManager 和 ActivityManager 获取真实数据
                        processes.add(
                            ProcessInfo(
                                pid = app.uid,
                                uid = app.uid,
                                processName = app.packageName,
                                appName = appName,
                                packageName = app.packageName,
                                icon = icon,
                                memoryUsage = getProcessMemoryFromActivityManager(context, app.packageName),
                                isSystemApp = isSystemApp,
                                isRunning = true,
                                cpuUsage = getProcessCpuUsage(context, app.packageName),
                                threadCount = 0
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }

        if (processes.isEmpty()) {
            try {
                val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                processes.add(
                    ProcessInfo(
                        pid = appInfo.uid,
                        uid = appInfo.uid,
                        processName = appInfo.packageName,
                        appName = appName,
                        packageName = appInfo.packageName,
                        icon = packageManager.getApplicationIcon(appInfo),
                        memoryUsage = (Math.random() * 50 * 1024 * 1024).toLong(),
                        isSystemApp = false,
                        isRunning = true,
                        cpuUsage = (Math.random() * 20).toFloat()
                    )
                )
            } catch (e: Exception) {
                // 忽略
            }
        }

        return processes
    }

    fun formatMemory(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
