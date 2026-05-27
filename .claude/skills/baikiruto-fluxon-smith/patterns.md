# 常见脚本模式与最佳实践

## 编写规范

### 必须遵守

1. **变量引用用 `&`**：`&player`、`&ops`、`&event`，裸标识符是字符串
2. **生命周期脚本末尾 `return item`**：保持物品流正常运转
3. **优先用 `&ops`**：不要直接操作 `&stream`，除非 `&ops` 无法满足需求
4. **空安全**：`&player` 和 `&sender` 可能为 null，用 `?.` 或 `&?player`
5. **冷却检查在前**：先检查冷却再执行逻辑，避免重复触发

### 建议遵守

1. 复杂逻辑拆分为多行，避免单行过长
2. 用 `&ops.setData()` 记录状态，而非依赖外部变量
3. 事件脚本中通过 `&event` 获取事件数据，不要硬编码；`on_async_tick` 的 `&event` 为 null
4. 使用 `!!` 取消语法代替手动 `&event.setCancelled(true)`
5. 使用自动冷却时，如果脚本取消事件但仍要进入冷却，在 `meta.cooldown.apply-on-cancelled-triggers` 或 `components.use_cooldown.apply_on_cancelled_triggers` 中声明触发器

---

## 模式 1：冷却技能

右键释放技能，带冷却检查和冷却提示。

```yaml
event:
  on_right_click!!: |
    if (&ops.cooldown() > 0) {
        remaining = &ops.cooldown() / 20
        &player?.sendMessage("&cCooldown: " + &remaining + "s")
        return item
    }
    &ops.setCooldown(100)
    &player?.sendMessage("&aSkill activated!")
    &ops.setData("last_skill", "fireball")
    return item
```

---

## 模式 2：攻击附加效果

攻击时对目标施加药水效果和着火。

```yaml
event:
  on_attack: |
    target = &event.getEntity()
    &target.setFireTicks(60)
    potionClass = forName("org.bukkit.potion.PotionEffect")
    typeClass = forName("org.bukkit.potion.PotionEffectType")
    slowness = static org.bukkit.potion.PotionEffectType.SLOW
    effect = new org.bukkit.potion.PotionEffect(&slowness, 100, 1)
    &target.addPotionEffect(&effect)
    &ops.damage(1)
    &ops.setData("last_hit", &target.getName())
    return item
```

---

## 模式 3：耐久度管理

攻击扣耐久，损坏时通知玩家。

```yaml
event:
  on_attack: |
    destroyed = &ops.damage(1)
    if (&destroyed) {
        &player?.sendMessage("&cYour weapon has shattered!")
        return item
    }
    current = &ops.durability()
    max = &ops.durabilityMax()
    if (&current < &max * 0.2) {
        &player?.sendMessage("&eWarning: weapon durability low!")
    }
    return item
```

配合 `data-mapper` 显示耐久条：

```yaml
lore:
  item_description:
    - "&7Durability: {dur_display}"
data-mapper:
  dur_display: |
    c = &ops.durability()
    m = &ops.durabilityMax()
    return &c :: toString() + "/" + &m :: toString()
```

---

## 模式 4：所有权绑定

首次使用时绑定玩家，非所有者无法使用。

```yaml
event:
  on_right_click!!: |
    if (&ops.owner() == null) {
        &ops.bindOwner()
        &player?.sendMessage("&aBound to you!")
        return item
    }
    if (!&ops.isOwner()) {
        &player?.sendMessage("&cThis item belongs to " + &ops.owner())
        return item
    }
    &player?.sendMessage("&aWelcome back, owner!")
    return item
```

---

## 模式 5：条件分支（when 表达式）

根据物品数据执行不同逻辑。

```yaml
event:
  on_right_click: |
    mode = &ops.data("mode") ?: "normal"
    when &mode {
        "normal" -> {
            &player?.sendMessage("&7Normal mode")
            &ops.setData("mode", "power")
        }
        "power" -> {
            &player?.sendMessage("&cPower mode!")
            &ops.setCooldown(200)
            &ops.setData("mode", "normal")
        }
        else -> {
            &ops.setData("mode", "normal")
        }
    }
    return item
```

---

## 模式 6：计数器与累积效果

记录击杀数，达到阈值触发特殊效果。

```yaml
event:
  on_attack: |
    count = int(&ops.data("kill_count") ?: 0)
    count = &count + 1
    &ops.setData("kill_count", &count)
    if (&count % 10 == 0) {
        &player?.sendMessage("&6Kill streak: " + &count + "!")
        &player.setHealth(min(&player.getHealth() + 4.0, &player.getMaxHealth()))
    }
    &ops.damage(1)
    return item
```

---

## 模式 7：方块交互

右键方块时获取方块信息。

```yaml
event:
  on_right_click: |
    block = &event.getClickedBlock()
    if (&block != null) {
        type = &block.getType().name()
        &player?.sendMessage("&7Clicked: " + &type)
        &ops.setData("last_block", &type)
    }
    return item
```

---

## 模式 8：异步 Tick 定时效果

按全局 `operations.async-tick.default-interval` 和物品 `meta.async-tick` 自动执行，适合被动效果。调度器每 tick 扫描在线玩家背包，但只有满足物品间隔和条件时才 dispatch。

```yaml
meta:
  async-tick:
    interval: 40
    conditions:
      slots:
        - MAINHAND
      sneaking: true
event:
  on_async_tick: |
    &ops.setData("tick_count", int(&ops.data("tick_count") ?: 0) + 1)
    &ops.setData("tick_slot", &ctx["slot"])
    &ops.setData("tick_index", &ctx["slot_index"])
    return item
```

注意：`on_async_tick` 由调度器触发，没有 Bukkit 事件对象，`&event` 为 null。尽量只读写 `&ops` 数据或做轻量逻辑；若要依赖玩家状态，优先用 `meta.async-tick.conditions` 做过滤。

---

## 模式 9：Meta 独立脚本

Meta 拥有独立的 build/drop 脚本，用于模块化物品效果。

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
  lifesteal:
    scripts:
      build: |
        &ops.setData("lifesteal_percent", 10)
        return item
```

---

## 模式 10：调试脚本

在 build 阶段输出调试信息。

```yaml
scripts:
  build: |
    if (&ctx["debug"] == true) {
        &sender?.sendMessage("[Debug] Building " + &itemId)
        &sender?.sendMessage("[Debug] Trigger: " + &trigger)
        &sender?.sendMessage("[Debug] Player: " + &?player?.getName())
    }
    return item
```

---

## 常见错误

### 忘记 `&` 引用变量

```fluxon
// ❌ 错误：ops 是字符串 "ops"，不是变量
ops.setData("key", "value")

// ✅ 正确
&ops.setData("key", "value")
```

### 忘记 `return item`

```fluxon
// ❌ 错误：没有返回值，物品流可能异常
event:
  on_attack: |
    &ops.damage(1)

// ✅ 正确
event:
  on_attack: |
    &ops.damage(1)
    return item
```

### 不安全的空值访问

```fluxon
// ❌ 错误：player 可能为 null
&player.sendMessage("hello")

// ✅ 正确
&player?.sendMessage("hello")
```

### 混淆 `::` 和 `.`

```fluxon
// &ops 是 Java 对象，用 . 调用其方法
&ops.setData("key", "value")     // ✅ Java 反射调用

// Fluxon 扩展函数用 ::
&list :: filter(|| &it > 5)      // ✅ Fluxon 扩展函数

// 不要对 Java 对象用 ::
&ops :: setData("key", "value")  // ❌ setData 不是 Fluxon 扩展函数
```
