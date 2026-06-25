# Effects 与 Components

## effects 节点

`effects:` 下的所有 key-value 对直接写入 runtimeData，在 `toItemStack()` 阶段由版本适配器应用到 ItemStack。

```yaml
effects:
  glow: true
  item-flags:
    - HIDE_ENCHANTS
    - HIDE_ATTRIBUTES
  custom-model-data: 1211101
  item-model: "baikiruto:items/sword_12111"
  tooltip-style: "baikiruto:tooltip/default"
  rarity: "epic"
  unbreakable: true
  damage: 3
  glider: false
```

### 支持的 effect key

| Key | 类型 | 说明 |
|-----|------|------|
| `glow` | Boolean | 附魔光效 |
| `unbreakable` | Boolean | 不可破坏 |
| `damage` | Int | 当前损伤值 |
| `custom-model-data` | Int | CustomModelData 值 |
| `item-model` | String | 1.21.4+ ItemModel 命名空间 |
| `tooltip-style` | String | 1.21.4+ Tooltip 样式命名空间 |
| `rarity` | String | 稀有度（common/uncommon/rare/epic） |
| `glider` | Boolean | 滑翔组件（1.21.2+） |
| `item-flags` | List\<String\> | ItemFlag 列表 |
| `enchantments` | Map\<String, Int\> | 附魔（key=附魔名, value=等级） |

### ItemFlag 可用值

`HIDE_ENCHANTS`、`HIDE_ATTRIBUTES`、`HIDE_UNBREAKABLE`、`HIDE_DESTROYS`、`HIDE_PLACED_ON`、`HIDE_POTION_EFFECTS`、`HIDE_DYE`、`HIDE_ARMOR_TRIM`

### 注意

`effects:` 是透传节点——任何 key-value 都会写入 runtimeData。上表列出的是有内置处理逻辑的 key，你也可以写入自定义 key 供脚本或 data-mapper 使用。

---

## components 节点（1.21+ Data Components）

`components:` 提供 Vanilla Data Component 风格的配置入口，解析后映射为 effects/runtimeData。

```yaml
components:
  custom_name: "&6Fire Sword"
  lore:
    - "&7A legendary weapon"
  item_model: "baikiruto:items/fire_sword"
  custom_model_data: 1211101
  enchantments:
    levels:
      sharpness: 5
      unbreaking: 3
    show_in_tooltip: false
  attribute_modifiers:
    modifiers:
      - type: "attack_damage"
        amount: 8.0
        operation: "add_value"
        slot: "mainhand"
      - type: "attack_speed"
        amount: 0.15
        operation: "add_multiplied_total"
        slot: "mainhand"
  unbreakable: true
  damage: 3
  max_damage: 240
  tooltip_style: "baikiruto:tooltip/default"
  rarity: "epic"
  glider: true
  can_break:
    blocks:
      - STONE
      - DEEPSLATE
  can_place_on:
    blocks:
      - DIRT
      - GRASS_BLOCK
  use_cooldown:
    seconds: 4.0
    cooldown_group: "baikiruto:fire_sword"
    apply_on_cancelled_triggers:
      - on_shoot
      - right_click
  use_remainder:
    id: "namespace:item_id"
    amount: 1
  equippable:
    slot: "mainhand"
    equip_sound: "entity.player.levelup"
    model: "baikiruto:items/fire_sword_equipped"
  damage_resistant:
    enabled: true
    types:
      - "projectile"
      - "fire"
  death_protection:
    enabled: true
    health: 4.0
    consume: false
    types:
      - "void"
  potion_contents:
    potion: "strong_swiftness"
    custom_color: "55AAFF"
    custom_effects:
      - id: "speed"
        duration: 300
        amplifier: 1
        ambient: false
        show_particles: true
        show_icon: true
  custom_data:
    baikiruto:
      item_id: "namespace:fire_sword"
      server: "1.21.11"
```

### Component 完整映射表

| Component Key | 映射到的 Effect Key | 值格式 |
|---------------|---------------------|--------|
| `custom_name` / `name` | 仅保留到 `components.custom_name` | String / Map / JSON 文本组件 |
| `lore` | 仅保留到 `components.lore` | List\<String\> / Map{lines} |
| `item_model` | `item-model` | String |
| `custom_model_data` | `custom-model-data` | Int / Map{value} / Map{floats[0]} |
| `enchantments` | `enchantments` + `item-flags` | Map{levels: {name: level}, show_in_tooltip} |
| `attribute_modifiers` | `attributes` | Map{modifiers: [{type, amount, operation, slot}]} |
| `unbreakable` | `unbreakable` | Boolean / Map{show_in_tooltip} |
| `damage` | `damage` | Int |
| `max_damage` | `durability` | Int（映射到耐久系统） |
| `can_break` | `can-destroy` | Map{blocks: [String]} |
| `can_place_on` | `can-place-on` | Map{blocks: [String]} |
| `tooltip_style` | `tooltip-style` | String |
| `rarity` | `rarity` | String |
| `glider` | `glider` | Boolean |
| `use_cooldown` | `cooldown` (ticks) + `use-cooldown-seconds` + `use-cooldown-group` + `cooldown-apply-on-cancelled-triggers` | Map{seconds, cooldown_group, apply_on_cancelled_triggers} |
| `use_remainder` | `use-remainder` (id) + `use-remainder-amount` | String 或 Map{id, amount} |
| `equippable` | `equippable-slot` + `equippable-equip-sound` + `equippable-model` | Map{slot, equip_sound, model} |
| `damage_resistant` | `damage-resistant-enabled` + `damage-resistant-types` | Boolean 或 Map{enabled, types} |
| `death_protection` | `death-protection-enabled/health/consume/types` | Boolean 或 Map |
| `potion_contents` | `potion-color` + `potion-base-type` + `potion-effects` | Map{potion, custom_color, custom_effects} |
| `custom_data` | 直接展开为顶层 effect key | Map（每个子 key 独立写入） |

### attribute_modifiers operation 值

| 配置值 | 含义 |
|--------|------|
| `add_value` / `add_number` | 绝对值加成 |
| `add_multiplied_base` / `add_scalar` | 基础值百分比加成 |
| `add_multiplied_total` / `multiply_scalar_1` | 最终值百分比加成 |

### 是否保留原版默认属性

无论通过 `components.attribute_modifiers` 还是顶层 `meta.attribute` 配置属性，默认都会**保留原版默认属性**（如盔甲护甲值、武器基础攻速），仅在其上叠加自定义属性。如需清除原版默认属性，使用顶层 `meta.attribute.preserve-default-attributes: false`（或 `attributes-replace-mode: true`），详见 meta-reference.md 的 attribute 小节。

### 文本组件格式

`custom_name` 和 `lore` 支持三种输入格式：

```yaml
# 1. 纯字符串
custom_name: "&6Fire Sword"

# 2. Map 格式
custom_name:
  text: "Fire Sword"
  color: "gold"
  bold: true

# 3. JSON 文本组件
custom_name: '{"text":"Fire Sword","color":"gold","bold":true}'
```

Map 格式支持的字段：`text`/`item_name`/`value`/`legacy`（文本）、`color`（颜色名或 #hex）、`bold`/`italic`/`obfuscated`/`strikethrough`/`underlined`（样式）。

注意：`components.custom_name` 与 `components.lore` 当前只作为高版本 Data Components 写入，不会回填覆盖 Baikiruto 的 legacy `name` / `lore` 分段和 Display 模板。需要跨版本显示文本时仍应使用顶层 `name` / `lore` 或 `display`。

### components 与 meta/effects 的关系

`components` 是 1.21+ 风格的配置入口，最终映射为与 `meta`/`effects` 相同的 runtimeData key。三者可以混用，合并优先级：`effects` < `meta` < `components`（后者覆盖前者）。
