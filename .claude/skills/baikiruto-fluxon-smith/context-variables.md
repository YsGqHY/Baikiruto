# 脚本上下文变量

Baikiruto 物品脚本执行时，`ItemScriptExecutor` 会自动注入以下变量到 Fluxon 运行时环境。所有变量通过 `&变量名` 引用。

## 变量表

| 变量名 | 类型 | 说明 | 可空 |
|--------|------|------|------|
| `sender` | `CommandSender` | 命令发送者（触发脚本的来源） | 是 |
| `player` | `Player` | 玩家实例，从 context 或 sender 推断 | 是 |
| `item` | `ItemStack` | 当前物品的快照克隆（可修改，修改会同步回 stream） | 否 |
| `stream` | `ItemStream` | 当前物品流对象（底层数据访问） | 否 |
| `ops` | `ItemScriptOps` | 脚本操作 DSL 对象（推荐使用） | 否 |
| `event` | `Any` | 触发此脚本的 Bukkit 事件对象 | 是 |
| `ctx` | `Map<String, Any?>` | 原始上下文映射（包含所有传入参数） | 否 |
| `itemId` | `String` | 当前物品的 ID（如 `"example:fire_sword"`） | 否 |
| `trigger` | `String` | 触发器名称（枚举 name 小写，如 `"attack"`、`"build"`、`"right_click"`） | 否 |
| `locale` | `String` | 玩家 locale，自动标准化为小写下划线格式（如 `zh_cn`）；仅玩家上下文中存在 | 是 |
| `slot` | `String` | 全身装备触发器和 async tick 注入的槽位 | 是 |
| `slot_index` | `Int` | `on_async_tick` 扫描背包 contents 时的槽位下标 | 是 |

## 变量详解

### `&sender` / `&player`

```fluxon
// sender 可能是控制台，player 一定是玩家（或 null）
if (&player != null) {
    &player.sendMessage("&aHello!")
}

// 安全访问
&player?.sendMessage("&aHello!")
```

`player` 的推断逻辑：优先取 `context["player"]`，其次尝试将 `sender` 转为 `Player`。

### `&item`

`ItemStream.snapshot()` 的克隆。脚本中对 `&item` 的修改会在脚本执行后通过 `syncScriptResult()` 同步回 ItemStream。

```fluxon
// 直接操作 ItemStack（不推荐，优先用 &ops）
meta = &item.getItemMeta()
&meta.setDisplayName("&6Modified Name")
&item.setItemMeta(&meta)
return &item
```

### `&stream`

底层 `ItemStream` 对象。一般不直接使用，优先通过 `&ops` 操作。

```fluxon
// 读取运行时数据
value = &stream.getRuntimeData("my_key")

// 检查物品状态
if (&stream.isOutdated()) {
    &ops.rebuild()
}
```

### `&ops`

推荐的脚本操作入口。详见 [ops-api.md](ops-api.md)。

### `&event`

触发脚本的原始 Bukkit 事件。不同触发器对应不同事件类型：

| 触发器 | 事件类型 |
|--------|----------|
| `on_attack` | `EntityDamageByEntityEvent` |
| `on_damage` | `PlayerItemDamageEvent` |
| `on_use` / `on_interact` | `PlayerInteractEvent` |
| `on_left_click` / `on_right_click` | `PlayerInteractEvent` |
| `on_right_click_entity` | `PlayerInteractEntityEvent` |
| `on_block_break` | `BlockBreakEvent` |
| `on_item_break` | `PlayerItemBreakEvent` |
| `on_consume` | `PlayerItemConsumeEvent` |
| `on_pickup` | `EntityPickupItemEvent` |
| `on_swap_to_mainhand` / `on_swap_to_offhand` | `PlayerSwapHandItemsEvent` |
| `on_inventory_click` | `InventoryClickEvent` |
| `on_select` | `PlayerItemHeldEvent` |
| `on_death` | `PlayerDeathEvent` |
| `on_kill` | `EntityDeathEvent` |
| `on_hurt` | `EntityDamageEvent` |
| `on_shoot` | `ProjectileLaunchEvent` |
| `on_projectile_hit` | `ProjectileHitEvent` |
| `on_sneak` | `PlayerToggleSneakEvent` |
| `on_sprint` | `PlayerToggleSprintEvent` |
| `on_jump` | `PlayerJumpEvent` |
| `on_respawn` | `PlayerRespawnEvent` |
| `on_equip` / `on_unequip` | `InventoryClickEvent` |
| `on_async_tick` | 无事件（定时触发，`event` 为 null） |
| `build` / `release` / `release_display` / `drop` | 无事件（生命周期触发） |

```fluxon
// 从攻击事件获取目标实体
target = &event.getEntity()
damage = &event.getDamage()
&target.setFireTicks(60)
```

### `&ctx`

原始上下文映射，包含调用方传入的所有参数。常用于检查调试标志或自定义参数。

```fluxon
if (&ctx["debug"] == true) {
    &sender?.sendMessage("[Debug] script executed")
}

// 获取自定义上下文参数
customValue = &ctx["my_custom_param"]
```

### `&slot` / `&slot_index`

全身装备触发器（`on_hurt`、`on_death`、`on_sneak`、`on_sprint`、`on_jump`、`on_respawn`）会遍历主手、副手和四件装备，`slot` 可能是 `MAINHAND`、`OFFHAND`、`HEAD`、`CHEST`、`LEGS`、`FEET`。

`on_async_tick` 会遍历玩家背包 contents，`slot` 可能是 `MAINHAND`、`HOTBAR`、`INVENTORY`、`FEET`、`LEGS`、`CHEST`、`HEAD`、`OFFHAND`，并额外提供 `slot_index`。

```fluxon
&ops.setData("last_slot", &ctx["slot"])
&ops.setData("last_slot_index", &?slot_index)
```

### `&itemId` / `&trigger`

```fluxon
// 日志记录
print("Item " + &itemId + " triggered " + &trigger)
```

## data-mapper 上下文

`data-mapper` 脚本的上下文与普通脚本略有不同，主要用于将运行时数据转换为显示文本：

```yaml
data-mapper:
  durability_line: |
    current = &data["durability_current"] ?: &data["durability"] ?: 0
    max = &data["durability"] ?: 0
    return &current :: toString() + "/" + &max :: toString()
```

`data-mapper` 中可用变量：
- `&data`：当前 `stream.runtimeData` 映射
- `&stream`：当前 `ItemStream`
- `&itemId`：当前物品 ID
- `&it`：该 mapper key 在执行前的旧值
- 以及上次物品构建/触发留下的调用上下文，如 `player`、`sender`、`event`、`locale` 等（存在性取决于场景）
