package com.wittighouse.crowsnest

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MusicNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val registeredCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    
    private val sessionListener = object : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            Log.d("MusicListener", "活跃会话列表变化，重新注册回调")
            registerCallbacks(controllers)
            updateMusicInfo(controllers)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicListener", "========== MusicListener 启动 ==========")
        try {
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionListener,
                android.content.ComponentName(this, MusicNotificationListener::class.java)
            )
            // 初始获取
            val controllers = mediaSessionManager?.getActiveSessions(
                android.content.ComponentName(this, MusicNotificationListener::class.java)
            )
            Log.d("MusicListener", "活跃媒体会话数量: ${controllers?.size ?: 0}")
            registerCallbacks(controllers)
            updateMusicInfo(controllers)
        } catch (e: Exception) {
            Log.e("MusicListener", "Failed to init", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // 取消所有回调
            unregisterAllCallbacks()
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            Log.e("MusicListener", "Failed to cleanup", e)
        }
    }
    
    private fun registerCallbacks(controllers: List<MediaController>?) {
        // 先取消旧的回调
        unregisterAllCallbacks()
        
        controllers?.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    Log.d("MusicListener", "元数据变化: ${controller.packageName}")
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    Log.d("MusicListener", "  新歌曲: $title - $artist")
                    
                    // 检查是否正在播放
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                        if (!title.isNullOrBlank()) {
                            StatusService.currentMusic = title
                            StatusService.currentMusicArtist = artist
                            Log.d("MusicListener", "✓ 更新为: $title - $artist")
                        }
                    }
                }
                
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    Log.d("MusicListener", "播放状态变化: ${controller.packageName} -> ${state?.state}")
                    // 重新检查所有会话
                    handler.post {
                        val currentControllers = mediaSessionManager?.getActiveSessions(
                            android.content.ComponentName(this@MusicNotificationListener, MusicNotificationListener::class.java)
                        )
                        updateMusicInfo(currentControllers)
                    }
                }
            }
            
            try {
                controller.registerCallback(callback, handler)
                registeredCallbacks[controller] = callback
                Log.d("MusicListener", "已为 ${controller.packageName} 注册回调")
            } catch (e: Exception) {
                Log.e("MusicListener", "注册回调失败: ${controller.packageName}", e)
            }
        }
    }
    
    private fun unregisterAllCallbacks() {
        registeredCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
                // 忽略
            }
        }
        registeredCallbacks.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 也可以从通知中获取音乐信息
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun updateMusicInfo(controllers: List<MediaController>?) {
        Log.d("MusicListener", "========== 更新音乐信息 ==========")
        Log.d("MusicListener", "会话数量: ${controllers?.size ?: 0}")
        
        if (controllers.isNullOrEmpty()) {
            StatusService.currentMusic = null
            StatusService.currentMusicArtist = null
            Log.d("MusicListener", "无活跃会话，清除音乐信息")
            return
        }

        // 列出所有会话状态
        controllers.forEachIndexed { index, controller ->
            val state = controller.playbackState?.state
            val stateName = when (state) {
                PlaybackState.STATE_PLAYING -> "播放中"
                PlaybackState.STATE_PAUSED -> "暂停"
                PlaybackState.STATE_STOPPED -> "停止"
                PlaybackState.STATE_BUFFERING -> "缓冲"
                else -> "其他($state)"
            }
            val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            Log.d("MusicListener", "  会话$index: ${controller.packageName} - $stateName - $title")
        }

        // 找到正在播放的
        val playingController = controllers.find { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        if (playingController != null) {
            val metadata = playingController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            
            Log.d("MusicListener", "找到播放中: ${playingController.packageName}")
            Log.d("MusicListener", "  标题: $title")
            Log.d("MusicListener", "  艺术家: $artist")
            
            // 只有当有有效信息时才更新
            if (!title.isNullOrBlank()) {
                StatusService.currentMusic = title
                StatusService.currentMusicArtist = artist
                Log.d("MusicListener", "✓ 已更新音乐信息")
            } else {
                Log.d("MusicListener", "✗ 标题为空，跳过")
            }
        } else {
            // 没有正在播放的，清除音乐信息
            StatusService.currentMusic = null
            StatusService.currentMusicArtist = null
            Log.d("MusicListener", "无播放中的会话，清除音乐信息")
        }
        Log.d("MusicListener", "==================================")
    }
}
