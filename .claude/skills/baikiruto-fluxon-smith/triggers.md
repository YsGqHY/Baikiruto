# 触发器类型与配置语法

## 触发器总览

Baikiruto 物品脚本通过 `ItemScriptTrigger` 枚举定义触发时机。触发器分为两大类：**生命周期触发器**和**事件触发器**。

### 生命周期触发器

在物品构建/释放流程中执行，写在 `scripts:` 节点下。

| 触发器 | 配置键 | 执行时机 |
|--------|--------|----------|
| `BUILD` | `build` | 物品构建时（`Item.build()`） |
| `RELEASE` | `release` | 物品释放时（`ItemStream.toItemStack()`） |
| `RELEASE_DISPLAY` | `release_display` | 显示层释放时（lore/name 渲染后） |
| `DROP` | `drop` | 物品 drop 阶段（先逆序执行 metas 的 drop，再执行物品自身 drop） |

```yaml
scripts:
  build: |
    &ops.setData("created_at", "now")
    return item
  release: |
    &ops.setData("last_release", "release")
    return item
  release_display: |
    &ops.setData("last_release_display", "release_display")
    return item
  drop: |
    &ops.setData("last_trigger", "drop")
    return item
```

### 事件触发器

由 Bukkit 事件驱动，写在 `event:` 节点下。

| 触发器 | 配置键 | Bukkit 事件 | 说明 |
|--------|--------|-------------|------|
| `USE` | `on_use` | `PlayerInteractEvent` (右键) / `PlayerItemConsumeEvent` | 使用物品（右键交互 + 消耗） |
| `INTERACT` | `on_interact` | `PlayerInteractEvent` | 任意交互（左键/右键均触发） |
| `LEFT_CLICK` | `on_left_click` | `PlayerInteractEvent` | 左键点击 |
| `RIGHT_CLICK` | `on_right_click` | `PlayerInteractEvent` | 右键点击 |
| `RIGHT_CLICK_ENTITY` | `on_right_click_entity` | `PlayerInteractEntityEvent` | 右键点击实体 |
| `ATTACK` | `on_attack` | `EntityDamageByEntityEvent` | 攻击实体 |
| `DAMAGE` | `on_damage` | `PlayerItemDamageEvent` | 物品受到耐久损耗 |
| `BLOCK_BREAK` | `on_block_break` | `BlockBreakEvent` | 破坏方块 |
| `ITEM_BREAK` | `on_item_break` | `PlayerItemBreakEvent` | 物品耐久归零损坏 |
| `CONSUME` | `on_consume` | `PlayerItemConsumeEvent` | 消耗物品（食物/药水） |
| `PICKUP` | `on_pickup` | `EntityPickupItemEvent` | 拾取物品 |
| `SWAP_TO_MAINHAND` | `on_swap_to_mainhand` | `PlayerSwapHandItemsEvent` | 切换到主手 |
| `SWAP_TO_OFFHAND` | `on_swap_to_offhand` | `PlayerSwapHandItemsEvent` | 切换到副手 |
| `INVENTORY_CLICK` | `on_inventory_click` | `InventoryClickEvent` | 背包点击 |
| `SELECT` | `on_select` | `PlayerItemHeldEvent` | 切换快捷栏选中 |
| `ASYNC_TICK` | `on_async_tick` | 无（定时调度） | 由 `config.yml` 的 `operations.async-tick` 与物品 `meta.async-tick` 控制，默认 100 tick 周期 |
| `DEATH` | `on_death` | `PlayerDeathEvent` | 玩家死亡，全身装备+手持 |
| `KILL` | `on_kill` | `EntityDeathEvent` | 玩家击杀实体，主手 |
| `HURT` | `on_hurt` | `EntityDamageEvent` | 玩家受伤，全身装备+手持 |
| `SHOOT` | `on_shoot` | `ProjectileLaunchEvent` | 发射弹射物，主手 |
| `PROJECTILE_HIT` | `on_projectile_hit` | `ProjectileHitEvent` | 弹射物命中，主手 |
| `SNEAK` | `on_sneak` | `PlayerToggleSneakEvent` | 切换潜行，全身装备+手持 |
| `SPRINT` | `on_sprint` | `PlayerToggleSprintEvent` | 切换疾跑，全身装备+手持 |
| `JUMP` | `on_jump` | `PlayerJumpEvent` | 跳跃，全身装备+手持 |
| `RESPAWN` | `on_respawn` | `PlayerRespawnEvent` | 复活，全身装备+手持 |
| `EQUIP` | `on_equip` | `InventoryClickEvent` | 穿戴装备，context 含 `slot` |
| `UNEQUIP` | `on_unequip` | `InventoryClickEvent` | 脱下装备，context 含 `slot` |

```yaml
event:
  on_right_click: |
    &ops.setCooldown(80)
    &ops.signal("script_dispatched")
    &ops.setData("last_trigger", "right_click")
    return item
  on_attack: |
    target = &event.getEntity()
    &target.setFireTicks(60)
    &ops.damage(1)
    return item
```

### 触发器键名别名

配置键支持多种写法，不区分大小写，`-` 和 `_` 等价：

| 标准键 | 等价别名 |
|--------|----------|
| `build` | `on_build`, `onbuild` |
| `on_use` | `use`, `onuse` |
| `on_attack` | `attack`, `onattack`, `on_sword`, `onsword` |
| `on_pickup` | `pickup`, `on_pick`, `onpick`, `onpickup` |
| `on_inventory_click` | `inventory_click`, `on_click`, `onclick` |
| `on_async_tick` | `async_tick`, `on_tick`, `ontick` |
| `on_death` | `death`, `ondeath` |
| `on_kill` | `kill`, `onkill` |
| `on_hurt` | `hurt`, `onhurt` |
| `on_shoot` | `shoot`, `onshoot` |
| `on_projectile_hit` | `projectile_hit`, `onprojectilehit` |
| `on_sneak` | `sneak`, `onsneak` |
| `on_sprint` | `sprint`, `onsprint` |
| `on_jump` | `jump`, `onjump` |
| `on_respawn` | `respawn`, `onrespawn` |
| `on_equip` | `equip`, `onequip` |
| `on_unequip` | `unequip`, `onunequip` |

其余触发器同理，均支持 `on_xxx` / `xxx` / `onxxx` 三种形式；`-` 和 `_` 等价。

---

## 触发器联动

部分 Bukkit 事件会同时触发多个触发器，按顺序依次执行：

| 玩家操作 | 触发顺序 |
|----------|----------|
| 左键点击空气/方块 | `INTERACT` → `LEFT_CLICK` |
| 右键点击空气/方块 | `INTERACT` → `RIGHT_CLICK` → `USE` |
| 右键点击实体 | `RIGHT_CLICK_ENTITY` |
| 消耗食物/药水 | `CONSUME` → `USE` |

同一事件中，任一触发器的脚本取消事件后，后续触发器不再执行。

---

## 取消事件语法 `!!`

在触发器键名后追加 `!!` 可自动取消对应的 Bukkit 事件（调用 `event.setCancelled(true)`），无需在脚本中手动处理。

```yaml
event:
  # 右键时自动取消事件（阻止原版交互），然后执行脚本
  on_right_click!!: |
    &player?.sendMessage("&aCustom right click!")
    &ops.setCooldown(40)
    return item

  # 仅取消事件，不执行脚本（空脚本或省略脚本体）
  on_left_click!!:
```

`!!` 的取消在脚本执行之前生效。即使脚本体为空，事件也会被取消。

### `!!` 与自动冷却

如果物品配置了 `meta.cooldown.ticks` 或 `components.use_cooldown.seconds`，脚本成功处理后会自动应用冷却；但事件已经被 `!!` 或事件总线取消时，默认不会应用自动冷却。需要取消后仍进入冷却时，配置触发器白名单：

```yaml
meta:
  cooldown:
    ticks: 80
    apply-on-cancelled-triggers:
      - on_right_click
      - on_shoot
# 或 components:
components:
  use_cooldown:
    seconds: 4.0
    apply_on_cancelled_triggers:
      - right_click
```

冷却期间的行为：
- `on_use`、`on_interact`、`on_right_click`、`on_right_click_entity`、`on_consume`、`on_shoot` 是硬阻断，会取消底层事件并跳过脚本。
- `on_attack`、`on_left_click` 是软阻断，只跳过脚本，不取消原版伤害或点击行为。

---

## Meta 脚本

每个 Meta 可以拥有独立的 `scripts:` 节点，在物品的 `metas:` 下定义。事件脚本顺序是物品自身脚本先执行，再按 metas 定义顺序执行；`drop` 阶段特殊，先按 metas 逆序执行各 Meta 的 `drop()` 和 DROP 脚本，最后执行物品自身 DROP 脚本。

```yaml
metas:
  trace:
    scripts:
      build: |
        if (&ctx["debug"] == true) {
            &sender?.sendMessage("[Baikiruto] meta build -> " + &itemId)
        }
        return item
      drop: |
        return item
  enchant_glow:
    scripts:
      build: |
        &ops.setData("glow_applied", true)
        return item
```

Meta 脚本的上下文变量与物品脚本完全一致，`&itemId` 格式为 `"物品ID:meta:Meta名"`。

---

## i18n 本地化脚本

物品和 Meta 的脚本均支持按语言环境提供不同版本。i18n 脚本在 `i18n:` 节点下按 locale 分组定义。

```yaml
i18n:
  en_us:
    scripts:
      build: |
        &player?.sendMessage("&aBuilding item (English)")
        return item
    event:
      on_right_click: |
        &player?.sendMessage("&aRight clicked! (English)")
        return item
  zh_cn:
    scripts:
      build: |
        &player?.sendMessage("&a正在构建物品")
        return item
    event:
      on_right_click: |
        &player?.sendMessage("&a右键点击！")
        return item
```

解析优先级：精确 locale（如 `zh_cn`）→ 语言前缀（如 `zh`）→ 默认脚本（无 i18n）。

locale 格式自动标准化：不区分大小写，`-` 转为 `_`（如 `en-US` → `en_us`）。

i18n 脚本同样支持 `!!` 取消事件语法。

---

## async-tick 条件脚本

`on_async_tick` 由全局 `operations.async-tick.enabled` 控制，并可在物品 `meta.async-tick` 中配置间隔和条件。调度会对玩家、槽位和物品 ID 计算稳定偏移，避免同 tick 集中执行。

```yaml
meta:
  async-tick:
    enabled: true
    interval: 40
    conditions:
      slots:
        - MAINHAND
      sneaking: true
      world: world
      game-mode: SURVIVAL
      permission: baikiruto.async.fire
event:
  on_async_tick: |
    &ops.setData("tick_slot", &ctx["slot"])
    &ops.setData("tick_index", &ctx["slot_index"])
    return item
```

可用布尔条件：`sneaking`、`sprinting`、`swimming`、`gliding`、`flying`、`on-ground`、`in-vehicle`、`burning`、`blocking`。可用列表条件：`slots`、`worlds`、`game-modes`、`permissions`。

---

## data-mapper 脚本

`data-mapper` 用于将运行时数据动态转换为显示文本，在 lore 占位符解析时执行。

```yaml
lore:
  item_description:
    - "&7Durability: {durability_line}"
    - "&7Status: {fire_status}"
data-mapper:
  durability_line: |
    current = &data["durability_current"] ?: &data["durability"] ?: 0
    max = &data["durability"] ?: 0
    return &current :: toString() + "/" + &max :: toString()
  fire_status: |
    cd = &ops.cooldown()
    return &cd > 0 ? "&cCooling" : "&aReady"
```

`data-mapper` 的键名对应 lore 中的 `{占位符}`，脚本返回值作为替换文本。可用变量包括 `&data`、`&stream`、`&itemId`、`&it`，并继承上次调用上下文中的 `player/sender/event/locale` 等变量。
