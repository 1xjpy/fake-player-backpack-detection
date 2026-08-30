# 26.1.2 构建说明

## 目标

为 **Minecraft 26.1.2** 编译 Fake Player Backpack Detection（假人背包检测）模组。

## 当前状态（2026-08-30）

**阻塞：Fabric / Mojang 尚未发布 26.1.2 的代码映射，暂时无法编译出可运行的 jar。**

已确认的事实：

- Minecraft 26.1.2 存在（Mojang 版本清单）。
- Fabric API `0.155.2+26.1.2` 已发布。
- Yarn 映射：26.1.2 / 26.1.1 / 26.1 / 26.2 **均无**（Fabric 官方未发布）。
- 官方 Mojang 映射：Loom `1.18.0-alpha.19` 报
  `Failed to find official mojang mappings for 26.1.2`（Mojang 未放出映射）。

## 工具链（已配好）

- Gradle 9.7.0（`work/tools/gradle-9.7.0`）
- Loom 1.18.0-alpha.19
- Java 25（HMCL `mojang-java-runtime-epsilon`，即
  `C:\Users\我自己、\AppData\Roaming\.hmcl\java\windows-x86_64\mojang-java-runtime-epsilon`）
- 构建时需设置 `JAVA_HOME` 指向上面 Java 25（见下方命令）

## 等映射发布后如何编译

在 `work/fake-player-inspector` 下执行：

```powershell
$env:JAVA_HOME='C:\Users\我自己、\AppData\Roaming\.hmcl\java\windows-x86_64\mojang-java-runtime-epsilon'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
C:\Users\我自己、\Documents\Codex\2026-08-30\n\work\tools\gradle-9.7.0\bin\gradle.bat build --refresh-dependencies
```

产物：`build/libs/fake-player-inspector-1.0.0.jar`

## 切回其它版本（临时方案）

如果 26.1.2 映射一直不出，可改用你环境里映射可用的版本。以 **1.21.11** 为例
（Yarn `1.21.11+build.6` 已存在），需要改 `gradle.properties`：

```
minecraft_version=1.21.11
loader_version=0.19.5
loom_version=1.10.1
fabric_version=<1.21.11 对应的 fabric api>
```

并把 `build.gradle` 的 `mappings loom.officialMojangMappings()` 改回
`mappings "net.fabricmc:yarn:<1.21.11+build>:v2"`，Java 目标保持 21。
