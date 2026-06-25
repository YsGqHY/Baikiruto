# 高级特性

## i18n 国际化

物品的 name、lore、scripts、event 均支持按玩家语言环境提供不同版本。

```yaml
i18n:
  en_us:
    name:
      item_name: "&6Fire Sword"
    lore:
      item_description:
        - "&7Burns enemies on hit"
    scripts:
      build: |
        &player?.sendMessage("&aBuilding (English)")
        return item
    event:
      on_right_click: |
        &player?.sendMessage("&aRight clicked!")
        return item
  zh_cn:
    name:
      item_name: "&6火焰之剑"
    lore:
      item_description:
        - "&7攻击时点燃敌人"
    scripts:
      build: |
        &player?.sendMessage("&a正在构建物品")
        return item
    event:
      on_right_click: |
        &player?.sendMessage("&a右键点击！")
        return item
```

### 覆盖规则

- `name` → 覆盖显示名称的分段 key
- `lore` → 覆盖描述的分段 key
- `scripts` → 覆盖生命周期脚本（build/drop/release 等）
- `event` → 覆盖事件脚本（on_use/on_attack/on_sneak/on_death 等，完整列表见 config-structure.md）

### locale 解析

- 自动标准化：`-` 转 `_`，全小写（`en-US` → `en_us`）
- 回退链：精确匹配 `en_us` → 语言前缀 `en` → 默认脚本（无 i18n）
- 来源：`player.locale` 或 context 中的 `locale` 字段

i18n 脚本同样支持 `!!` 取消事件语法，以及事件触发器的 `@priority` 优先级后缀（与主 `event` 节点一致，解析逻辑相同）。

---

## `!!` 锁定机制

在配置 key 后追加 `!!` 后缀，可锁定该字段，使其在运行时不可被脚本或事件修改。

### Display 字段锁定

```yaml
# 锁定名称 — 脚本无法修改 displayName
name!!:
  item_name: "&6Immutable Sword"

# 锁定描述 — 脚本无法修改 lore
lore!!:
  item_description:
    - "&7This lore cannot be changed"

# 锁定材质 — 脚本无法修改 material
icon!!: "NETHERITE_SWORD"
```

支持的 display 锁定字段：
- `name!!` / `display-name!!` → 锁定 `name`
- `lore!!` → 锁定 `lore`
- `material!!` / `icon!!` / `type!!` → 锁定 `icon`（材质）

锁定后的行为：
- `setDisplayName()` / `setLore()` 调用被静默忽略
- 脚本修改 ItemStack 后，被锁定的字段自动恢复为原始值
- 锁定值通过签名机制持久化到 NBT，rebuild 后仍然有效

### Data 路径锁定

```yaml
data:
  category!!: "weapon"             # 锁定 "category" 路径
  stats!!:                         # 锁定 "stats" 及其所有子路径
    attack: 10
    defense: 5
```

- key 以 `!!` 结尾 → 该路径被锁定
- 嵌套 key 也支持：`stats!!` 锁定 `stats`、`stats.attack`、`stats.defense` 等所有子路径
- 脚本中 `&ops.setData("category", ...)` 会被静默忽略（返回 false）
- `!!` 后缀在解析时自动去除，不影响实际 key 名

---

## version-hash 版本控制

`version-hash` 标识物品配置的版本，用于检测已有物品是否需要更新。

```yaml
items:
  "weapons:sword":
    version-hash: "v2"             # 手动指定版本
```

### 手动指定 vs 自动生成

- 手动指定：`version-hash: "任意字符串"`，修改配置后手动更新此值
- 自动生成：不填 `version-hash`，系统对以下数据计算 SHA-1 哈希：
  - itemId、完整 item 配置、modelIds、models 数据、displayId、display 数据、template 序列化、runtimeData
  - 所有 Map 按 key 排序后序列化，确保确定性

### 物品更新机制

1. 玩家加入/重生/切换世界时，扫描背包中的物品
2. 读取 ItemStack NBT 中的 `baikiruto.version`
3. 与当前配置的 version-hash 比较
4. 不一致时触发 `ItemCheckUpdateEvent`（可取消）
5. 调用 `stream.rebuild(player)` 重建物品（保留运行时数据）
6. 替换背包中的 ItemStack

### NBT 结构

```
ItemStack NBT:
└── baikiruto (Compound)
    ├── id (String)           — 物品 ID
    ├── version (String)      — 版本哈希
    ├── meta_history (List)   — Meta 应用历史
    └── data (Compound)       — 运行时数据
```

---

## metas — 自定义脚本 Meta

`metas:` 节点（别名 `meta-scripts`）定义自定义脚本 Meta，每个 Meta 拥有独立的生命周期脚本。

```yaml
metas:
  fire_enchant:
    scripts:
      build: |
        &ops.setData("fire_enchant_active", true)
        return item
      drop: |
        &ops.setData("fire_enchant_active", false)
        return item
    event:
      on_attack: |
        &event.getEntity().setFireTicks(60)
        return item

  lifesteal:
    type: "script"                 # 可选：MetaFactory 类型
    scripts:
      build: |
        &ops.setData("lifesteal_percent", 10)
        return item
```

### 可用字段

| 字段 | 说明 |
|------|------|
| `type` / `factory` / `meta_factory` / `meta-factory` | MetaFactory ID（可选，默认 "script"） |
| `scripts` | 生命周期脚本（build/drop/release 等） |
| `event` | 事件脚本（完整触发器列表见 config-structure.md） |
| `i18n` | 本地化脚本覆盖 |

### 执行顺序

- `build` 阶段：先执行物品自身脚本，再按 metas 定义顺序依次执行各 Meta 脚本
- `drop` 阶段：先按 metas **逆序**依次执行各 Meta 的 `drop()` 与 DROP 脚本，再执行物品自身 DROP 脚本
- 事件触发：先执行物品自身脚本，再按 metas 定义顺序依次执行各 Meta 脚本

### Meta 脚本的 `&itemId`

Meta 脚本中 `&itemId` 格式为 `"物品ID:meta:Meta名"`（如 `"weapons:sword:meta:fire_enchant"`）。

---

## 物品构建流程

理解构建流程有助于正确配置物品。

### build 阶段（`Item.build(context)`）

```
1. 合并 context（defaultRuntimeData + eventData + 传入 context）
2. 创建 ItemStream（初始化 runtimeData，并记住本次调用上下文）
3. 发布 ItemBuildPreEvent
4. 执行 BUILD 脚本
5. 遍历 metas（正序）→ meta.build() → 执行 meta BUILD 脚本
6. 发布 ItemBuildPostEvent
7. 返回 ItemStream
```

### toItemStack 阶段（`ItemStream.toItemStack()`）

```
1. UniqueFeature.prepare() — 生成 UUID/绑定玩家
2. DataMapperFeature.apply() — 执行 data-mapper 脚本（可用 `data`、`stream`、`itemId`、`it` 和上次调用上下文）
3. 发布 ItemReleaseEvent
4. 执行 RELEASE 脚本
5. 选择并应用 Display 模板（<xxx> 占位符替换）
6. 应用 i18n 覆盖
7. 执行 RELEASE_DISPLAY 脚本
8. DurabilityFeature.prepare() — 计算耐久条
9. CooldownFeature.injectDisplayData() — 注入冷却数据
10. 替换运行时占位符（{xxx}）
11. PlaceholderAPI 替换
12. 写入 NBT
13. 应用版本特定效果（enchantments/attributes/skull/spawner 等）
14. NativeFeature.apply() — 写入自定义 NBT
15. 返回 ItemStack
```

### 合并优先级（从低到高）

```
Model defaults → Display → Item 配置 → Components → Data → Effects → Meta → i18n
```

后者覆盖前者。
