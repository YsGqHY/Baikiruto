# 内置 Meta 参考

`meta:` 节点下配置内置 Meta 类型。所有 Meta 配置被解析为 runtimeData，由对应 Feature 在物品构建时消费。

---

## durability — 自定义耐久

```yaml
meta:
  durability:
    synchronous: true              # 是否同步到原版耐久条（可选，默认 false）
    remains: "namespace:item_id"   # 耐久归零后替换的物品 ID（可选）
    bar-length: 12                 # 耐久条长度（可选，默认 10）
    bar-symbol:                    # 耐久条符号 [满, 空]（可选）
      - "&a|"
      - "&7|"
```

配合 `data:` 中的 `durability` 值使用：
```yaml
data:
  durability: 240                  # 最大耐久值
```

lore 中可用的自动占位符：
- `{durability_current}` — 当前耐久
- `{durability_max}` — 最大耐久（= data.durability）
- `{durability_bar}` — 渲染后的耐久条文本

---

## cooldown — 冷却

```yaml
meta:
  cooldown:
    ticks: 80                      # 冷却时间（tick，20 ticks = 1 秒）
    by-player: true                # 是否按玩家独立计算冷却（可选，默认 true）
    apply-on-cancelled-triggers:   # 事件被 !! 或事件总线取消后仍然应用冷却（可选）
      - on_right_click
      - on_shoot
```

别名：`ticks` / `time` / `value` 都表示冷却 tick；`cooldown: 80` 也可直接写成标量。

lore 中可用的自动占位符：
- `{cooldown_remaining}` — 剩余冷却 tick 数
- `{cooldown_remaining_seconds}` — 剩余冷却秒数（Double）

脚本中通过 `&ops.setCooldown(ticks)` 触发冷却，`&ops.cooldown()` 查询剩余 tick。

冷却阻断规则：
- 硬阻断：`on_use`、`on_interact`、`on_right_click`、`on_right_click_entity`、`on_consume`、`on_shoot` 在冷却期间取消底层事件并跳过脚本。
- 软阻断：`on_attack`、`on_left_click` 在冷却期间只跳过脚本，不取消原版伤害或点击行为。
- 触发器脚本实际执行后会自动应用配置的 `cooldown`，无需每次手写 `&ops.setCooldown()`；如果脚本用 `!!` 取消事件，默认不应用自动冷却，除非该触发器在 `apply-on-cancelled-triggers` 中。
- `apply-on-cancelled-triggers` 支持 `on_shoot`、`right_click` 等触发器别名，未知值会被忽略。

---

## unique — 唯一绑定

```yaml
meta:
  unique:
    enabled: true                  # 启用唯一标识（自动生成 UUID）
    bind-player: true              # 是否绑定玩家（可选，默认 false）
    deny-message: "&cThis item is bound to another player."  # 拒绝消息（可选）
```

lore 中可用的自动占位符：
- `{unique.player}` — 绑定的玩家名
- `{unique.uuid}` — 物品唯一 UUID
- `{unique.date}` — 绑定日期

脚本中通过 `&ops.owner()` / `&ops.isOwner()` / `&ops.bindOwner()` 操作。

---

## drop — 掉落实体展示

```yaml
meta:
  drop:
    display-name: "&6Protected Artifact"   # 物品实体自定义名称
    display-visible: true                  # 是否显示名称（可选，默认 true）
```

别名：
- `drop.display-name` / `drop.display_name` / `drop.displayName` / `drop.name`
- 顶层 `drop-name` / `drop_name` / `dropName`
- `drop.display-visible` / `drop.display_visible` / `drop.displayVisible` / `drop.visible`
- 顶层 `drop-visible` / `drop_visible` / `dropVisible`

行为：Baikiruto 物品生成掉落实体时，`ItemSpawnEvent` 会读取该配置并写入实体 `customName` 与 `isCustomNameVisible`。

---

## protection — 物品保护规则

```yaml
meta:
  protection:
    crafting:
      vanilla: true                 # 阻止原版工作台合成
      any: false                    # 阻止所有受支持工作站（优先级最高）
      stations:                    # 指定工作站黑名单
        - CRAFTING
        - ANVIL
        - GRINDSTONE
        - SMITHING
    containers:
      deny:
        - HOPPER
        - FURNACE
        - ARMOR_STAND
    destroy:
      enabled: true
      causes:
        - fire
        - lava
        - cactus
        - lightning
```

等价根节点：`protection`、`protect`、`rules`。快捷写法：

```yaml
meta:
  no-craft: true                   # 等价于 protection.crafting.any=true
  no-destroy: true                 # 等价于 protection.destroy.enabled=true + causes=[all]
```

`crafting` 支持布尔标量，等价于 `any`；`destroy` 支持布尔标量，等价于 `enabled`。

工作站别名：
- `CRAFTING` / `WORKBENCH` / `CRAFTING_TABLE`
- `STONECUTTER` / `STONE_CUTTER` / `切石机`
- `ENCHANTING` / `ENCHANT_TABLE` / `ENCHANTMENT_TABLE` / `附魔台`
- `ANVIL` / `铁砧`
- `GRINDSTONE` / `砂轮`
- `SMITHING` / `SMITHING_TABLE` / `锻造台`
- `CRAFTER` / `合成器`

容器别名：`DECORATED_POT`/`POT`/`陶罐`、`FURNACE`/`熔炉`、`BLAST_FURNACE`/`高炉`、`SMOKER`/`烟熏炉`、`ARMOR_STAND`/`盔甲架`、`HOPPER`/`漏斗`、`CRAFTER`/`合成器`。

销毁原因别名：`all`、`fire`（含 `FIRE`/`FIRE_TICK`/`HOT_FLOOR`）、`lava`、`cactus`（含 `CONTACT`）、`lightning`、`explosion`（含方块/实体爆炸）、`void`（含 `OUT_OF_WORLD`）。

---

## async-tick — 异步定时触发策略

```yaml
meta:
  async-tick:
    enabled: true                  # 是否启用该物品的 on_async_tick（可选，默认 true）
    interval: 100                  # 触发间隔 tick；别名 ticks/period
    conditions:
      slots:
        - MAINHAND
        - OFFHAND
      sneaking: true
      sprinting: false
      world: world
      game-mode: SURVIVAL
      permission: baikiruto.async.fire
```

标量写法：`async-tick: false` 禁用；`async-tick: 20` 表示启用并设置 20 tick 间隔。

可用布尔条件：`sneaking/sneak`、`sprinting/sprint`、`swimming/swim`、`gliding/glide`、`flying/fly`、`on-ground/on_ground/onGround/ground`、`in-vehicle/in_vehicle/inVehicle/vehicle`、`burning/burn/on-fire/on_fire/fire`、`blocking/block`。

可用列表条件：
- `slot/slots`：`MAINHAND`、`OFFHAND`、`HEAD`、`CHEST`、`LEGS`、`FEET`、`HOTBAR`、`INVENTORY`、`ARMOR`、`EQUIPPED`、`ALL`
- `world/worlds`：世界名，小写匹配
- `game-mode/game-modes/gamemode/gamemodes`：`SURVIVAL`、`CREATIVE`、`ADVENTURE`、`SPECTATOR`
- `permission/permissions/perm/perms`：玩家拥有任意列出的权限即匹配

`on_async_tick` 的扫描总开关在 `config.yml` 的 `operations.async-tick.enabled`；默认间隔来自 `operations.async-tick.default-interval`（当前默认 100 tick）。调度会按玩家、槽位和物品 ID 生成稳定偏移，避免所有物品同 tick 集中触发。

---

## attribute — 属性修饰符

```yaml
meta:
  attribute:
    mainhand:                      # 槽位
      attack_damage: "8"           # 绝对值 → ADD_NUMBER
      attack_speed: "+15%"         # 百分比 → MULTIPLY_SCALAR_1（0.15）
    offhand:
      armor: "5"
    head:
      max_health: "10"
```

### 支持的槽位

| 配置写法 | 标准化 |
|----------|--------|
| `mainhand` / `main_hand` / `hand` | HAND |
| `offhand` / `off_hand` | OFF_HAND |
| `head` / `helmet` | HEAD |
| `chest` / `chestplate` | CHEST |
| `legs` / `leggings` | LEGS |
| `feet` / `boots` | FEET |

### 数值语法

- `"10"` — 绝对值，operation = ADD_NUMBER
- `"+15%"` / `"15%"` — 百分比，operation = MULTIPLY_SCALAR_1，amount = 0.15

### 常用属性名

`attack_damage`、`attack_speed`、`armor`、`armor_toughness`、`max_health`、`knockback_resistance`、`movement_speed`、`luck`

属性名自动标准化：转大写，`-` 转 `_`，自动补 `GENERIC_` 前缀。

---

## potion — 药水

```yaml
meta:
  potion:
    base: "speed"                  # 基础药水类型
    extended: false                # 是否延长版（可选）
    upgraded: true                 # 是否升级版（可选）
    color: "55AAFF"                # 自定义颜色 hex（可选）
    effects:                       # 自定义药水效果（可选）
      speed:
        duration: 200              # 持续时间（tick）
        amplifier: 1               # 等级（0 = I级）
        icon: true                 # 是否显示图标（可选）
      regeneration:
        duration: 100
        amplifier: 0
```

---

## skull — 头颅

```yaml
meta:
  skull:
    owner: "Notch"                 # 玩家名（四选一）
    # texture: "base64..."         # Base64 纹理
    # url: "http://textures..."    # 纹理 URL
    # hdb: "12345"                 # HeadDatabase ID（需安装 HeadDatabase 插件）
```

四种来源互斥，优先级：`owner` > `texture` > `url` > `hdb`。

---

## spawner — 刷怪笼

```yaml
meta:
  spawner:
    entity: "ZOMBIE"               # 实体类型
    delay: 40                      # 初始延迟（tick）
    min-delay: 200                 # 最小延迟
    max-delay: 800                 # 最大延迟
    spawn-count: 2                 # 每次生成数量
    max-nearby-entities: 8         # 最大附近实体数
    required-player-range: 16      # 激活范围
    spawn-range: 4                 # 生成范围
```

---

## native — 原生 NBT

```yaml
meta:
  native:
    baikiruto:                     # 任意嵌套 Map → 直接写入 NBT
      source: "custom-data"
      server: "1.21.11"
```

---

## enchantment — 附魔

```yaml
meta:
  enchantment:                     # 或 enchantments
    sharpness: 5
    unbreaking: 3
    fire_aspect: 2
```

key 为附魔名（不区分大小写），value 为等级（默认 1）。

---

## can-destroy / can-place-on — 冒险模式

```yaml
meta:
  can-destroy:
    - STONE
    - DEEPSLATE
  can-place-on:
    - DIRT
    - GRASS_BLOCK
```
