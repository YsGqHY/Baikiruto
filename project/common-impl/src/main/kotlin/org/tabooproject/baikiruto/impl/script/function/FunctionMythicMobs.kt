package org.tabooproject.baikiruto.impl.script.function

import ink.ptms.um.Mythic
import ink.ptms.um.Mob
import ink.ptms.um.MobType
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.script.relocate.FluxonRelocate
import org.tabooproject.fluxon.runtime.FluxonRuntime
import org.tabooproject.fluxon.runtime.FunctionSignature
import org.tabooproject.fluxon.runtime.Type
import org.tabooproject.fluxon.runtime.java.Export
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
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

    @Awake(LifeCycle.INIT)
    private fun init() {
        // um 库始终被打包进 JAR，但 MythicMobs 插件不一定安装
        // Mythic.isLoaded() 检测 MythicMobs 插件是否存在
        if (!Mythic.isLoaded()) {
            return
        }
        with(FluxonRuntime.getInstance()) {
            exportRegistry.registerClass(MythicApi::class.java)
            registerFunction("mythic", FunctionSignature.returnsObject().noParams()) { it.setReturnRef(MythicApi) }
            registerFunction("mythicmobs", FunctionSignature.returnsObject().noParams()) { it.setReturnRef(MythicApi) }

            // Mob 扩展函数
            registerExtension(Mob::class.java)
                .function("getId", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.id) }
                .function("getDisplayName", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.displayName) }
                .function("getLevel", FunctionSignature.returns(Type.D).noParams()) { it.setReturnDouble(it.target!!.level) }
                .function("getEntity", FunctionSignature.returnsObject().noParams()) { it.setReturnRef(it.target!!.entity) }
                .function("getFaction", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.faction) }
                .function("getStance", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.stance) }

            // MobType 扩展函数
            registerExtension(MobType::class.java)
                .function("getId", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.id) }
                .function("getDisplayName", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.displayName) }
                .function("getEntityType", FunctionSignature.returns(Type.STRING).noParams()) { it.setReturnRef(it.target!!.entityType) }
                .function("spawn", FunctionSignature.returnsObject().params(Type.OBJECT, Type.D)) { ctx ->
                    val location = ctx.getRef(0) as Location
                    val level = ctx.getDouble(1)
                    ctx.setReturnRef(ctx.target!!.spawn(location, level))
                }
        }
    }

    object MythicApi {

        // ── 状态 ──

        @Export
        fun isLoaded(): Boolean {
            return Mythic.isLoaded()
        }

        // ── 怪物 ──

        @Export
        fun getMob(entity: Entity): Mob? {
            return runCatching { Mythic.API.getMob(entity) }.getOrNull()
        }

        @Export
        fun getMobByUUID(uuid: String): Mob? {
            val parsed = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return null
            return runCatching { Mythic.API.getMob(parsed) }.getOrNull()
        }

        @Export
        fun getMobType(id: String): MobType? {
            return runCatching { Mythic.API.getMobType(id) }.getOrNull()
        }

        @Export
        fun getMobIds(): List<String> {
            return runCatching { Mythic.API.getMobIDList() }.getOrDefault(emptyList())
        }

        @Export
        fun spawnMob(id: String, location: Location, level: Double): Mob? {
            val mobType = runCatching { Mythic.API.getMobType(id) }.getOrNull() ?: return null
            return runCatching { mobType.spawn(location, level) }.getOrNull()
        }

        @Export
        fun isMythicMob(entity: Entity): Boolean {
            return runCatching { Mythic.API.getMob(entity) }.getOrNull() != null
        }

        // ── 物品 ──

        @Export
        fun getItem(id: String): ItemStack? {
            return runCatching { Mythic.API.getItemStack(id) }.getOrNull()
        }

        @Export
        fun getItemWithPlayer(id: String, player: Player): ItemStack? {
            return runCatching { Mythic.API.getItemStack(id, player) }.getOrNull()
        }

        @Export
        fun getItemId(itemStack: ItemStack): String? {
            return runCatching { Mythic.API.getItemId(itemStack) }.getOrNull()
        }

        @Export
        fun getItemIds(): List<String> {
            return runCatching { Mythic.API.getItemIDList() }.getOrDefault(emptyList())
        }

        // ── 技能 ──

        @Export
        fun castSkill(caster: Entity, skillName: String, power: Float) {
            runCatching { Mythic.API.castSkill(caster, skillName, power = power) }
        }

        @Export
        fun castSkillAt(caster: Entity, skillName: String, target: LivingEntity, power: Float) {
            runCatching { Mythic.API.castSkill(caster, skillName, target, power = power) }
        }

        // ── 仇恨 ──

        @Export
        fun addThreat(entity: Entity, target: LivingEntity, amount: Double) {
            val mob = runCatching { Mythic.API.getMob(entity) }.getOrNull() ?: return
            runCatching { mob.addThreat(entity, target, amount) }
        }

        @Export
        fun reduceThreat(entity: Entity, target: LivingEntity, amount: Double) {
            val mob = runCatching { Mythic.API.getMob(entity) }.getOrNull() ?: return
            runCatching { mob.reduceThreat(entity, target, amount) }
        }
    }
}
