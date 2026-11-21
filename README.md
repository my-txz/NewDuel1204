# NewDuel1204

**高性能 · 低占用的 Minecraft 1v1 竞技场 / 决斗插件**（Paper 1.20.4）

> 预生成世界 · 21 种玩法 · 匹配与排位 · ELO 段位 · 崩溃级数据保护

[![Paper](https://img.shields.io/badge/Paper-1.20.4-3fb950?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Jar](https://img.shields.io/badge/Jar-165KB-8a2be2?style=flat-square)](../../releases)

---

## 目录

- [为什么选它](#为什么选它)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速开始](#快速开始)
- [21 种对战模式](#21-种对战模式)
- [匹配与排位系统](#匹配与排位系统)
- [数据安全设计](#数据安全设计)
- [性能设计](#性能设计)
- [命令与权限](#命令与权限)
- [配置文件](#配置文件)
- [数据兼容性](#数据兼容性)
- [从源码构建](#从源码构建)
- [常见问题](#常见问题)

---

## 为什么选它

| 特性 | 说明 |
| --- | --- |
| **开箱即用** | 不需要 WorldEdit，不需要手工建图，装进 `plugins/` 重启即可开打 |
| **预生成世界** | 启动时用虚空生成器创建独立竞技场世界，并把全部场地预生成落盘，对战时只做区块加载 |
| **21 种玩法** | 无药水、药水、相扑、拳击、水晶、建筑 UHC、蘑菇汤、一击必杀、火焰弹……每周不重样 |
| **匹配 + 排位** | 内存级匹配队列，ELO 搜索范围随等待时间自动扩大；7 段位、每模式独立分数 |
| **数据绝对安全** | 先落盘再改动、崩溃自动还原、世界级 keepInventory 三重保险 |
| **低占用** | 单文件 165KB，零第三方依赖；每局只有 1 个 10 tick 定时器，24 个场地全空闲时 0 常驻开销 |
| **向后兼容** | 配置、语言、玩家数据均带 schema 版本与增量迁移，升级不丢数据 |
| **可运维** | `/nd info` 一眼看清 TPS、场地、队列、待还原快照；`/nd reload` 热重载 |

实测（Paper 1.20.4 build 499 / 2 核 4G / Java 20）：

- 插件启用耗时 **148 ms**
- 24 个竞技场预生成 **3.0 s**（后台分帧执行，不卡启动）
- 空载 TPS **20.02**，内存占用 < 5 MB

---

## 环境要求

- **服务端**：Paper 1.20.4（build 499 已验证），Spigot 系 1.20.4 理论可用但未测试
- **Java**：17 或更高
- **内存**：建议 ≥ 1 GB（插件本身开销可忽略）
- 不需要任何前置插件

---

## 安装

1. 从 [Releases](../../releases) 下载 `NewDuel1204-1.0.0.jar`
2. 放入服务端 `plugins/` 目录
3. 重启服务器（首次启动会自动创建 `newduel_arena` 世界并预生成场地）

首次启动日志应当类似：

```
[NewDuel1204] 已分配 24 个竞技场（5x5 网格）
[NewDuel1204] 开始后台预生成 24 个竞技场…
Duel » 竞技场预生成完成 (3022ms)，随时可以开战。
```

> 生成的 `newduel_arena` 世界**不要**手工修改，也不要用 Multiverse 删除，否则插件会按坐标自动重建。

---

## 快速开始

```
/duel                      # 打开决斗中心 GUI
/duel Steve                # 邀请 Steve（使用你上次选择的模式）
/duel Steve sumo           # 邀请 Steve 打相扑
/duel accept Steve         # 接受 Steve 的邀请（消息里也有可点击按钮）
/duel leave                # 认输 / 退出队列 / 退出观战
/queue crystal ranked      # 加入水晶 PvP 的排位队列
/duel top                  # 排行榜
/duel stats                # 我的战绩
```

流程：`邀请 → 对方接受 → 系统占场并加载区块 → 保存双方数据 → 传送 + 发装备 → 3 秒倒计时 → 开打 → 结算 → 还原数据`。

---

## 21 种对战模式

| ID | 名称 | 类型 | 说明 |
| --- | --- | --- | --- |
| `nodebuff` | 无药水决斗 | 对战 | 钻石保护IV、锋利V、速度II、治疗药水 |
| `debuff` | 药水决斗 | 对战 | 毒药 / 迟缓 / 伤害药水全开 |
| `classic` | 经典决斗 | 对战 | 铁装 + 弓，适合新手 |
| `gapple` | 金苹果决斗 | 对战 | 32 个金苹果的消耗战 |
| `soup` | 蘑菇汤决斗 | 对战 | 右键喝汤回 3.5 心，手速为王 |
| `sumo` | 相扑 | 技巧 | 高空圆台，击落即胜，无伤害 |
| `boxing` | 拳击 | 技巧 | 先命中 100 次获胜 |
| `archer` | 弓箭决斗 | 对战 | 力量III 弓 + 皮革套 |
| `builduhc` | 建筑 UHC | 对战 | 可搭桥 / 放水 / 放岩浆，无自然回血 |
| `uhc` | 极限生存 | 对战 | 无自然回血，靠金苹果续航 |
| `diamond` | 钻石装 | 对战 | 无附魔对拼 |
| `iron` | 铁装 | 对战 | 轻量级对拼 |
| `gold` | 金装 | 对战 | 脆皮高速刺客局 |
| `netherite` | 下界合金 | 对战 | 火焰附加 + 抗火 |
| `axe` | 盾斧决斗 | 对战 | 破盾博弈，副手盾牌 |
| `trident` | 三叉戟 | 对战 | 忠诚III / 穿刺V / 引雷 + 末影珍珠 |
| `crystal` | 水晶 PvP | 对战 | 128 水晶 + 64 黑曜石 + 2 图腾，可破坏地形 |
| `onehit` | 一击必杀 | 一击 | 木剑无甲，谁先碰到谁赢 |
| `combo` | 连击 | 对战 | 击退II 木棍，命中把对手挑上天 |
| `pearl` | 珍珠战 | 对战 | 16 颗末影珍珠的机动战 |
| `fireball` | 火焰弹 | 对战 | 右键发射火焰弹，把对手炸进虚空 |

模式特性：`allowBuild`（地形可破坏，结束后自动回滚）、`naturalRegen`、`timeLimit`、`hitTarget`、`combat` 规则、`win` 条件全部集中在 `GameMode` 枚举里，**新增一个模式只需一行枚举 + 一套 kit**。

---

## 匹配与排位系统

### 匹配队列

- 双方同意加入同一模式队列后，每 `queue.interval-ticks`（默认 20 tick）扫描一次
- 起始 ELO 搜索范围 `±100`，每等待 10 秒扩大 `±100`，上限 `±800`
- 匹配成功立即从队列移除并占场，避免"重复匹配"
- 队列状态通过 action bar 实时反馈：`模式 | 等待时间 | 队列人数 | 搜索范围`

### ELO 与段位

标准 ELO：`新分 = 旧分 + K × (实际 − 期望)`，`K = 32`，定级赛（前 10 场）K 值 ×1.75，胜负保底 ±3 分。

| 段位 | 分数 |
| --- | --- |
| 木质 | 0 – 899 |
| 铁质 | 900 – 1049 |
| 金质 | 1050 – 1199 |
| 铂金 | 1200 – 1349 |
| 钻石 | 1350 – 1499 |
| 大师 | 1500 – 1649 |
| 宗师 | 1650+ |

每个模式有独立分数，另有一个全局分数用于总榜。`/nd season reset` 可开启新赛季。

---

## 数据安全设计

这是本插件最用力的部分。玩家进入竞技场前后，以下状态会被完整快照并逐字段还原：

> 背包 36 格 + 4 件护甲 + 副手、末影箱 27 格、主手槽位、生命值、最大生命值、饱食度 / 饱和度 / 疲劳度、等级 / 经验 / 总经验、游戏模式、飞行状态、行走 / 飞行速度、无敌标记、着火 tick、氧气值、全部药水效果、坐标与视角

**三重保险：**

1. **先存后改**：快照必须成功写入磁盘（原子写入 `tmp → rename`）之后，才允许传送玩家、替换装备。落盘失败 → 对局直接中止，玩家状态保持原样并提示。
2. **崩溃自动还原**：快照文件在**还原成功之前不会删除**。服务器崩溃、玩家掉线、插件被强制卸载，都会在下次启动 / 玩家上线时自动还原；还原成功后才会写入 `snapshots-backup/` 备份并删除待还原文件（备份保留 72 小时，可手工找回）。
3. **世界级兜底**：竞技场世界强制开启 `keepInventory`，死亡清空掉落与经验掉落，即使极端情况下也不会掉物品。

其它保护：

- 对局中禁止丢弃物品、禁止非参赛者拾取、禁止外部伤害
- 对局中默认封锁命令（仅放行 `/duel` `/queue`），防止 `/spawn` 逃跑
- 建筑 / 水晶类模式的方块改动全部记入 undo 日志，对局结束逐块回滚
- 玩家离线时快照保留，重连后自动还原，绝不"用竞技场装备覆盖真实背包"

相关命令：

```
/nd snapshot list             # 查看待还原快照
/nd snapshot recover Steve    # 手工强制还原
```

---

## 性能设计

| 手段 | 收益 |
| --- | --- |
| 虚空生成器世界 | 世界体积接近 0，区块生成几乎零成本 |
| 启动期预生成 | 战斗时只读磁盘区块，无地形生成尖峰 |
| 异步区块加载 + 插件区块票据 | 传送前预加载，比赛中区块不会被卸载 |
| 单一 10 tick 定时器 / 局 | 100 局同时进行也只有 100 个轻量任务 |
| 共享记分板 + 队伍前缀刷新 | 每局一个记分板对象，不重建、不刷屏 |
| 内存档案缓存 + 脏标记异步落盘 | 排行榜、ELO 计算零磁盘 IO |
| 场地空闲即释放 | 回滚方块、清实体、撤票据，无实体堆积 |
| 零第三方依赖 | 单个 165 KB jar，不 shade、不重定位 |

---

## 命令与权限

### 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/duel` | 打开决斗中心 GUI |
| `/duel <玩家> [模式]` | 发起挑战 |
| `/duel accept [玩家]` / `deny [玩家]` | 接受 / 拒绝邀请 |
| `/duel leave` | 认输 / 退出队列 / 退出观战 |
| `/duel spectate [玩家]`、`/duel spectate stop` | 观战 |
| `/duel stats [玩家]` | 战绩界面 |
| `/duel top [模式]` | 排行榜 |
| `/duel modes` | 模式列表（可点击直接进队列） |
| `/duel rematch` | 再来一局（接受最近的邀请） |
| `/queue [模式] [ranked]`、`/queue leave` | 匹配队列 |

### 管理命令（`/nd`）

| 命令 | 说明 |
| --- | --- |
| `/nd reload` | 热重载配置与语言 |
| `/nd arena list / tp <id> / rebuild <id> / release <id>` | 场地管理 |
| `/nd snapshot list / recover <玩家>` | 快照管理 |
| `/nd profile info / reset <玩家>` | 玩家数据 |
| `/nd season reset` | 重置赛季 |
| `/nd stop <玩家>` | 强制结束某人的对局 |
| `/nd info` | TPS、场地、队列、快照状态 |
| `/nd version` | 版本信息 |

### 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `newduel.use` | 所有人 | 基础功能 |
| `newduel.ranked` | 所有人 | 参加排位 |
| `newduel.spectate` | 所有人 | 观战 |
| `newduel.admin` | OP | 管理命令 |

---

## 配置文件

`plugins/NewDuel1204/config.yml` 关键项：

```yaml
world:
  name: newduel_arena        # 竞技场世界名（改后需重启）
  arena-count: 24            # 场地数量（改后需重启）
  base-y: 100                # 地板高度
  cell-size: 128             # 场地间距（≥96，改后需重启）
  pregenerate-on-start: true # 启动时预生成

match:
  countdown-seconds: 3
  end-delay-seconds: 4
  default-time-limit: 600
  attack-speed: 16.0         # 16 = 1.8 手感，4 = 1.9 冷却
  block-commands: true
  disable-item-drop: true

ranked:
  start-elo: 1000
  k-factor: 32
  provisional-matches: 10

queue:
  interval-ticks: 20
  initial-elo-range: 100
  range-increase-per-10s: 100
  max-elo-range: 800
```

全部文案在 `messages.yml`，使用 [MiniMessage](https://docs.advntr.dev/minimessage/format.html) 格式，支持渐变、悬停、点击事件。

---

## 数据兼容性

| 文件 | 版本机制 |
| --- | --- |
| `config.yml` | `config-version`，升级时自动补齐缺失键，**不覆盖**已有值 |
| `messages.yml` | 加载时把 jar 内新增默认键合并进用户文件，自定义文案不丢 |
| `playerdata/*.yml` | `schema` 版本号 + 增量迁移函数（v1 平铺字段 → v2 分模式 → v3 赛季与历史） |
| `snapshots/*.yml` | 字段独立、缺省容错；旧版本快照可直接被新版本还原 |

写入玩家数据时采用「先读旧文件 → 只覆盖已知键 → 原子写入」，因此**旧版本遗留字段、未来版本新增字段都不会被抹掉**。

---

## 从源码构建

```bash
git clone https://github.com/my-txz/NewDuel1204.git
cd NewDuel1204
mvn clean package
# 产物：target/NewDuel1204-1.0.0.jar
```

需要 JDK 17+ 与 Maven 3.8+。首次构建会从 `repo.papermc.io` 拉取 `paper-api`。

推送到 `main` 或打 `v*` 标签时，GitHub Actions 会自动构建并发布 Release（见 [`.github/workflows/build.yml`](.github/workflows/build.yml)）。

---

## 常见问题

**Q：能用于 1.21 吗？**
编译目标是 1.20.4 的 Paper API。1.21 的附魔 / 药水 API 有重命名，需要改 `Kits` 里的常量后才能编译，功能逻辑本身不依赖新 API。

**Q：可以自己加模式吗？**
可以。在 `GameMode` 枚举加一行，在 `Kits` 写一套装备（返回 `DuelKit`），再按需设置 `combat / win / timeLimit / allowBuild`。GUI、匹配、排位数据会自动支持。

**Q：场地被破坏了怎么办？**
`/nd arena rebuild <id>` 会原地重建。所有模式的方块改动本来就会在对局结束时自动回滚。

**Q：玩家掉线判负吗？**
判负。对手获胜，掉线玩家的快照保留，重新上线时自动还原背包与位置。

**Q：数据放在哪？**
`plugins/NewDuel1204/`：`playerdata/`（战绩）、`snapshots/`（待还原）、`snapshots-backup/`（72 小时备份）。

---

## License

[MIT](LICENSE) © my-txz
