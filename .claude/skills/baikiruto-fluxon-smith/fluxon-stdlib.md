# Fluxon 内置函数与扩展函数

## 全局内置函数

### 系统函数

| 函数 | 签名 | 说明 |
|------|------|------|
| `print(obj)` | `Object -> void` | 输出到 stdout |
| `error(obj)` | `Object -> void` | 输出到 stderr |
| `sleep(ms)` | `int -> void` | 线程休眠（毫秒） |
| `forName(name)` | `String -> Class` | 按全限定名加载 Java 类 |
| `call(fn, args)` | `(Object, List) -> Object` | 动态调用函数/Lambda |
| `this()` | `-> Object` | 获取当前上下文的 target 对象 |
| `throw(obj)` | `Object -> void` | 抛出异常 |

### 类型转换

| 函数 | 说明 |
|------|------|
| `string(obj)` | 转字符串 |
| `int(obj)` / `intOrNull(obj)` | 转 int（失败返回 0 / null） |
| `long(obj)` / `longOrNull(obj)` | 转 long |
| `float(obj)` / `floatOrNull(obj)` | 转 float |
| `double(obj)` / `doubleOrNull(obj)` | 转 double |
| `array(obj)` | Collection 转数组 |
| `list(obj)` / `mutableList(obj)` | 数组转 List |
| `typeOf(obj)` | 获取类型名 |
| `isString(obj)` / `isNumber(obj)` / `isArray(obj)` / `isList(obj)` / `isMap(obj)` | 类型检查 |

### 数学函数

| 分类 | 函数 |
|------|------|
| 基础 | `min(a,b)`, `max(a,b)`, `clamp(val,min,max)`, `abs(n)`, `sign(n)` |
| 取整 | `round(d)`, `floor(d)`, `ceil(d)` |
| 三角 | `sin(d)`, `cos(d)`, `tan(d)`, `asin(d)`, `acos(d)`, `atan(d)`, `atan2(y,x)` |
| 指数/对数 | `exp(d)`, `log(d)`, `log10(d)`, `pow(base,exp)`, `sqrt(d)`, `cbrt(d)` |
| 随机 | `random()` → 0.0~1.0, `random(n)` → 0~n-1, `random(min,max)` → min~max-1 |
| 角度 | `rad(deg)`, `deg(rad)` |
| 其他 | `lerp(a,b,t)`, `hypot(x,y)` |

全局常量：`PI`、`E`

---

## 扩展函数（通过 `::` 调用）

### Object（所有对象通用）

`toString()`, `hashCode()`, `class()`, `isInstance(obj)`

### String

| 分类 | 函数 |
|------|------|
| 基础 | `length()`, `trim()`, `ltrim()`, `rtrim()` |
| 查找 | `indexOf(s)`, `indexOf(s,from)`, `lastIndexOf(s)`, `lastIndexOf(s,from)` |
| 替换 | `replace(old,new)`, `replaceAll(regex,replacement)` |
| 匹配 | `contains(s)`, `matches(regex)`, `findAll(regex)` |
| 截取 | `substring(start)`, `substring(start,end)` |
| 填充 | `padLeft(len)`, `padLeft(len,char)`, `padRight(len)`, `padRight(len,char)` |
| 转换 | `uppercase()`, `lowercase()`, `capitalize()`, `reverse()`, `repeat(n)`, `split(delim)` |
| 检查 | `startsWith(prefix)`, `endsWith(suffix)`, `isEmpty()`, `isBlank()` |
| 字符 | `charAt(i)`, `charCodeAt(i)`, `toCharArray()` |

### Collection（通用集合）

`size()`, `isEmpty()`, `contains(obj)`, `toArray()`, `join()`, `join(delim)`, `random()`, `random(n)`, `add(obj)`, `remove(obj)`, `addAll(c)`, `removeAll(c)`, `clear()`

### Iterable（List/Set 等可迭代对象）

| 分类 | 函数 |
|------|------|
| 转换 | `map(fn)`, `flatMap(fn)`, `associateBy(fn)`, `associateWith(fn)` |
| 过滤 | `filter(fn)` |
| 检查 | `any(fn)`, `all(fn)`, `none(fn)` |
| 检索 | `find(fn)`, `first()`, `last()` |
| 聚合 | `countOf(fn)`, `sumOf(fn)`, `minOf(fn)`, `maxOf(fn)`, `minBy(fn)`, `maxBy(fn)` |
| 分组 | `groupBy(fn)`, `partition(fn)`, `chunked(n)` |
| 排序 | `sorted()`, `sortedDescending()`, `sortedBy(fn)`, `sortedDescendingBy(fn)`, `reversed()`, `shuffled()` |
| 截取 | `take(n)`, `drop(n)`, `takeLast(n)`, `dropLast(n)` |
| 集合运算 | `union(list)`, `intersect(list)`, `subtract(list)`, `distinct()`, `distinctBy(fn)` |
| 遍历 | `each(fn)` |

Lambda 参数约定：单参数用 `|| &it`，多参数用 `|a, b| ...`

### List（有序列表专用）

`get(i)`, `set(i,obj)`, `insert(i,obj)`, `removeAt(i)`, `indexOf(obj)`, `lastIndexOf(obj)`, `subList(from,to)`

### Map

`get(k)`, `getOrDefault(k,default)`, `put(k,v)`, `remove(k)`, `containsKey(k)`, `containsValue(v)`, `size()`, `isEmpty()`, `clear()`, `keySet()`, `values()`, `entrySet()`, `putAll(map)`, `putIfAbsent(k,v)`, `replace(k,v)`

### Map.Entry

`key()`, `value()`

### Domain 表达式

| 表达式 | 说明 |
|--------|------|
| `target :: with { ... }` | 执行闭包，返回最后一行的值 |
| `target :: also { ... }` | 执行闭包，返回 target 本身 |

---

## Import 模块

语法：`import 'fs:xxx'`

### fs:time — 时间模块

```fluxon
import 'fs:time'
now = time :: formatDateTime("yyyy-MM-dd HH:mm:ss")
ts = time :: parseDateTime("2024-01-01", "yyyy-MM-dd")
days = time :: daysBetween(&ts, time :: now())
```

TimeObject 方法（`time :: xxx()`）：
- 基础：`now()`, `nowSeconds()`, `nano()`
- 格式化：`formatDateTime(pattern?)`, `formatTimestamp(ts, pattern?)`, `parseDateTime(str, pattern)`
- 时间组件：`year()`, `month()`, `day()`, `hour()`, `minute()`, `second()`, `weekday()`
- 从时间戳提取：`yearFromTimestamp(ts)`, `monthFromTimestamp(ts)`, `dayFromTimestamp(ts)`, `hourFromTimestamp(ts)`, `minuteFromTimestamp(ts)`, `secondFromTimestamp(ts)`
- 时间计算：`addDays(ts, n)`, `addHours(ts, n)`, `addMinutes(ts, n)`, `addSeconds(ts, n)`
- 时间差：`daysBetween(ts1, ts2)`, `hoursBetween(ts1, ts2)`, `minutesBetween(ts1, ts2)`, `secondsBetween(ts1, ts2)`
- 比较：`isToday(ts)`, `isYesterday(ts)`, `isTomorrow(ts)`, `isBetween(ts, start, end)`
- 边界：`startOfDay(ts)`, `endOfDay(ts)`, `startOfMonth(ts)`, `endOfMonth(ts)`, `startOfYear(ts)`, `endOfYear(ts)`

### fs:crypto — 加密/编码模块

```fluxon
import 'fs:crypto'
hashed = hash :: sha256("password123")
encoded = base64 :: encode("Hello World")
decoded = base64 :: decode(&encoded)
hexStr = hex :: encode("hello")
```

工具对象：`hash`（md5/sha1/sha256/sha384/sha512）、`base64`（encode/decode）、`unicode`（encode/decode）、`hex`（encode/decode）

### fs:io — 文件/路径模块

```fluxon
import 'fs:io'
f = file("config.txt")
if &f :: exists() { content = &f :: readText() }
file("output.txt") :: writeText("Hello!")
```

File 扩展：`name()`, `path()`, `exists()`, `isFile()`, `isDirectory()`, `readText()`, `readLines()`, `writeText(str)`, `appendText(str)`, `delete()`, `mkdir()`, `mkdirs()`, `list()`, `walk()` 等

### fs:reflect — 反射模块

```fluxon
import 'fs:reflect'
cls = forName("java.lang.String")
&cls :: methods() :: each(|| print(&it :: name()))
m = &cls :: method("substring")
result = &m :: invoke("hello world", 6)
```

Class/Method/Field/Constructor 扩展函数，提供完整的反射操作能力。
