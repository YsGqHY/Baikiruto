package org.tabooproject.baikiruto.impl.ops

import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Schedule
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.module.lang.sendLang
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object BaikirutoFileWatcher {

    private val running = AtomicBoolean(false)
    private val reloadQueued = AtomicBoolean(false)
    private val queuedAtTick = AtomicLong(0L)
    private val schedulerTick = AtomicLong(0L)
    @Volatile
    private var queuedSource: String? = null
    private var watchService: WatchService? = null
    private var watcherThread: Thread? = null

    @Awake(LifeCycle.ACTIVE)
    private fun start() {
        if (!BaikirutoSettings.watcherEnabled) {
            return
        }
        val watchPath = itemsDir().toPath()
        if (!watchPath.toFile().exists()) {
            watchPath.toFile().mkdirs()
        }
        if (!running.compareAndSet(false, true)) {
            return
        }
        watchService = FileSystems.getDefault().newWatchService().also { service ->
            watchPath.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            )
        }
        watcherThread = Thread({ watchLoop(watchPath) }, "Baikiruto-FileWatcher").apply {
            isDaemon = true
            start()
        }
        console().sendLang("log-watcher-started", watchPath)
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        running.set(false)
        try {
            watchService?.close()
        } catch (_: Exception) {
            // ignore watcher shutdown exception
        }
        watcherThread = null
        watchService = null
        reloadQueued.set(false)
        queuedSource = null
        queuedAtTick.set(0L)
        schedulerTick.set(0L)
    }

    @Schedule(period = 1)
    private fun flushQueuedReload() {
        val currentTick = schedulerTick.incrementAndGet()
        if (!reloadQueued.get()) {
            return
        }
        val queueTick = queuedAtTick.get()
        val delayTicks = BaikirutoSettings.watcherDebounceTicks.coerceAtLeast(1L)
        if (currentTick - queueTick < delayTicks) {
            return
        }
        val source = queuedSource ?: "watcher"
        try {
            BaikirutoReloader.reloadItemsFromWatcher(source)
        } catch (ex: Throwable) {
            console().sendLang("log-watcher-reload-failed", ex.message.orEmpty())
        } finally {
            if (queuedAtTick.compareAndSet(queueTick, 0L)) {
                queuedSource = null
                reloadQueued.set(false)
            }
        }
    }

    private fun watchLoop(watchPath: Path) {
        while (running.get()) {
            val key = try {
                watchService?.take()
            } catch (_: Throwable) {
                return
            } ?: return
            val shouldReload = key.pollEvents()
                .mapNotNull { it.context()?.toString() }
                .any { it.endsWith(".yml", true) || it.endsWith(".yaml", true) }
            key.reset()
            if (shouldReload) {
                queueReload("watcher:${watchPath.fileName}")
            }
        }
    }

    private fun queueReload(source: String) {
        queuedSource = source
        queuedAtTick.set(schedulerTick.get())
        reloadQueued.set(true)
    }

    private fun itemsDir(): File {
        return File(getDataFolder(), "items")
    }
}
