## Baikiruto Plugin

Baikiruto 是基于 TabooLib 的跨版本物品插件，面向高自定义、可脚本化、可热重载的服务器物品体系。

- 覆盖 Minecraft `1.12 - 1.21.11` 物品能力
- Fluxon Only 脚本执行链路
- 面向实战的物品构建、读回、重构与在线更新

## 功能特性

- 跨版本物品配置与生成（含模型、显示、分组、元数据）
- `ItemStream` 运行时数据流：`build / read / rebuild / update`
- `components` 简化解析：
- 自动兼容 `minecraft:` 前缀与无前缀写法
- 支持扁平文本（如 `&6Example`）与结构化文本配置
- Meta 扩展机制（`MetaFactory`）：
- 支持注册与注销自定义工厂
- 支持配置侧按 `metas.type` 动态分发
- 管理能力：
- `list / give / serialize / rebuild / menu / reload / selfcheck`
- 分组 GUI 浏览与发放
- reload 后在线玩家物品自动更新检查

## 版本信息

| 项目 | 内容 |
| --- | --- |
| 插件定位 | 跨版本物品系统 |
| 脚本引擎 | Fluxon Only |
| Kether 支持 | 不提供 |
| 兼容版本 | 1.12 - 1.21.11 |

## 适用场景

- RPG 物品系统
- 带脚本行为的技能/装备体系
- 需要频繁调配置并热更新的服务器 
