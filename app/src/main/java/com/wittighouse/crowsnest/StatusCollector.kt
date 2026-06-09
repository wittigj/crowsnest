package com.wittighouse.crowsnest

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar
import kotlin.coroutines.resume

object StatusCollector {

    // 步数计数器（直接使用传感器返回的累计步数）
    var todaySteps: Int = 0

    // 游戏包名列表
    private val gamePackages = setOf(
        "com.tencent.tmgp.sgame", "com.tencent.tmgp.pubgmhd", "com.tencent.ig",
        "com.miHoYo.Yuanshen", "com.miHoYo.hkrpg", "com.miHoYo.zzz",
        "com.netease.onmyoji", "com.netease.dwrg", "com.YoStarJP.BlueArchive",
        "com.nexon.bluearchive", "com.tencent.lolm"
    )

    // 工作/学习App
    private val workPackages = setOf(
        "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
        "com.tencent.wework", "com.alibaba.android.rimet", "com.ss.android.lark",
        "com.github.android", "com.csdn.csdnplus"
    )

    private val appNameMap = mapOf(
        "com.tencent.mm" to "微信",
        "com.tencent.mobileqq" to "QQ",
        "com.sina.weibo" to "微博",
        "com.zhihu.android" to "知乎",
        "com.xingin.xhs" to "小红书",
        "org.telegram.messenger" to "Telegram",
        "com.discord" to "Discord",
        "com.tencent.tmgp.sgame" to "王者荣耀",
        "com.tencent.tmgp.pubgmhd" to "和平精英",
        "com.miHoYo.Yuanshen" to "原神",
        "com.miHoYo.hkrpg" to "崩坏：星穹铁道",
        "tv.danmaku.bili" to "哔哩哔哩",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.kuaishou.nebula" to "快手",
        "com.netease.cloudmusic" to "网易云音乐",
        "com.tencent.qqmusic" to "QQ音乐",
        "com.kugou.android" to "酷狗音乐",
        "com.microsoft.emmx" to "Edge",
        "com.android.chrome" to "Chrome",
        "com.quark.browser" to "夸克",
        "com.UCMobile" to "UC浏览器",
        "com.taobao.taobao" to "淘宝",
        "com.taobao.idlefish" to "闲鱼",
        "com.jingdong.app.mall" to "京东",
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.sankuai.meituan" to "美团",
        "com.dianping.v1" to "大众点评",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.android.settings" to "设置",
        "com.android.vending" to "Play商店",
        "com.bbk.launcher2" to "桌面",
        "com.vivo.launcher" to "桌面",
        "com.vivo.permissionmanager" to "权限管理",
        "com.android.permissioncontroller" to "权限管理",
        "com.vivo.browser" to "vivo浏览器",
        "com.vivo.email" to "邮件",
        "com.android.mms" to "短信",
        "com.android.dialer" to "电话",
        "com.android.contacts" to "联系人",
        "com.android.camera" to "相机",
        "com.vivo.gallery" to "相册",
        "com.android.gallery3d" to "相册",
        "com.vivo.weather" to "天气",
        "com.vivo.appstore" to "应用商店",
        "com.android.systemui" to "系统界面",
        "com.wittighouse.crowsnest" to "状态同步",
        "com.wittighouse.crowsnest.selfhosted" to "状态同步自托管"
    )
    
    // 需要过滤掉的系统应用（不应该显示为"当前使用"）
    private val systemPackages = setOf(
        // 系统核心
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.android.settings",
        "com.android.inputmethod.latin",
        // vivo 系统
        "com.vivo.permissionmanager",
        "com.vivo.launcher",
        "com.bbk.launcher2",
        "com.vivo.daemonService",
        "com.vivo.assistant",
        "com.vivo.smartunlock",
        "com.iqoo.secure",
        // 本 App
        "com.wittighouse.crowsnest",
        "com.wittighouse.crowsnest.selfhosted"
    )

    fun initStepCounter(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        Log.d("StatusCollector", "========== 步数传感器初始化 ==========")
        
        if (stepSensor != null) {
            Log.d("StatusCollector", "✓ 找到步数传感器: ${stepSensor.name}")
            
            sm.registerListener(object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // 直接使用传感器返回的累计步数
                    todaySteps = event.values[0].toInt()
                    Log.d("StatusCollector", "步数更新: $todaySteps")
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    Log.d("StatusCollector", "步数传感器精度变化: $accuracy")
                }
            }, stepSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.e("StatusCollector", "✗ 未找到步数传感器！")
        }
    }

    fun getCurrentApp(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        // 查询最近 5 分钟的事件
        val startTime = endTime - 5 * 60 * 1000
        
        Log.d("StatusCollector", "========== App 检测 ==========")
        
        // 方法1：使用 queryEvents 获取最近的前台 App 事件（最准确）
        try {
            val events = usm.queryEvents(startTime, endTime)
            var lastForegroundApp: String? = null
            var lastForegroundTime: Long = 0
            
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // MOVE_TO_FOREGROUND = 1，表示 App 进入前台
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    // 过滤系统应用
                    if (!systemPackages.contains(event.packageName)) {
                        if (event.timeStamp > lastForegroundTime) {
                            lastForegroundTime = event.timeStamp
                            lastForegroundApp = event.packageName
                        }
                    }
                }
            }
            
            if (lastForegroundApp != null) {
                val timeDiff = (endTime - lastForegroundTime) / 1000
                Log.d("StatusCollector", "通过事件检测到前台App: $lastForegroundApp (${timeDiff}秒前进入前台)")
                
                // 如果超过5分钟没有新的前台事件，可能是一直在同一个App里
                // 这种情况下仍然返回最后进入前台的App
                return lastForegroundApp
            }
            Log.d("StatusCollector", "未检测到最近5分钟内的前台事件")
        } catch (e: Exception) {
            Log.e("StatusCollector", "queryEvents 失败", e)
        }
        
        // 方法2：回退到 queryUsageStats（作为备用）
        Log.d("StatusCollector", "使用备用方法 queryUsageStats")
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val todayStart = calendar.timeInMillis
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, endTime)
        
        if (stats.isNullOrEmpty()) {
            Log.e("StatusCollector", "✗ 没有获取到任何 UsageStats 数据！请检查使用情况权限")
            return "unknown"
        }
        
        // 按最后使用时间排序
        val sortedStats = stats
            .filter { !systemPackages.contains(it.packageName) }
            .sortedByDescending { it.lastTimeUsed }
        
        val recentApp = sortedStats.firstOrNull()
        if (recentApp != null) {
            val timeDiff = (endTime - recentApp.lastTimeUsed) / 1000
            Log.d("StatusCollector", "备用方法检测: ${recentApp.packageName} (${timeDiff}秒前)")
            if (timeDiff <= 300) { // 5分钟内
                return recentApp.packageName
            }
        }
        
        Log.d("StatusCollector", "检测结果: unknown")
        return "unknown"
    }
    
    fun getAppDisplayName(packageName: String): String {
        if (packageName == "unknown") return "未知应用"
        return appNameMap[packageName] ?: packageName.substringAfterLast(".")
    }

    // 获取今日屏幕使用时长（分钟）
    fun getTodayScreenTime(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val events = usm.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        var totalTime = 0L
        val lastEventTime = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastEventTime[event.packageName] = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = lastEventTime.remove(event.packageName)
                    if (start != null) {
                        totalTime += (event.timeStamp - start)
                    }
                }
            }
        }
        
        // 处理当前正在使用的 App
        for (start in lastEventTime.values) {
            totalTime += (endTime - start)
        }

        return (totalTime / 60000).toInt()
    }

    // 获取今日游戏时长（分钟）
    fun getTodayGameTime(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val events = usm.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        var gameTime = 0L
        val lastEventTime = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (gamePackages.contains(event.packageName)) {
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastEventTime[event.packageName] = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = lastEventTime.remove(event.packageName)
                        if (start != null) {
                            gameTime += (event.timeStamp - start)
                        }
                    }
                }
            }
        }
        return (gameTime / 60000).toInt()
    }

    // 获取今日Top3应用
    fun getTodayTopApps(context: Context): List<Pair<String, Int>> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val events = usm.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        val appTimeMap = mutableMapOf<String, Long>()
        val lastEventTime = mutableMapOf<String, Long>()
        val ignoredPackages = setOf("com.bbk.launcher2", "com.vivo.launcher", "com.android.systemui")

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!ignoredPackages.contains(event.packageName)) {
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastEventTime[event.packageName] = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = lastEventTime.remove(event.packageName)
                        if (start != null) {
                            val duration = event.timeStamp - start
                            val displayName = getAppDisplayName(event.packageName)
                            appTimeMap[displayName] = (appTimeMap[displayName] ?: 0L) + duration
                        }
                    }
                }
            }
        }

        return appTimeMap.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { Pair(it.key, (it.value / 60000).toInt()) }
    }

    // 判断当前状态（摸鱼/工作/游戏/休息）
    fun getCurrentMood(currentApp: String): String {
        return when {
            gamePackages.contains(currentApp) -> "gaming"
            workPackages.contains(currentApp) -> "working"
            currentApp.contains("launcher") || currentApp == "unknown" -> "idle"
            currentApp.contains("bili") || currentApp.contains("aweme") || 
            currentApp.contains("weibo") || currentApp.contains("xhs") -> "slacking"
            else -> "normal"
        }
    }

    // 判断是否熬夜（23点-5点使用手机）
    fun isLateNight(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }

    fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getBatteryTemperature(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10f
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun getWifiName(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            info.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" }
        } catch (e: Exception) { null }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.isEnabled == true
        } catch (e: SecurityException) { false }
    }

    fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isInteractive
    }

    fun getNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "无网络"
        val caps = cm.getNetworkCapabilities(network) ?: return "无网络"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "流量"
            else -> "其他"
        }
    }

    fun getVolume(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100 / max) else 0
    }

    fun getBrightness(context: Context): Int {
        return try {
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            (brightness * 100 / 255)
        } catch (e: Exception) { -1 }
    }

    fun getStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes / (1024 * 1024 * 1024)
            val free = stat.availableBytes / (1024 * 1024 * 1024)
            "${total - free}/${total}GB"
        } catch (e: Exception) { "" }
    }

    fun getMemoryInfo(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val total = memInfo.totalMem / (1024 * 1024 * 1024)
            val free = memInfo.availMem / (1024 * 1024 * 1024)
            "${total - free}/${total}GB"
        } catch (e: Exception) { "" }
    }

    /**
     * 获取位置信息
     * @return LocationResult 包含城市名（如果本地解析成功）或经纬度（供服务端解析）
     */
    data class LocationResult(
        val city: String? = null,      // 城市名
        val source: String? = null     // 来源：local/amap
    )
    
    suspend fun getLocation(context: Context): LocationResult? {
        Log.d("StatusCollector", "开始获取位置...")
        return try {
            val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
            
            // 1. 先尝试获取实时位置
            var location = suspendCancellableCoroutine<android.location.Location?> { cont ->
                try {
                    Log.d("StatusCollector", "请求 GPS 实时位置...")
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                        .addOnSuccessListener { loc -> 
                            Log.d("StatusCollector", "GPS 实时位置: ${loc?.latitude}, ${loc?.longitude}")
                            cont.resume(loc) 
                        }
                        .addOnFailureListener { e -> 
                            Log.e("StatusCollector", "GPS 实时位置获取失败: ${e.message}")
                            cont.resume(null) 
                        }
                } catch (e: SecurityException) { 
                    Log.e("StatusCollector", "GPS 权限异常: ${e.message}")
                    cont.resume(null) 
                }
            }
            
            // 2. 如果实时位置失败，尝试获取上次已知位置
            if (location == null) {
                Log.d("StatusCollector", "实时位置获取失败，尝试获取上次已知位置...")
                location = suspendCancellableCoroutine { cont ->
                    try {
                        client.lastLocation
                            .addOnSuccessListener { loc ->
                                Log.d("StatusCollector", "上次已知位置: ${loc?.latitude}, ${loc?.longitude}")
                                cont.resume(loc)
                            }
                            .addOnFailureListener { e ->
                                Log.e("StatusCollector", "上次已知位置获取失败: ${e.message}")
                                cont.resume(null)
                            }
                    } catch (e: SecurityException) {
                        Log.e("StatusCollector", "获取上次位置权限异常: ${e.message}")
                        cont.resume(null)
                    }
                }
            }
            
            if (location == null) {
                Log.w("StatusCollector", "无法获取任何位置信息，请检查位置权限和GPS是否开启")
                return null
            }
            
            Log.d("StatusCollector", "成功获取位置: ${location.latitude}, ${location.longitude}")
            
            // 1. 首先尝试 Android Geocoder 本地解析
            val localCity = try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.CHINA)
                if (android.location.Geocoder.isPresent()) {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        var city = addr.locality ?: addr.adminArea ?: addr.subAdminArea
                        if (city != null && city.endsWith("市")) {
                            city = city.dropLast(1)
                        }
                        city
                    } else null
                } else null
            } catch (e: Exception) {
                Log.d("StatusCollector", "Geocoder 解析失败: ${e.message}")
                null
            }
            
            if (localCity != null) {
                Log.d("StatusCollector", "位置解析成功(Android Geocoder): $localCity")
                return LocationResult(city = localCity, source = "local")
            }
            
            // 2. Android Geocoder 失败，尝试高德 API（手机端是国内网络，可以直接访问）
            Log.d("StatusCollector", "Geocoder 失败，尝试高德 API...")
            val amapCity = try {
                val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
                val amapKey = prefs.getString("amap_key", "") ?: ""
                Log.d("StatusCollector", "高德 API Key: ${if (amapKey.isNotEmpty()) "${amapKey.take(8)}..." else "未配置"}")
                
                if (amapKey.isNotEmpty()) {
                    val url = "https://restapi.amap.com/v3/geocode/regeo?location=${location.longitude},${location.latitude}&key=$amapKey&output=json"
                    Log.d("StatusCollector", "高德 API 请求: $url")
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(url).get().build()
                    val response = client.newCall(request).execute()
                    Log.d("StatusCollector", "高德 API 响应: ${response.code}")
                    
                    if (response.isSuccessful) {
                        val json = response.body?.string()
                        Log.d("StatusCollector", "高德 API 返回: $json")
                        val geo = com.google.gson.JsonParser.parseString(json).asJsonObject
                        if (geo.get("status")?.asString == "1") {
                            val addr = geo.getAsJsonObject("regeocode")?.getAsJsonObject("addressComponent")
                            var city = addr?.get("city")?.let { 
                                if (it.isJsonArray) null else it.asString 
                            } ?: addr?.get("province")?.asString ?: ""
                            // 去掉"市"后缀
                            if (city.endsWith("市")) {
                                city = city.dropLast(1)
                            }
                            Log.d("StatusCollector", "高德解析城市: $city")
                            if (city.isNotEmpty()) city else null
                        } else {
                            Log.w("StatusCollector", "高德 API 返回错误状态: ${geo.get("info")?.asString}")
                            null
                        }
                    } else {
                        Log.w("StatusCollector", "高德 API HTTP 错误: ${response.code}")
                        null
                    }
                } else {
                    Log.d("StatusCollector", "未配置高德 API Key")
                    null
                }
            } catch (e: Exception) {
                Log.e("StatusCollector", "高德 API 调用失败", e)
                null
            }
            
            if (amapCity != null) {
                Log.d("StatusCollector", "位置解析成功(高德API): $amapCity")
                return LocationResult(city = amapCity, source = "amap")
            }
            
            Log.d("StatusCollector", "位置解析失败，无法获取城市名")
            null
        } catch (e: Exception) { 
            Log.e("StatusCollector", "获取位置失败", e)
            null 
        }
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }
}
