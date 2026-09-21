package com.wangye.tftbox.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wangye.tftbox.MainActivity
import com.wangye.tftbox.R
import com.wangye.tftbox.TftApp
import com.wangye.tftbox.data.Lineup
import com.wangye.tftbox.util.Clip
import com.wangye.tftbox.util.Perms
import com.wangye.tftbox.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮球 + 浮窗的宿主。
 *
 * 必须是前台服务：Android 8 起后台 Service 会被随时杀掉，悬浮球要有通知撑着才活得久。
 * Android 14 起前台服务必须声明类型，这里用 specialUse（悬浮窗不属于任何标准类型），
 * manifest 里对应声明了 FOREGROUND_SERVICE_SPECIAL_USE 权限。
 */
class FloatingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { (application as TftApp).repository }

    private lateinit var windowManager: WindowManager
    private var ballView: BallView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var panelView: PanelView? = null

    private var ballSize = 0
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onCreate() {
        super.onCreate()
        lastError = null
        isRunning = true
        runCatching {
            // 用 Context.WINDOW_SERVICE 而不是 getSystemService(Class)：
            // 后者在非可视 Context（Service / Application）上可能返回 null 或抛异常。
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm == null) {
                lastError = "拿不到 WindowManager（getSystemService 返回 null）"
            } else {
                windowManager = wm
            }
            refreshScreenSize()
            ballSize = dp(52f)
        }.onFailure {
            lastError = "onCreate 初始化失败：${it.javaClass.simpleName}: ${it.message}"
            Log.e(TAG, "onCreate failed", it)
        }
        // Service 自己收不到转屏回调，挂在 Application 上才稳
        runCatching { application.registerComponentCallbacks(configWatcher) }
        observeLineups()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i(TAG, "onStartCommand 进入，SDK=${Build.VERSION.SDK_INT}")

        try {
            startForegroundCompat()
            Log.i(TAG, "startForeground 成功")
        } catch (t: Throwable) {
            // 这里挂掉的话整个服务会被系统杀掉，悬浮球自然就不会出现。
            // 把原因记下来，设置页的自检能看到。
            lastError = "startForeground 失败：${t.javaClass.simpleName}: ${t.message}"
            Log.e(TAG, "startForeground failed", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (ballView == null) addBall()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 用户把 App 从后台任务列表里划掉了 → 球也收掉。
     *
     * 配合界面上的约定：App 活着就有球，App 划掉了就没球。
     * 必须显式 stopSelf()，否则 START_STICKY 会让系统把服务重新拉起来，
     * 用户会看到「明明退干净了球又自己冒出来」。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "任务被移除，关闭悬浮球")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * 转屏 / 分屏回调。
     *
     * 挂在 Application 上而不是覆写 Service.onConfigurationChanged —— Service 作为
     * 组件并不保证收到配置变更回调，注册 ComponentCallbacks 才是文档认可的用法。
     */
    private val configWatcher = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = onScreenChanged()
        override fun onLowMemory() = Unit
    }

    private fun refreshScreenSize() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    /**
     * 屏幕尺寸变了（一般是转屏）。
     *
     * 之前把宽高缓存下来就不管了，转屏后宽高互换，球的坐标和夹取边界全按旧屏幕算，
     * 结果就是球被挤到看不见的地方 —— 用户看到的是「球没了」。
     * 这里按比例把球搬到新屏幕的对应位置，让它继续靠在原来那一边。
     */
    private fun onScreenChanged() {
        val oldWidth = screenWidth
        val oldHeight = screenHeight
        refreshScreenSize()
        if (oldWidth == screenWidth && oldHeight == screenHeight) return

        Log.i(TAG, "屏幕尺寸变化 ${oldWidth}x$oldHeight -> ${screenWidth}x$screenHeight")

        // 面板的宽高也是按旧屏幕算的，先收掉，下次打开会按新尺寸重建
        hidePanel()

        val view = ballView ?: return
        val params = ballParams ?: return

        val fx = if (oldWidth > 0) params.x.toFloat() / oldWidth else 0f
        val fy = if (oldHeight > 0) params.y.toFloat() / oldHeight else 0f

        params.x = (fx * screenWidth).toInt()
            .coerceIn(0, (screenWidth - ballSize).coerceAtLeast(0))
        params.y = (fy * screenHeight).toInt()
            .coerceIn(0, (screenHeight - ballSize).coerceAtLeast(0))

        // 球自己拖拽时的边界也得跟着换
        view.screenWidth = screenWidth
        view.screenHeight = screenHeight

        runCatching { windowManager.updateViewLayout(view, params) }
        Prefs.saveBallPos(this, params.x, params.y)
    }

    override fun onDestroy() {
        hidePanel()
        ballView?.let { runCatching { windowManager.removeView(it) } }
        runCatching { application.unregisterComponentCallbacks(configWatcher) }
        ballView = null
        ballParams = null
        ballAttached = false
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    // ── 前台服务 ────────────────────────────────────────────────

    private fun startForegroundCompat() {
        ensureChannel()
        // API 34 起 startForeground 必须带类型，用 ServiceCompat 兼容低版本
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_MIN：悬浮球常驻，通知尽量别闪、别响、别占状态栏图标
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    // ── 悬浮球 ──────────────────────────────────────────────────

    private fun addBall() {
        val defaultX = (screenWidth - ballSize - dp(8f)).coerceAtLeast(0)
        val defaultY = screenHeight / 3
        val (savedX, savedY) = Prefs.ballPos(this, defaultX, defaultY)

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, (screenWidth - ballSize).coerceAtLeast(0))
            y = savedY.coerceIn(0, (screenHeight - ballSize).coerceAtLeast(0))
        }

        val view = BallView(
            context = this,
            colors = OverlayColors.resolve(this),
            params = params,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onTap = { togglePanel() },
            onPositionChanged = { x, y -> Prefs.saveBallPos(this, x, y) }
        )

        val result = runCatching { windowManager.addView(view, params) }
        if (result.isFailure) {
            val t = result.exceptionOrNull()
            lastError = "addView 失败：${t?.javaClass?.simpleName}: ${t?.message}" +
                "（悬浮窗权限=${Perms.canDrawOverlay(this)}）"
            Log.e(TAG, "addView failed", t)
            Toast.makeText(this, "悬浮球添加失败，请确认已授予悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        ballView = view
        ballParams = params
        ballAttached = true
        Log.i(TAG, "悬浮球已添加 x=${params.x} y=${params.y} size=$ballSize 屏幕=${screenWidth}x$screenHeight")
    }

    // ── 浮窗 ────────────────────────────────────────────────────

    private fun togglePanel() {
        if (panelView != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return

        val panelWidth = minOf((screenWidth * 0.9f).toInt(), dp(380f))
        val panelHeight = (screenHeight * 0.62f).toInt()

        // 屏幕正中。之前是贴着球那一侧靠边站，看着偏，还是居中舒服。
        val x = (screenWidth - panelWidth) / 2
        val y = (screenHeight - panelHeight) / 2

        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不带 FLAG_NOT_FOCUSABLE：搜索框要能输入。
            // FLAG_NOT_TOUCH_MODAL 保证面板之外的触摸照常落到游戏上。
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }

        val view = PanelView(
            context = this,
            colors = OverlayColors.resolve(this),
            onCopy = { copyLineup(it) },
            onClose = { hidePanel() },
            onAddFromClipboard = { addFromClipboard() },
            onStopService = { stopSelf() },
        )

        if (!runCatching { windowManager.addView(view, params) }.isSuccess) return

        panelView = view
        view.requestFocus()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(140L).start()

        scope.launch {
            val list = runCatching { repository.getAll() }.getOrDefault(emptyList())
            panelView?.setData(list)
        }
    }

    private fun hidePanel() {
        val view = panelView ?: return
        panelView = null
        runCatching { windowManager.removeView(view) }
    }

    // ── 复制 ────────────────────────────────────────────────────

    private fun copyLineup(lineup: Lineup) {
        // 先直接写一次兜底。
        runCatching { Clip.copy(this, lineup.code) }

        // 再借透明 Activity 在有焦点的情况下写一次。
        // 部分 ROM 只认后者；重复写同一份内容没有副作用。
        val started = runCatching {
            startActivity(
                Intent(this, ClipboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(ClipboardActivity.EXTRA_TEXT, lineup.code)
                    .putExtra(ClipboardActivity.EXTRA_LABEL, lineup.title)
            )
        }.isSuccess

        if (!started) {
            // Activity 起不来（被厂商 ROM 拦了），至少给个反馈
            Toast.makeText(this, "已复制：${lineup.title}", Toast.LENGTH_SHORT).show()
        }

        scope.launch { runCatching { repository.markUsed(lineup.id) } }
        hidePanel()
    }

    // ── 从剪贴板快速新增 ────────────────────────────────────────

    /**
     * 浮窗里的「＋」：把剪贴板里刚复制的阵容码存进来。
     *
     * 注意这里**不在 Service 里读剪贴板** —— 浮窗窗口不算「有焦点的应用」，
     * Android 10 起读剪贴板要求焦点，国产 ROM 卡得更严，直接读会拿到 null，
     * 用户看到的就是「点了没反应」。
     * 所以交给 [ClipboardActivity] 去读：真 Activity 一定有焦点，这是系统保证的。
     * 存完数据库会变，下面那个 Flow 订阅会自动把新阵容刷进面板。
     */
    private fun addFromClipboard() {
        val started = runCatching {
            startActivity(
                Intent(this, ClipboardActivity::class.java)
                    .setAction(ClipboardActivity.ACTION_IMPORT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }.isSuccess

        if (!started) {
            toast("打不开导入窗口，去系统设置里给本应用开「后台弹出界面」权限")
        }
    }

    /** 数据一变就刷新面板，浮窗里新增完立刻能看到新行 */
    private fun observeLineups() {
        scope.launch {
            runCatching {
                repository.observeAll().collect { list -> panelView?.setData(list) }
            }
        }
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }

    private fun dp(value: Float): Int = ChipBackground.dp(this, value)

    companion object {
        private const val TAG = "TftCodeBox"
        private const val CHANNEL_ID = "floating_ball"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.wangye.tftbox.action.STOP_FLOATING"

        /** 服务活着（onCreate 到 onDestroy 之间） */
        @Volatile
        var isRunning: Boolean = false
            internal set

        /** 球真的挂到窗口上了。isRunning 为 true 但这个是 false，就说明挂在 addView 那一步 */
        @Volatile
        var ballAttached: Boolean = false
            internal set

        /** 最后一次失败原因，给设置页的自检看 */
        @Volatile
        var lastError: String? = null
            internal set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }
}
