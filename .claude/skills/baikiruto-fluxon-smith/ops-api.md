# ItemScriptOps API 参考

`ItemScriptOps` 是脚本中通过 `&ops` 访问的 DSL 对象，封装了物品流的常用操作。所有方法通过 Java 反射调用（`.` 语法）。

## 方法一览

### 运行时数据

| 方法 | 签名 | 说明 |
|------|------|------|
| `data(key)` | `String -> Any?` | 读取运行时数据 |
| `setData(key, value)` | `(String, Any?) -> ItemScriptOps` | 写入运行时数据，支持链式调用 |

```fluxon
// 读取
count = &ops.data("kill_count") ?: 0

// 写入（链式）
&ops.setData("kill_count", &count + 1)
    .setData("last_kill_time", "now")
```

### 耐久度

| 方法 | 签名 | 说明 |
|------|------|------|
| `damage(amount)` | `int -> boolean` | 扣减耐久，返回是否已损坏 |
| `durability()` | `-> int` | 获取当前耐久值 |
| `durabilityMax()` | `-> int` | 获取最大耐久值 |
| `setDurability(value)` | `int -> ItemScriptOps` | 直接设置耐久值 |

```fluxon
// 攻击时扣耐久
destroyed = &ops.damage(1)
if (&destroyed) {
    &player?.sendMessage("&cYour weapon has broken!")
}

// 读取耐久
current = &ops.durability()
max = &ops.durabilityMax()
ratio = &current / &max
```

### 冷却

| 方法 | 签名 | 说明 |
|------|------|------|
| `cooldown()` | `-> long` | 获取剩余冷却 tick 数 |
| `setCooldown(ticks)` | `long -> ItemScriptOps` | 设置冷却时间（tick） |

```fluxon
// 检查冷却
if (&ops.cooldown() > 0) {
    &player?.sendMessage("&cSkill is on cooldown!")
    return item
}

// 设置 4 秒冷却（80 ticks）
&ops.setCooldown(80)
```

### 唯一绑定

| 方法 | 签名 | 说明 |
|------|------|------|
| `owner()` | `-> String?` | 获取绑定的玩家名 |
| `isOwner()` | `-> boolean` | 当前玩家是否为物品所有者 |
| `bindOwner(name?)` | `String? -> boolean` | 绑定所有者，无参数时绑定当前玩家 |

```fluxon
// 首次使用时绑定
if (&ops.owner() == null) {
    &ops.bindOwner()
    &player?.sendMessage("&aThis item is now bound to you!")
}

// 检查所有权
if (!&ops.isOwner()) {
    &player?.sendMessage("&cThis item belongs to " + &ops.owner())
    return item
}
```

### 信号

| 方法 | 签名 | 说明 |
|------|------|------|
| `signal(name)` | `String -> ItemScriptOps` | 标记信号 |
| `hasSignal(name)` | `String -> boolean` | 检查信号是否已标记 |

信号用于在脚本执行期间标记状态，供 ItemStream 后续处理使用。

```fluxon
// 标记信号
&ops.signal("script_dispatched")
&ops.signal("needs_rebuild")

// 检查信号
if (&ops.hasSignal("script_dispatched")) {
    print("Signal already set")
}
```

可用信号名（`ItemSignal` 枚举值，不区分大小写，`-` 和 `_` 等价）：
- `UPDATE_CHECKED` -- 版本更新已检查
- `SCRIPT_DISPATCHED` -- 脚本已执行
- `DATA_MAPPED` -- 数据映射已完成
- `COOLDOWN_APPLIED` -- 冷却已应用
- `DURABILITY_CHANGED` -- 耐久已变更
- `DURABILITY_DESTROYED` -- 耐久归零已损坏

### 重建

| 方法 | 签名 | 说明 |
|------|------|------|
| `rebuild()` | `-> ItemStack` | 重建并返回新的 ItemStack |

```fluxon
// 修改数据后重建物品
&ops.setData("level", 5)
newItem = &ops.rebuild()
```

## 链式调用

`setData`、`setCooldown`、`setDurability`、`signal` 均返回 `ItemScriptOps` 自身，支持链式调用：

```fluxon
&ops.setData("last_trigger", "attack")
    .damage(1)

&ops.setCooldown(80)
    .signal("script_dispatched")
    .setData("last_use", "right_click")
```

## 注意事项

1. `&ops` 的方法通过 `.` 调用（Java 反射），不是 `::` 上下文调用
2. `damage()` 返回 `boolean`，其余 setter 返回 `ItemScriptOps`
3. `cooldown()` 返回 `long` 类型（tick 数），20 ticks = 1 秒
4. `bindOwner()` 无参数时自动绑定当前 `&player`，`&player` 为 null 时返回 `false`
5. 信号名会自动转大写并将 `-` 替换为 `_`，匹配 `ItemSignal` 枚举
