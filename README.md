# Baikiruto

Baikiruto 是基于 TabooLib 的跨版本物品库插件，目标支持 Minecraft 1.12 - 1.21.11。

## 模块结构

- `project:common`：公共 API、ItemStream/Item/Meta 模型
- `project:common-impl`：默认实现、脚本融合、重载/诊断能力
- `project:module-bukkit`：Bukkit 启动层
- `project:module-v1_12`：1.12 版本适配
- `project:module-v1_20_4`：1.20.4 版本适配
- `project:module-v1_21`：1.21.x 版本适配（core 依赖 `ink.ptms.core:v12110:12110`）
- `plugin`：最终聚合打包模块

## 关键命令

- `/baikiruto debug build [itemId]`
- `/baikiruto reload`
- `/baikiruto reload items`
- `/baikiruto reload scripts`
- `/baikiruto selfcheck`

## 文档

- `docs/开发计划.md`
- `docs/开发约定.md`
- `docs/快速开始.md`
- `docs/脚本示例.md`
- `docs/版本支持表.md`
- `docs/排障指南.md`
- `docs/发布清单.md`

## 构建

```bash
./gradlew build
```
