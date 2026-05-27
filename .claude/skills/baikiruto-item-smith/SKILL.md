---
name: Baikiruto Item Smith
description: >
  在 Baikiruto 插件中编写物品 YAML 配置的完整自包含知识库。涵盖物品配置结构（group/model/display/item）、
  name/lore 分段系统与占位符、data 数据系统、effects 效果、meta 内置元数据（durability/cooldown/unique/
  drop/protection/async-tick/attribute/potion/skull/spawner）、metas 自定义脚本 Meta、components 1.21+ 数据组件、
  i18n 国际化、!! 锁定机制、version-hash 版本控制、冷却取消策略、30 种事件触发器（含 death/kill/hurt/
  sneak/sprint/jump/respawn/equip/unequip/shoot/projectile_hit/async_tick 等触发器）。无需依赖其他 Skill 即可编写正确的物品配置。
globs:
  - "**/items/**/*.yml"
  - "**/items/**/*.yaml"
  - "**/display/**/*.yml"
  - "**/display/**/*.yaml"
---

# Baikiruto Item Smith

你是 Baikiruto 物品配置专家。当用户需要编写物品 YAML 配置时，严格遵循以下规则。

## 激活条件

- 用户要求创建/编辑 Baikiruto 物品配置
- 用户要求配置物品的 name/lore/data/effects/meta/components
- 工作区中存在 `items/**/*.yml` 或 `display/**/*.yml` 文件

## 核心行为

1. 生成的配置必须放在 `items/` 目录下的 `.yml` 文件中
2. Display 模板可放在 `display/` 目录或 `items/` 文件的 `displays:` 节点
3. 物品 ID 格式为 `"namespace:name"`（YAML key 即 ID）
4. 必填字段只有 YAML key 本身，其余均可选（material 默认 STONE）
5. 脚本内容遵循 Fluxon 语法（参考 `baikiruto-fluxon-smith` Skill）
6. 配置中的颜色代码使用 `&` 前缀（如 `&6` 金色、`&7` 灰色）

## 知识文件

- `config-structure.md` -- 文件组织、__group__、models、displays、items 完整字段
- `name-lore.md` -- name/lore 分段系统、Display 模板、占位符、data-mapper
- `meta-reference.md` -- 内置 Meta 类型（durability/cooldown/unique/drop/protection/async-tick/attribute/potion/skull/spawner）
- `effects-components.md` -- effects 效果、components 1.21+ 数据组件
- `advanced.md` -- i18n 国际化、!! 锁定机制、version-hash、metas 自定义 Meta

## 快速示例

```yaml
__group__:
  id: "weapons"
  priority: 100

items:
  "weapons:fire_sword":
    icon: "NETHERITE_SWORD"
    version-hash: "v1"
    name:
      item_name: "&6Fire Sword"
    lore:
      item_type: "&7Legendary Weapon"
      item_description:
        - "&7Durability: {durability_current}/{durability_max} {durability_bar}"
        - "&7Cooldown: {cooldown_remaining} ticks ({cooldown_remaining_seconds}s)"
    data:
      category: "weapon"
      tier: "legendary"
      durability: 240
    effects:
      glow: true
      item-flags:
        - HIDE_ENCHANTS
    meta:
      durability:
        synchronous: true
        bar-length: 12
        bar-symbol:
          - "&a|"
          - "&7|"
      cooldown:
        ticks: 80
        by-player: true
        apply-on-cancelled-triggers:
          - on_right_click
          - on_shoot
      async-tick:
        enabled: true
        interval: 100
        conditions:
          slots:
            - MAINHAND
          sneaking: true
      drop:
        display-name: "&6Fire Sword"
        display-visible: true
      protection:
        crafting:
          stations:
            - ANVIL
        containers:
          deny:
            - HOPPER
        destroy:
          enabled: true
          causes:
            - fire
      attribute:
        mainhand:
          attack_damage: "8"
          attack_speed: "+15%"
    scripts:
      build: |
        return item
    event:
      on_attack: |
        &ops.damage(1)
        &event.getEntity().setFireTicks(60)
        return item
      on_right_click!!: |
        if (&ops.cooldown() > 0) {
            &player?.sendMessage("&cCooldown!")
            return item
        }
        &ops.setCooldown(80)
        &player?.sendMessage("&aFire Blast!")
        return item
      on_hurt: |
        &ops.setData("hurt_slot", &ctx["slot"])
        return item
      on_sneak: |
        &player?.sendMessage("&7Sneaking with fire sword...")
        return item
      on_kill: |
        &player?.sendMessage("&aTarget eliminated!")
        return item
```
