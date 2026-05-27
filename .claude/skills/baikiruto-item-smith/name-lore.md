# Name / Lore 系统

## Name 节点

### 分段 key 格式（推荐）

```yaml
name:
  item_name: "&6Fire Sword"
  item_subtitle: "&7Legendary"
```

每个 key 是一个"分段"，在 Display 模板中通过 `<key>` 引用。

### 直接字符串格式

```yaml
name: "&6Fire Sword"
```

自动映射为 `{ item_name: "&6Fire Sword" }`。

### 别名

`display-name` 等价于 `name`：
```yaml
display-name: "&6Fire Sword"
```

---

## Lore 节点

### 分段 key 格式（推荐）

```yaml
lore:
  item_type: "&7Legendary Weapon"
  item_description:
    - "&7A sword forged in fire."
    - "&7Durability: {durability_current}/{durability_max}"
  item_footer: "&8Right-click to activate"
```

- 单行值：`key: "text"` → 单行分段
- 多行值：`key: [...]` → 列表分段
- 排序规则：按 key 的字典序排列

### 直接列表格式

```yaml
lore:
  - "&7Line 1"
  - "&7Line 2"
```

自动映射为 `{ item_description: ["&7Line 1", "&7Line 2"] }`。

### 自动换行

```yaml
lore:
  ~autowrap: 40
  item_description:
    - "&7This is a very long description that will be automatically wrapped at 40 characters with color code inheritance."
```

`~autowrap` 设置自动换行字符数，超过该宽度自动断行并继承前一行的颜色代码。

---

## Display 模板

Display 模板定义 name/lore 的最终渲染格式，使用 `<xxx>` 占位符引用物品的分段 key。

### 定义

```yaml
displays:
  "weapons/default":
    name: "&7<item_name>"
    lore:
      - "&9<item_type>"
      - "&f<item_description...>"
      - ""
      - "&7<item_footer>"
```

### 占位符

| 语法 | 说明 | 示例 |
|------|------|------|
| `<xxx>` | 标量替换 | `<item_name>` → 取 name 分段中 `item_name` 的值 |
| `<xxx...>` | 列表展开 | `<item_description...>` → 将列表逐行展开 |

### 渲染流程

1. 物品的 name/lore 分段提供变量值
2. Display 模板中的 `<xxx>` 被替换为对应值
3. `<xxx...>` 展开为多行（列表有几项就生成几行）
4. 找不到的占位符替换为空字符串

### 示例

物品配置：
```yaml
name:
  item_name: "&6Fire Sword"
lore:
  item_type: "&7Legendary Weapon"
  item_description:
    - "&7Burns enemies on hit"
    - "&7+60 fire ticks"
```

Display 模板：
```yaml
name: "&f<item_name>"
lore:
  - "&9<item_type>"
  - "&f<item_description...>"
```

渲染结果：
```
Name: §fFire Sword
Lore:
  §9Legendary Weapon
  §fBurns enemies on hit
  §f+60 fire ticks
```

---

## 运行时占位符

在 name/lore 的最终文本中，`{xxx}` 和 `%xxx%` 会在运行时被替换为 runtimeData 中的值。

```yaml
lore:
  item_description:
    - "&7Durability: {durability_current}/{durability_max}"
    - "&7Owner: {unique.player}"
    - "&7Status: {fire_status}"
```

- `{key}` — 从 runtimeData 中取值（支持嵌套路径如 `{unique.player}`）
- `%key%` — PlaceholderAPI 兼容格式
- 值为 null 时替换为空字符串

---

## data-mapper

`data-mapper` 用 Fluxon 脚本将运行时数据动态转换为显示文本，在 `toItemStack()` 阶段执行。

```yaml
data-mapper:
  durability_line: |
    current = &data["durability_current"] ?: &data["durability"] ?: 0
    max = &data["durability"] ?: 0
    return &current :: toString() + "/" + &max :: toString()
  fire_status: |
    cd = &ops.cooldown()
    return &cd > 0 ? "&cCooling" : "&aReady"
```

- 键名对应 lore 中的 `{占位符}`
- 脚本可用变量：`&data`（runtimeData map）、`&stream`（ItemStream）、`&itemId`、`&it`（当前 key 的旧值）
- 脚本返回值写回 runtimeData，然后被 `{xxx}` 占位符引用

---

## Data 节点

```yaml
data:
  category: "weapon"
  tier: "legendary"
  durability: 240
  charge: 3
  custom_flag: true
```

- 所有 key-value 对存入 runtimeData
- 脚本中通过 `&ops.data("key")` 读取、`&ops.setData("key", value)` 写入
- 运行时数据持久化到 NBT 的 `baikiruto.data` compound 中
- 支持嵌套 Map：`data: { stats: { attack: 10, defense: 5 } }`

---

## 颜色代码

使用 `&` 前缀的 Minecraft 颜色代码：

| 代码 | 颜色 | 代码 | 颜色 |
|------|------|------|------|
| `&0` | 黑色 | `&8` | 深灰 |
| `&1` | 深蓝 | `&9` | 蓝色 |
| `&2` | 深绿 | `&a` | 绿色 |
| `&3` | 深青 | `&b` | 青色 |
| `&4` | 深红 | `&c` | 红色 |
| `&5` | 紫色 | `&d` | 粉色 |
| `&6` | 金色 | `&e` | 黄色 |
| `&7` | 灰色 | `&f` | 白色 |

格式代码：`&k` 混淆、`&l` 粗体、`&m` 删除线、`&n` 下划线、`&o` 斜体、`&r` 重置

Hex 颜色：`&x&a&b&c&d&e&f` 格式（`#ABCDEF`）
