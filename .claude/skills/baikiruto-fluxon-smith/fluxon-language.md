# Fluxon 语言参考

Fluxon 脚本语言完整语法规则。Baikiruto 物品脚本基于此语言编写。

## 黄金规则

**裸标识符在表达式位置是字符串字面量，不是变量引用。** 读取变量必须用 `&name`。

```fluxon
x = 10
print(x)    // ❌ 输出字符串 "x"
print(&x)   // ✅ 输出 10
y = x + 5   // ❌ "x" + 5 → 类型错误
y = &x + 5  // ✅ 15
```

---

## 1. 基础语法

- 语句分隔：换行或 `;`
- 注释：`// 行注释`、`/* 块注释 */`
- 代码块：`{ <stmt>* <expr>? }`，值为最后一个表达式的结果

### 字面量

| 类型 | 示例 |
|------|------|
| 整数 | `42` |
| 长整数 | `123L` |
| 浮点 | `0.5f` |
| 双精度 | `2.5e3` |
| 字符串 | `"hello"` 或 `'world'` |
| 布尔 | `true`、`false` |
| 空值 | `null` |
| 列表 | `[1, 2, 3]` |
| 映射 | `[host: "localhost", "port": 8080]` |

### 字符串插值

```fluxon
name = "World"
"Hello ${&name}!"              // "Hello World!"
"Sum: ${&a + &b}"              // "Sum: 30"
"Len: ${'hello'::length()}"   // "Len: 5"
```

- 单/双引号均支持插值
- `null` 值转为字符串 `"null"`
- 转义：`\${` 输出字面量 `${`

### 标识符

- 支持中文字符、`-` 作为非首字符（如 `log-level`）

---

## 2. 变量与引用

| 形式 | 行为 |
|------|------|
| `&name` | 严格引用，变量未定义时报错 |
| `&?name` | 可选引用，未定义或 `null` 时返回 `null` |

```fluxon
x = 10              // 基本赋值
x += 5              // 复合赋值：+= -= *= /= %=
```

### 常量

全大写标识符赋值字面量时自动成为编译时内联常量：

```fluxon
PI = 3.14159        // 常量，编译时内联
PI = 6.28           // ❌ Cannot reassign constant
```

### 解构赋值

```fluxon
(a, b) = [10, 20]
for (key, value) in &map { print(&key + ":" + &value) }
```

---

## 3. 运算符优先级（高→低）

1. 后缀：`f()`、`x[i]`
2. 成员访问：`obj.member`、`obj?.member`（null 短路）
3. 上下文调用：`target :: func()`、`target ?:: func()`（null 短路）
4. 一元：`!`、`-`、`await`、`&name`、`&?name`
5. 幂：`**`
6. 乘除模：`*`、`/`、`%`
7. 加减：`+`、`-`
8. 区间：`a..b`（闭区间）、`a..<b`（左闭右开）
9. 比较/类型检查：`> >= < <= == !=`、`is`
10. 逻辑：`&&`、`||`（短路求值）
11. 三元：`<cond> ? <then> : <else>`
12. Elvis：`<expr> ?: <fallback>`（左侧为 null 时取右侧）
13. 赋值：`=`、`+=`、`-=`、`*=`、`/=`、`%=`

### 类型检查 `is`

```fluxon
"hello" is string   // true
123 is int           // true
null is string       // false
obj is java.util.ArrayList  // 支持完全限定类名
```

类型别名（不区分大小写）：`string`、`int`、`long`、`float`、`double`、`boolean`、`list`、`map`、`set`

---

## 4. 函数与 Lambda

### 函数定义（仅顶层，不可嵌套）

```fluxon
def add(a, b) = &a + &b                    // 表达式体
def abs(n) {                                // 块体
    if &n >= 0 { return &n }
    return -&n
}
def add a, b = &a + &b                      // 无括号参数
async def fetchData() = { ... }             // 异步，返回 CompletableFuture
sync def updateUI() = { ... }              // 主线程执行
```

### Lambda

```fluxon
inc = |x| &x + 1                           // 带参数
sum = |a, b| &a + &b
process = |x| { y = &x * 2; &y + 1 }      // 块体

// 隐式参数 it（|| 语法）
doubled = [1, 2, 3] :: map(|| &it * 2)     // [2, 4, 6]

// 动态调用
fn = |x| &x + 1
call(&fn, [5])  // 6
```

---

## 5. 控制流（均为表达式，有返回值）

### if

```fluxon
grade = if &score >= 90 then "A" else "B"
result = if &x > 0 { "positive" } else { "non-positive" }
if &debug { print("debug mode") }           // 无 else 返回 null
```

### when

```fluxon
// 条件模式
label = when {
    &n % 2 == 0 -> "even"
    &n > 100    -> "big"
    else        -> "odd"
}
// 值匹配 + 范围
bucket = when &y {
    in 0..10   -> "small"
    in 11..100 -> "medium"
    else       -> "large"
}
// 类型匹配
typeLabel = when &obj {
    is int    -> "integer"
    is string -> "text"
    else      -> "unknown"
}
```

### 循环

```fluxon
for i in 1..5 { print(&i) }           // 闭区间 [1,5]
for i in 0..<10 { print(&i) }         // 左闭右开 [0,10)
for (k, v) in &map { print(&k) }      // 解构迭代
while &j < 10 { j += 1 }
// break / continue 仅循环体内有效
```

### 三元 / Elvis

```fluxon
result = &x > 0 ? "pos" : "neg"
name = &?username ?: "anonymous"
```

### 异常处理

```fluxon
result = try {
    throw("boom")
} catch (e) {
    "caught: " + &e.message
} finally {
    print("cleanup")
}
```

---

## 6. 上下文调用 `::`

`::` 在目标对象上调用 Fluxon 扩展函数。

```fluxon
target :: method(args)                      // 单次调用
target :: { method1(); method2() }          // 块形式
target ?:: method(args)                     // 安全形式（null 时返回 null）
```

### 关键区分

```fluxon
&list :: size()    // ✅ 对变量 list 调用扩展函数
list :: size()     // ❌ list 被当作函数调用 list()，再对返回值调用 size()
```

---

## 7. JVM 互操作

### 成员访问 `.`（Java 反射）

```fluxon
&text.length()                             // Java 方法调用
&text.toUpperCase()
&maybeNull?.length()                       // 安全访问，null 时返回 null
```

### 静态成员 `static`

```fluxon
static java.lang.Integer.parseInt("42")     // 静态方法
static java.lang.Math.PI                    // 静态字段
static (java.lang.Integer).TYPE.getName()   // 括号消歧义
```

### 构造对象 `new`

```fluxon
list = new java.util.ArrayList()
sb = new java.lang.StringBuilder("hello") :: toString()
```

### 匿名实现 `impl`

```fluxon
runnable = impl: java.lang.Runnable {
    override run {
        print("running")
    }
}
```

### 并发

```fluxon
async def fetchData() = { sleep(1000); "data loaded" }
result = await fetchData()

future = scope {
    runAsync { heavyComputation() }
    runSync { updateState() }
}
result = await &future
```

### Domain 表达式

```fluxon
result = &obj :: with { doSomething(); computeResult() }  // 返回最后一行
list = [1, 2, 3] :: also { print("created") }            // 返回 target
```

---

## 8. `.` 与 `::` 的区别

| 语法 | 含义 | 使用场景 |
|------|------|----------|
| `&obj.method()` | Java 反射调用 | 调用 Java/Bukkit 对象的方法 |
| `&obj :: method()` | Fluxon 扩展函数 | 调用 Fluxon 内置/注册的扩展函数 |

```fluxon
// Java 对象用 .
&player.sendMessage("hello")
&ops.setData("key", "value")
&event.getEntity()

// Fluxon 扩展函数用 ::
&list :: filter(|| &it > 5)
"hello" :: uppercase()
&data :: map(|| &it :: get("name"))
```

**规则**：Bukkit API 对象（`&player`、`&event`、`&item`、`&ops`）用 `.`，Fluxon 内置操作（集合处理、字符串扩展）用 `::`。
