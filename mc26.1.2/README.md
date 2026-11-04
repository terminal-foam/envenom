# Envenom 1.1.4（Minecraft 26.1.2）

这是 Envenom 1.1.4 的 **26.1.2** 端口，独立于仓库根目录的 1.21.1 工程。

- Minecraft：26.1.2
- NeoForge：26.1.2.122+
- ModDevGradle：2.0.148
- Java：25
- 模组版本：`1.1.4`
- jar 名：`envenom-1.1.4+mc26.1.2.jar`

## 构建

```bash
./gradlew build
./gradlew runGameTestServer
```

离线脚本：`.\build-26.1.2.ps1 -Clean -Verify`（需要 JDK 25 和 `libs-26.1.2/`，见根目录 README）。

## 与 1.21.1 的必要差异

- 剑和工具不再有 `SwordItem` / `DiggerItem`：剑刃涂层认 `#minecraft:swords`、斧、镐、锹、锄、三叉戟，并包含 26.1.2 新增的 **矛 `#minecraft:spears`**；仍排除弓、弩、重锤。
- 盔甲不再有 `ArmorItem`：涂层认 `equippable` 组件里的人形盔甲槽。
- 食物判定使用吃/喝动画和 `food` 组件。
- 中毒伤害类型使用 `neoforge:poison`。
- 药包染色走物品定义里的 `envenom:potion_pack` 色调。
- 伤害结算使用 `hurtServer`；易伤仍乘在护甲/抗性之前的 incoming 伤害上。
- 矛冲刺的涂层强度按「举矛阶段」缩放（`KINETIC_WEAPON`：已举 tick − 前摇）。

## 测试

`runGameTestServer` 只跑 `envenom:*`；无头测试实例在 `data/envenom/test_instance/`，
测试函数由 `BuiltinTestFunctionsMixin` 在原版注册表冻结前写入。
