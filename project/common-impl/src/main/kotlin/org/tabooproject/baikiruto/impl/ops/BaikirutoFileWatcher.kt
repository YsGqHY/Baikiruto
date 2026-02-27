package org.tabooproject.baikiruto.impl.ops

import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.platform.util.submit
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.atomic.AtomicBoolean

object BaikirutoFileWatcher {

    private val running = AtomicBoolean(false)
    private val reloadQueued = AtomicBoolean(false)
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
        info("[Baikiruto] File watcher started at $watchPath")
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        running.set(false)
        runCatching { watchService?.close() }
        watcherThread = null
        watchService = null
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
        if (!reloadQueued.compareAndSet(false, true)) {
            return
        }
        val delayTicks = BaikirutoSettings.watcherDebounceTicks.coerceAtLeast(1L)
        submit(delay = delayTicks) {
            try {
                ItemDefinitionLoader.reloadItems(source)
            } catch (ex: Throwable) {
                warning("[Baikiruto] File watcher reload failed: ${ex.message}")
            } finally {
                reloadQueued.set(false)
            }
        }
    }

    private fun itemsDir(): File {
        return File(getDataFolder(), "items")
    }
}
