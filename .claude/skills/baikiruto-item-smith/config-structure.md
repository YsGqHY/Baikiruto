# 配置文件结构

## 文件组织

物品配置放在 `items/` 目录下，Display 模板放在 `display/` 目录下，均支持子目录递归扫描。

### 文件格式

一个 `.yml` 文件支持三种组织方式：

**方式 1：`items:` 节点下多物品（推荐）**
```yaml
__group__:
  id: "weapons"

models:
  "weapons/base": { ... }

displays:
  "weapons/default": { ... }

items:
  "weapons:sword": { ... }
  "weapons:axe": { ... }
```

**方式 2：单物品文件**
```yaml
# 文件自动检测为单物品（含 id/material/name/lore/scripts/effects 等字段）
id: "weapons:dagger"
icon: "IRON_SWORD"
name: "&7Dagger"
```

**方式 3：顶层多 key**
```yaml
# 排除 __group__/models/displays/display 和以 $ 结尾的 key
"weapons:sword":
  icon: "DIAMOND_SWORD"
"weapons:axe":
  icon: "DIAMOND_AXE"
```

---

## `__group__` 节点

定义物品文件的分组元数据。

```yaml
__group__:
  id: "weapons"                    # 分组 ID（可选，默认=文件相对路径小写）
  path: "weapons"                  # 显示路径（可选，默认=文件相对路径不含扩展名）
  priority: 100                    # 排序优先级（可选，默认 0，越大越靠前）
  icon: "weapons:fire_sword"       # 分组图标物品 ID（可选）
```

---

## `models` 节点

Model 定义物品的基础属性模板，支持继承。

```yaml
models:
  "weapons/base":
    material: "NETHERITE_SWORD"
    display: "weapons/default"
    data:
      category: "weapon"
      server_target: "1.21.11"
    effects:
      glow: true

  "weapons/1_21_11":
    from:                          # 继承（支持列表或逗号分隔字符串）
      - "weapons/base"
    effects:
      item-model: "baikiruto:items/sword_12111"
      custom-model-data: 1211101
```

也支持以 `$` 结尾的顶层 key 作为 Model（`$` 会被去掉作为 ID）：
```yaml
"weapons/base$":
  material: "NETHERITE_SWORD"
```

### 继承合并策略

- 按 `from` 依赖顺序合并（被依赖的先加载），检测循环引用
- 普通字段：后者覆盖前者（浅合并）
- `__locked_data_paths__` / `__locked_display_fields__`：累加合并
- `components`：key 级别合并，`custom_data` 做深度合并

### Model 可用字段

| 字段 | 说明 |
|------|------|
| `material` / `icon` / `type` | 材质 |
| `display` | 引用 Display ID |
| `from` | 继承的 Model ID 列表 |
| `data` | 静态数据 |
| `effects` | 效果 |
| `meta` | 内置 Meta |
| `components` | 1.21+ 组件 |
| `name` / `lore` | 名称/描述 |
| `scripts` / `event` | 脚本 |
| `i18n` | 国际化 |

---

## `displays` 节点

Display 定义名称和描述的渲染模板，使用 `<xxx>` 占位符引用物品的 name/lore 分段。

```yaml
displays:
  "weapons/default":
    name: "&7<item_name>"
    lore:
      - "&9<item_type>"
      - "&f<item_description...>"
      - ""
      - "&a+<damage> Damage"
```

也可以放在 `display/` 目录下的独立文件中：
```yaml
# display/weapons.yml
"weapons/default":
  name: "&7<item_name>"
  lore:
    - "&9<item_type>"
    - "&f<item_description...>"
```

### 占位符语法

| 语法 | 说明 |
|------|------|
| `<xxx>` | 标量替换，从 name/lore 分段或 runtimeData 中取值 |
| `<xxx...>` | 列表展开，将多行列表逐行展开（用于 lore 中的列表分段） |

---

## `items` 节点 — 物品完整字段

```yaml
items:
  "namespace:item_name":
    # === 基础 ===
    icon: "NETHERITE_SWORD"        # 材质（别名：material/type），默认 STONE
    version-hash: "v1"             # 版本哈希（可选，不填自动 SHA-1）
    model: "weapons/1_21_11"       # 引用 Model ID（别名：from，支持列表）
    display: "weapons/default"     # 引用 Display ID

    # === 显示 ===
    name: { ... }                  # 名称（分段 key 或直接字符串）
    lore: { ... }                  # 描述（分段 key 或直接列表）

    # === 数据 ===
    data: { ... }                  # 静态数据
    data-mapper: { ... }           # 动态数据映射脚本

    # === 效果 ===
    effects: { ... }               # 视觉/功能效果

    # === Meta ===
    meta: { ... }                  # 内置 Meta（durability/cooldown/unique 等）
    metas: { ... }                 # 自定义脚本 Meta（别名：meta-scripts）

    # === 脚本 ===
    scripts: { ... }               # 生命周期脚本（build/release/drop）
    event: { ... }                 # 事件脚本（on_use/on_attack 等）

    # === 高级 ===
    components: { ... }            # 1.21+ Data Components
    i18n: { ... }                  # 国际化覆盖
```

所有字段均可选，只有 YAML key 本身是必须的。

---

## `scripts` 节点 — 生命周期脚本

物品构建/释放/丢弃阶段执行的脚本。

```yaml
scripts:
  build: |
    # Item.build() 阶段执行
    return item
  release: |
    # toItemStack() 阶段执行
    return item
  release_display: |
    # Display 模板应用后执行
    return item
  drop: |
    # Meta drop 阶段执行（逆序）
    return item
```

---

## `event` 节点 — 事件触发器脚本

事件触发器在玩家与物品交互时执行。key 后追加 `!!` 可同时取消 Bukkit 事件。

### 完整触发器列表

#### 交互类（检测主手物品）

| YAML key | 触发时机 | 说明 |
|----------|---------|------|
| `on_use` | 右键使用物品 | PlayerInteractEvent (RIGHT_CLICK_*) |
| `on_interact` | 任意交互 | PlayerInteractEvent (所有 Action) |
| `on_left_click` | 左键点击 | PlayerInteractEvent (LEFT_CLICK_*) |
| `on_right_click` | 右键点击 | PlayerInteractEvent (RIGHT_CLICK_*) |
| `on_right_click_entity` | 右键点击实体 | PlayerInteractEntityEvent |
| `on_attack` | 攻击实体 | EntityDamageByEntityEvent（别名 `on_sword`） |
| `on_block_break` | 破坏方块 | BlockBreakEvent |
| `on_consume` | 消耗物品 | PlayerItemConsumeEvent |
| `on_drop` | 丢弃物品 | PlayerDropItemEvent |
| `on_pickup` | 拾取物品 | EntityPickupItemEvent（别名 `on_pick`） |
| `on_swap_to_mainhand` | 从副手换到主手 | PlayerSwapHandItemsEvent |
| `on_swap_to_offhand` | 从主手换到副手 | PlayerSwapHandItemsEvent |
| `on_select` | 切换到该物品槽位 | PlayerItemHeldEvent |
| `on_inventory_click` | 背包中点击该物品 | InventoryClickEvent（别名 `on_click`） |

#### 耐久/破损类（检测主手物品）

| YAML key | 触发时机 | 说明 |
|----------|---------|------|
| `on_damage` | 物品受到耐久损伤 | PlayerItemDamageEvent |
| `on_item_break` | 物品耐久归零损坏 | PlayerItemBreakEvent |

#### 战斗类

| YAML key | 触发时机 | 检测范围 | 说明 |
|----------|---------|---------|------|
| `on_kill` | 击杀实体 | 主手 | EntityDeathEvent（killer 为玩家） |
| `on_hurt` | 玩家受到伤害 | 全身装备+手持 | EntityDamageEvent，context 含 `slot` |
| `on_death` | 玩家死亡 | 全身装备+手持 | PlayerDeathEvent，context 含 `slot` |
| `on_shoot` | 发射弹射物 | 主手 | ProjectileLaunchEvent |
| `on_projectile_hit` | 弹射物命中 | 主手 | ProjectileHitEvent |

#### 移动/状态类（全身装备+手持，context 含 `slot`）

| YAML key | 触发时机 | 说明 |
|----------|---------|------|
| `on_sneak` | 切换潜行状态 | PlayerToggleSneakEvent |
| `on_sprint` | 切换疾跑状态 | PlayerToggleSprintEvent |
| `on_jump` | 跳跃 | PlayerMoveEvent（Y 轴上升检测） |
| `on_respawn` | 复活 | PlayerRespawnEvent |

#### 装备类（检测装备槽操作，context 含 `slot`）

| YAML key | 触发时机 | 说明 |
|----------|---------|------|
| `on_equip` | 穿戴装备 | InventoryClickEvent（装备槽操作） |
| `on_unequip` | 脱下装备 | InventoryClickEvent（装备槽操作） |

#### 定时类（全身装备+手持）

| YAML key | 触发时机 | 说明 |
|----------|---------|------|
| `on_async_tick` | 异步定时触发 | 由 `config.yml` 的 `operations.async-tick` 和物品 `meta.async-tick` 控制，默认 100 tick 周期（别名 `on_tick`） |

### 检测范围说明

- **主手**: 仅检测玩家主手持有的物品
- **全身装备+手持**: 遍历主手、副手、头盔、胸甲、护腿、靴子共 6 个槽位，对每个 Baikiruto 物品分别触发脚本，context 中注入 `slot` 变量
- **Async Tick**: 遍历玩家背包 contents，context 中注入 `slot` 与 `slot_index`；slot 可能是 `MAINHAND`、`HOTBAR`、`INVENTORY`、`FEET`、`LEGS`、`CHEST`、`HEAD`、`OFFHAND`

### `slot` 变量值

全身装备触发器的 context 中 `&ctx["slot"]` 可能的值：
- `MAINHAND` / `OFFHAND` / `HEAD` / `CHEST` / `LEGS` / `FEET`

`on_async_tick` 额外注入 `&ctx["slot_index"]`（背包 contents 下标），并可能使用 `HOTBAR` / `INVENTORY` 等聚合槽位。

### 示例

```yaml
event:
  # 右键使用，同时取消原版交互
  on_right_click!!: |
    &ops.setCooldown(80)
    &ops.setData("last_trigger", "right_click")
    return item

  # 攻击时扣耐久并点燃目标
  on_attack: |
    &ops.damage(1)
    &event.getEntity().setFireTicks(60)
    return item

  # 玩家受伤时记录槽位
  on_hurt: |
    &ops.setData("last_trigger", "hurt")
    &ops.setData("hurt_slot", &ctx["slot"])
    return item

  # 潜行时触发效果
  on_sneak: |
    &ops.setData("last_trigger", "sneak")
    return item

  # 穿戴装备时记录
  on_equip: |
    &ops.setData("equipped_slot", &ctx["slot"])
    return item

  # 击杀实体时
      on_kill: |
        &player?.sendMessage("&aTarget eliminated!")
        return item

  # 按物品配置 async tick 条件：仅主手且潜行时触发
  "weapons:passive_ring":
    icon: "EMERALD"
    meta:
      async-tick:
        interval: 40
        conditions:
          slots: MAINHAND
          sneaking: true
    event:
      on_async_tick: |
        &ops.setData("tick_slot", &ctx["slot"])
        &ops.setData("tick_index", &ctx["slot_index"])
        return item
```
