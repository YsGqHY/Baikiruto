package org.tabooproject.baikiruto.impl.script.function

import ink.ptms.um.Mythic
import ink.ptms.um.Mob
import ink.ptms.um.MobType
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.script.FluxonChecker
import org.tabooproject.baikiruto.impl.script.relocate.FluxonRelocate
import org.tabooproject.fluxon.runtime.FluxonRuntime
import org.tabooproject.fluxon.runtime.FunctionSignature
import org.tabooproject.fluxon.runtime.Type
import org.tabooproject.fluxon.runtime.java.Export
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.util.submit
import java.util.UUID

/**
 * 将 MythicMobs (um) 的 API 封装为 Fluxon 脚本函数。
 *
 * 脚本中通过 `mythic()` 获取 API 对象，调用其方法操作 MythicMobs。
 *
 * 示例:
 * ```fluxon
 * mm = mythic()
 * &mm :: spawnMob("SkeletonKing", &player :: getLocation(), 5.0)
 * &mm :: castSkill(&player, "Fireball", 1.0)
 *
 * // 判断实体是否为 MM 怪物
 * if &mm :: isMythicMob(&entity) {
 *     mob = &mm :: getMob(&entity)
 *     print(&mob :: getId())
 * }
 *
 * // 获取 MM 物品
 * item = &mm :: getItem("CustomSword")
 * ```
 */
@FluxonRelocate
object FunctionMythicMobs {

    @Awake(LifeCycle.ENABLE)
    private fun init() {
        if (!BaikirutoSettings.mythicHookEnabled) {
            return
        }
        // um 库始终被打包进 JAR，但 MythicMobs 插件不一定安装
        // Mythic.isLoaded() 检测 MythicMobs 插件是否存在
        if (!Mythic.isLoaded() || !FluxonChecker.isReady()) {
            return
        }
        with(FluxonRuntime.getInstance()) {
            // 注册 MythicApi 的 @Export(shared = true) 方法，自动导出到共享注册表
            exportRegistry.registerClass(MythicApi::class.java)

            // 注册并导出顶层函数
            registerFunction("mythic", FunctionSignature.returnsObject().noParams()) { it.setReturnRef(MythicApi) }
            exportRegisteredFunction("mythic")
            registerFunction("mythicmobs", FunctionSignature.returnsObject().noParams()) { it.setReturnRef(MythicApi) }
            exportRegisteredFunction("mythicmobs")

            // Mob 扩展函数（使用 sharedFunction 一步注册+导出）
            registerExtension(Mob::class.java)
                .sharedFunction("getId", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.id) }
                .sharedFunction("getDisplayName", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.displayName) }
                .sharedFunction("getLevel", FunctionSignature.returns(Type.D).noParams()) { it.setReturnDouble(it.target!!.level) }
                .sharedFunction("getEntity", FunctionSignature.returnsObject().noParams()) { it.setReturnRef(it.target!!.entity) }
                .sharedFunction("getFaction", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.faction) }
                .sharedFunction("getStance", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.stance) }

            // MobType 扩展函数
            registerExtension(MobType::class.java)
                .sharedFunction("getId", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.id) }
                .sharedFunction("getDisplayName", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.displayName) }
                .sharedFunction("getEntityType", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.entityType) }
                .sharedFunction("spawn", FunctionSignature.returnsObject().params(Type.OBJECT, Type.D)) { ctx ->
                    val location = ctx.getRef(0) as Location
                    val level = ctx.getDouble(1)
                    ctx.setReturnRef(ctx.target!!.spawn(location, level))
                }
        }
    }

    object MythicApi {

        // ── 状态 ──

        @Export(shared = true)
        fun isLoaded(): Boolean {
            return Mythic.isLoaded()
        }

        // ── 怪物 ──

        @Export(shared = true)
        fun getMob(entity: Entity): Mob? {
            if (!isLoaded()) {
                return null
            }
            return Mythic.API.getMob(entity)
        }

        @Export(shared = true)
        fun getMobByUUID(uuid: String): Mob? {
            if (!isLoaded()) {
                return null
            }
            val parsed = parseUuid(uuid) ?: return null
            return Mythic.API.getMob(parsed)
        }

        @Export(shared = true)
        fun getMobType(id: String): MobType? {
            if (!isLoaded()) {
                return null
            }
            return Mythic.API.getMobType(id)
        }

        @Export(shared = true)
        fun getMobIds(): List<String> {
            if (!isLoaded()) {
                return emptyList()
            }
            return Mythic.API.getMobIDList()
        }

        @Export(shared = true)
        fun spawnMob(id: String, location: Location, level: Double): Mob? {
            if (!isLoaded()) {
                return null
            }
            val mobType = Mythic.API.getMobType(id) ?: return null
            return mobType.spawn(location, level)
        }

        @Export(shared = true)
        fun isMythicMob(entity: Entity): Boolean {
            if (!isLoaded()) {
                return false
            }
            return Mythic.API.getMob(entity) != null
        }

        // ── 物品 ──

        @Export(shared = true)
        fun getItem(id: String): ItemStack? {
            if (!isLoaded()) {
                return null
            }
            return Mythic.API.getItemStack(id)
        }

        @Export(shared = true)
        fun getItemWithPlayer(id: String, player: Player): ItemStack? {
            if (!isLoaded()) {
                return null
            }
            return Mythic.API.getItemStack(id, player)
        }

        @Export(shared = true)
        fun getItemId(itemStack: ItemStack): String? {
            if (!isLoaded()) {
                return null
            }
            return Mythic.API.getItemId(itemStack)
        }

        @Export(shared = true)
        fun getItemIds(): List<String> {
            if (!isLoaded()) {
                return emptyList()
            }
            return Mythic.API.getItemIDList()
        }

        // ── 技能 ──

        @Export(shared = true)
        fun castSkill(caster: Entity, skillName: String, power: Float) {
            if (!isLoaded()) {
                return
            }
            caster.submit {
                if (caster.isValid) {
                    Mythic.API.castSkill(caster, skillName, power = power)
                }
            }
        }

        @Export(shared = true)
        fun castSkillAt(caster: Entity, skillName: String, target: LivingEntity, power: Float) {
            if (!isLoaded()) {
                return
            }
            caster.submit {
                if (caster.isValid && target.isValid) {
                    Mythic.API.castSkill(caster, skillName, target, power = power)
                }
            }
        }

        // ── 仇恨 ──

        @Export(shared = true)
        fun addThreat(entity: Entity, target: LivingEntity, amount: Double) {
            if (!isLoaded()) {
                return
            }
            val mob = Mythic.API.getMob(entity) ?: return
            mob.addThreat(entity, target, amount)
        }

        @Export(shared = true)
        fun reduceThreat(entity: Entity, target: LivingEntity, amount: Double) {
            if (!isLoaded()) {
                return
            }
            val mob = Mythic.API.getMob(entity) ?: return
            mob.reduceThreat(entity, target, amount)
        }

        private fun parseUuid(value: String): UUID? {
            return try {
                UUID.fromString(value)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
