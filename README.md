# Fake Player Backpack Detection

> 假人背包检测

一个给 Minecraft Java 版用的 Fabric 客户端/服务端模组，用来**查看地毯模组（Carpet）假人的名字与背包内容**。

## 兼容性

- Minecraft：`26.1.2` / `26.2`（Mojang 官方类名），以及 `1.21` – `1.21.11` 全系（Yarn 映射）
- Fabric Loader：`>=0.19.0`
- 前置：`fabric-api`
- 建议（可选）：`carpet`、`jei` / `roughlyenoughitems` / `emi`

## 📦 多版本下载（按你的 Minecraft 版本选）

每个版本一个 jar，文件名末尾 `mc<版本>` 就是目标版本。到 [Releases](https://github.com/1xjpy/fake-player-backpack-detection/releases) 下载对应文件放入 `mods` 即可。

| Minecraft 版本 | 文件名后缀 | 映射 | Java |
|---|---|---|---|
| 26.1.2 / 26.2 | `-mc26.1.2.jar` / `-mc26.2.jar` | Mojang 官方名 | 25 |
| 1.21 | `-mc1.21.jar` | Yarn | 21 |
| 1.21.1 | `-mc1.21.1.jar` | Yarn | 21 |
| 1.21.2 | `-mc1.21.2.jar` | Yarn | 21 |
| 1.21.3 | `-mc1.21.3.jar` | Yarn | 21 |
| 1.21.4 | `-mc1.21.4.jar` | Yarn | 21 |
| 1.21.5 | `-mc1.21.5.jar` | Yarn | 21 |
| 1.21.6 | `-mc1.21.6.jar` | Yarn | 21 |
| 1.21.7 | `-mc1.21.7.jar` | Yarn | 21 |
| 1.21.8 | `-mc1.21.8.jar` | Yarn | 21 |
| 1.21.9 | `-mc1.21.9.jar` | Yarn | 21 |
| 1.21.10 | `-mc1.21.10.jar` | Yarn | 21 |
| 1.21.11 | `-mc1.21.11.jar` | Yarn | 21 |

> 举例：你是 1.21.4，就下 `fake-player-inspector-1.0.2-mc1.21.4.jar`。
## 功能

- **进游戏自动读取**：进入世界自动扫描 `players\data\*.dat`，读取所有历史假人背包。
- **离线/历史假人**：即使假人不在线也能查询到它的背包，并显示假人真名。
- **在线实时**：假人在线时每 20 tick 更新背包，变化即推给客户端。
- **潜影盒递归**：背包里的潜影盒/束袋容器内容会被递归统计进来。
- **只显示名字**：假人用名字显示，不再显示 UUID；无法还原的名字显示为「未命名假人」。
- **JEI / EMI / REI 兼容**：在物品上**悬停**即可看到「哪些假人持有、各有多少」，走物品 `getTooltipLines` 管线，三个模组通吃。
- **假人行为记录**：监听假人 `spawn` / `kill`，记录到 `config\fake-player-inspector-events.log`。

## 指令

```
/fpi                          查看假人列表与当前状态
/fpi list                     列出所有假人（状态、最近行为、下线存档路径）
/fpi <假人名>                 查询某个假人的背包
/fpi auto true/false          后台读取开关
/fpi display true/false       悬停显示假人背包信息开关
```

> 说明：`/fpi display` 默认关闭，只有开启后才会在物品 tooltip 上显示假人持有信息。

## 构建

源码按版本拆成多个构建目录（`work/fake-player-inspector-*`）。以 26.1.2 为例，需要 Java 25 与 Gradle：

```powershell
$env:JAVA_HOME='<你的 Java 25 路径>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
gradle build
```

构建产物在 `build\libs\fake-player-inspector-1.0.2.jar`。不同版本请用对应的 Yarn/Mojang 映射与 Gradle 配置。

## 数据文件

写入 `config\` 目录：

- `fake-player-inspector.json`：假人背包缓存
- `fake-player-inspector-names.json`：UUID -> 真名映射
- `fake-player-inspector-events.log`：假人出现/消失行为日志
- `fake-player-inspector-fake.json`：确认过的假人 UUID 列表
- `fake-player-inspector-real.json`：记录过的真人 UUID 列表（用于排除真人）

## License

MIT
