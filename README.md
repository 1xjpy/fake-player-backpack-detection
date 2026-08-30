# Fake Player Backpack Inspector

> 假人背包检测 · 背包容量查看 · 假人背包检查

一个给 Minecraft Java 版用的 Fabric 客户端/服务端模组，用来**查看地毯模组（Carpet）假人的名字与背包内容**。

## 兼容性

- Minecraft：`26.1.2`（Fabric，未混淆，Mojang 官方类名）
- Fabric Loader：`>=0.19.0`
- 前置：`fabric-api`
- 建议（可选）：`carpet`、`jei` / `roughlyenoughitems` / `emi`

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

需要 Java 25 与 Gradle：

```powershell
$env:JAVA_HOME='<你的 Java 25 路径>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
gradle build
```

构建产物在 `build\libs\fake-player-inspector-1.0.0.jar`。

## 数据文件

写入 `config\` 目录：

- `fake-player-inspector.json`：假人背包缓存
- `fake-player-inspector-names.json`：UUID -> 真名映射
- `fake-player-inspector-events.log`：假人出现/消失行为日志
- `fake-player-inspector-fake.json`：确认过的假人 UUID 列表
- `fake-player-inspector-real.json`：记录过的真人 UUID 列表（用于排除真人）

## License

MIT
