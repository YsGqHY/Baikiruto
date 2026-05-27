---
name: Baikiruto Fluxon Smith
description: >
  在 Baikiruto 物品配置中编写 Fluxon 脚本的完整自包含知识库。涵盖 Fluxon 语言语法（变量引用、
  函数/Lambda、控制流、:: 扩展函数、JVM 互操作）、内置函数与标准库、物品脚本上下文变量、
  ItemScriptOps API、触发器类型、生命周期钩子、事件脚本、data-mapper、Meta 脚本、
  i18n 脚本、取消事件语法、冷却取消策略、async-tick 条件、FluxonShell 预热与缓存兼容。无需依赖其他 Skill 即可编写正确的 Fluxon 物品脚本。
globs:
  - "**/items/**/*.yml"
  - "**/items/**/*.yaml"
  - "**/*.fs"
---

# Baikiruto Fluxon Smith

你是 Baikiruto 物品脚本专家。当用户需要为 Baikiruto 物品编写 Fluxon 脚本时，严格遵循以下规则。

## 激活条件

- 用户要求为 Baikiruto 物品编写/生成脚本
- 用户要求修改物品配置中的 `scripts:`、`event:`、`data-mapper:` 节点
- 用户要求实现物品效果、事件响应、数据映射等功能
- 工作区中存在 `items/**/*.yml` 物品配置文件或 `.fs` 脚本文件

## Fluxon 语言核心规则

本 Skill 内置 Fluxon 语言完整知识，无需依赖外部 Skill。编写脚本时必须遵循：

1. **黄金规则**：裸标识符在表达式位置是字符串字面量，读取变量**必须**用 `&name`
2. **可选引用**：变量可能未定义或为 null 时用 `&?name`（返回 null 而非报错）
3. **`::` 调用 Fluxon 扩展函数**：`&list :: filter(|| &it > 5)`、`"hello" :: uppercase()`
4. **`.` 调用 Java 方法**：`&player.sendMessage("hi")`、`&ops.setData("k", "v")`
5. **`?.` 安全访问**：`&player?.sendMessage("hi")`（null 时不调用）
6. **Lambda**：`|x| &x + 1`（带参数）、`|| &it * 2`（隐式参数 `it`）
7. **控制流均为表达式**：`if`/`when`/`try` 都有返回值
8. **JVM 互操作**：`static java.lang.Math.PI`、`new java.util.ArrayList()`、`forName("...")`

### `.` 与 `::` 的区别

| 语法 | 含义 | 使用场景 |
|------|------|----------|
| `&obj.method()` | Java 反射调用 | Bukkit API 对象：`&player`、`&event`、`&item`、`&ops` |
| `&obj :: method()` | Fluxon 扩展函数 | 集合处理、字符串操作等 Fluxon 内置函数 |

```fluxon
// ✅ Java 对象用 .
&player.sendMessage("hello")
&ops.setData("key", "value")
&event.getEntity().setFireTicks(60)

// ✅ Fluxon 扩展函数用 ::
&list :: filter(|| &it > 5) :: map(|| &it * 2)
"hello world" :: split(" ") :: map(|| &it :: capitalize()) :: join(" ")

// ❌ 不要对 Java 对象用 ::
&ops :: setData("key", "value")  // setData 不是 Fluxon 扩展函数
```

## 核心行为

1. 生成的脚本必须写在 YAML 物品配置的对应节点中（`scripts:`、`event:`、`data-mapper:`、`metas:` 下的 `scripts:`）
2. 脚本中**必须**通过 `&变量名` 引用上下文变量，不可使用裸标识符
3. 优先使用 `&ops` 提供的 DSL 方法操作物品，而非直接操作 `&stream`
4. 脚本末尾应 `return item` 以保持物品流正常运转（生命周期和事件脚本）
5. `&player` 和 `&sender` 可能为 null，必须用 `?.` 安全访问
6. 使用 `!!` 取消语法代替手动取消事件
7. 需要取消事件后仍应用自动冷却时，必须在 `apply-on-cancelled-triggers` / `apply_on_cancelled_triggers` 中配置触发器
8. `on_async_tick` 中 `&event` 为 null，依赖槽位时使用 `&ctx["slot"]` 与 `&ctx["slot_index"]`

## 脚本执行与缓存

- 内置脚本类型为 `fluxon`，也可写 `type: fluxon` / `engine: fluxon` / 直接字符串脚本。
- 启动或重载时会按配置预热脚本；运行时使用脚本 ID 缓存 `FluxonShell.parse` 的结果，源码变化或物品重载会按物品 ID 前缀失效缓存。
- 执行环境会将 Baikiruto 上下文变量定义为 Fluxon 根变量；`sender` 和玩家场景下的 `player` 也会由 Fluxon 环境显式定义。
- FluxonShell 运行异常会被隔离记录，脚本失败返回 null，不应让单个脚本中断物品事件链。

## 知识文件

### Fluxon 语言（自包含，无需外部 Skill）
- `fluxon-language.md` -- 完整语法：变量引用、运算符、函数/Lambda、控制流、:: 上下文调用、JVM 互操作
- `fluxon-stdlib.md` -- 内置函数、扩展函数速查表、import 模块（fs:time/fs:crypto/fs:io/fs:reflect）

### Baikiruto 物品脚本
- `context-variables.md` -- 脚本执行时注入的上下文变量表
- `ops-api.md` -- ItemScriptOps DSL 方法参考
- `triggers.md` -- 触发器类型、配置语法、i18n 脚本、取消事件
- `patterns.md` -- 常见脚本模式、最佳实践、完整示例

## 快速示例

```yaml
items:
  "my_plugin:fire_sword":
    icon: "NETHERITE_SWORD"
    version-hash: "v1"
    scripts:
      build: |
        if (&ctx["debug"] == true) {
            &sender?.sendMessage("[Debug] building fire_sword")
        }
        return item
    meta:
      cooldown:
        ticks: 100
        apply-on-cancelled-triggers:
          - on_right_click
      async-tick:
        interval: 40
        conditions:
          slots: MAINHAND
          sneaking: true
    event:
      on_attack: |
        target = &event.getEntity()
        &target.setFireTicks(60)
        &ops.damage(1)
        &ops.setData("last_hit", &target.getName())
        return item
      on_right_click!!: |
        if (&ops.cooldown() > 0) {
            remaining = &ops.cooldown() / 20
            &player?.sendMessage("&cCooldown: " + &remaining + "s")
            return item
        }
        &ops.setCooldown(100)
        &player?.sendMessage("&aFire Blast!")
        return item
      on_async_tick: |
        &ops.setData("last_tick_slot", &ctx["slot"])
        &ops.setData("last_tick_index", &ctx["slot_index"])
        return item
    data-mapper:
      fire_status: |
        cd = &ops.cooldown()
        return &cd > 0 ? "&cCooling" : "&aReady"
```
