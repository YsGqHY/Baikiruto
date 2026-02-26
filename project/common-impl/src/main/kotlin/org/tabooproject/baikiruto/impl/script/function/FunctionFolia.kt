package org.tabooproject.baikiruto.impl.script.function

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.tabooproject.baikiruto.impl.script.relocate.FluxonRelocate
import org.tabooproject.fluxon.FluxonPlugin
import org.tabooproject.fluxon.function.platform.FunctionExecutor
import org.tabooproject.fluxon.runtime.Environment
import org.tabooproject.fluxon.runtime.FluxonRuntime
import org.tabooproject.fluxon.runtime.Function
import org.tabooproject.fluxon.runtime.java.Export
import org.tabooproject.fluxon.util.getFluxonScript
import org.tabooproject.fluxon.util.invokeInline
import taboolib.common.LifeCycle
import taboolib.common.Requires
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor
import taboolib.platform.util.submit
import java.util.*

@Requires(missingClasses = ["!org.tabooproject.fluxon.ParseScript"])
@FluxonRelocate
object FunctionFolia {

    @Awake(LifeCycle.INIT)
    private fun init() {
        with(FluxonRuntime.getInstance()) {
            // Builder 模式
            exportRegistry.registerClass(TaskBuilder::class.java)
            registerExtension(FunctionExecutor.TaskBuilder::class.java)
                .function("on", 1) {
                    val builder = it.target ?: return@function null
                    TaskBuilder(builder.env).apply {
                        this.async = builder.async
                        this.delay = builder.delay
                        this.period = builder.period
                    }.on(it.getArgument(0))
                }
                .function("scheduler", 1) {
                    val builder = it.target ?: return@function null
                    TaskBuilder(builder.env).apply {
                        this.async = builder.async
                        this.delay = builder.delay
                        this.period = builder.period
                    }.scheduler(it.getBoolean(0))
                }
        }
    }

    /**
     * 执行任务的核心方法
     */
    fun executeTask(
        lambda: Function,
        env: Environment,
        async: Boolean,
        delay: Long,
        period: Long,
        useScheduler: Boolean,
        on: Any?
    ): PlatformExecutor.PlatformTask? {
        val script = env.getFluxonScript()
        if (script == null) {
            if (!FluxonPlugin.DEFAULT_ALLOW_EXECUTE_TASK_ON_NON_SCRIPT_ENV) {
                warning("无法注册调度器：没有找到脚本环境。")
                return null
            }
        }
        val task = when (on) {
            is Entity -> on.submit(async = async, delay = delay, period = period, useScheduler = useScheduler) {
                lambda.invokeInline(env, 1, this, null, null, null, this)
            }
            is Location -> on.submit(async = async, delay = delay, period = period, useScheduler = useScheduler) {
                lambda.invokeInline(env, 1, this, null, null, null, this)
            }
            is Block -> on.submit(async = async, delay = delay, period = period, useScheduler = useScheduler) {
                lambda.invokeInline(env, 1, this, null, null, null, this)
            }
            is Chunk -> on.submit(async = async, delay = delay, period = period, useScheduler = useScheduler) {
                lambda.invokeInline(env, 1, this, null, null, null, this)
            }
//            is World -> on.submit(async = async, delay = delay, period = period, useScheduler = useScheduler) {
//                lambda.invokeInline(env, 1, this, null, null, null, this)
//            }
            else -> error("unsupported on $on")
        }
        // 如果是周期性任务，注册为可释放资源
        if (script != null) {
            if (period > 0) {
                val resourceId = "task_${UUID.randomUUID()}"
                script.resources[resourceId] = AutoCloseable {
                    task.cancel()
                }
            }
        }
        return task
    }

    /**
     * 任务构建器
     */
    @FluxonRelocate
    class TaskBuilder(val env: Environment) {

        var async = false
        var delay = 0L
        var period = 0L
        var on: Any? = null
        var scheduler = true /** 非 folia 是否使用 scheduler */

        @Export
        fun async(): TaskBuilder {
            this.async = true
            return this
        }

        @Export
        fun delay(ticks: Long): TaskBuilder {
            this.delay = ticks
            return this
        }

        @Export
        fun period(ticks: Long): TaskBuilder {
            this.period = ticks
            return this
        }

        @Export
        fun on(on: Any?): TaskBuilder {
            this.on = on
            return this
        }

        @Export
        fun scheduler(scheduler: Boolean): TaskBuilder {
            this.scheduler = scheduler
            return this
        }

        @Export
        fun run(fn: Function): PlatformExecutor.PlatformTask? {
            return executeTask(fn, env, async, delay, period, scheduler, on)
        }
    }
}