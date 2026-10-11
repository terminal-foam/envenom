<div align="center">

# Envenom

**给武器与盔甲涂药水**

把一瓶药水涂在装备上，之后触发时释放对应效果。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3fb950?style=flat-square)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.248+-e8a33d?style=flat-square)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-ed8b00?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-GPL--3.0%20%2B%20非商用-blue?style=flat-square)](LICENSE)

[**下载最新版**](../../releases/latest) · [玩法说明](#玩法) · [附魔](#五个附魔) · [从源码构建](#从源码构建)

</div>

---

## 这是什么

原版的药水只能喝、只能砸。**Envenom 让你把它涂在装备上**，分成三个槽位，各自在不同的时机释放：

| 槽位 | 涂在哪 | 什么时候生效 | 冷却 |
|---|---|---|---|
| 🗡️ **剑刃** | 武器 | **打中敌人时** | 跟随武器攻击冷却 |
| 🪄 **剑柄** | 武器 | **自己身上** | 30 秒 |
| 🛡️ **盔甲** | 盔甲 | **自己身上** | 瞬间效果 0.5 秒<br>其余 30 秒 |

于是"中毒剑"、"瞬回胸甲"、"缓速锤"这类玩法都成立了。

---

## 环境要求

| 项 | 版本 |
|---|---|
| **Minecraft** | 1.21.1 |
| **NeoForge** | 21.1.248 或更高 |
| **Java** | 21 |

---

## 安装

1. 到 [Releases](../../releases/latest) 下载 `envenom-x.y.z.jar`
2. 放进**游戏实例**的 `mods/` 文件夹
3. 启动游戏

> ⚠️ **如果你开了"版本隔离"**（PCL / HMCL / 官方启动器的版本隔离等），路径是
> `​.minecraft/versions/<版本名>/mods/`，**不是** `​.minecraft/mods/`。
> 放错地方模组**完全不会加载**，而且不会有任何报错。

---

## 玩法

### 涂涂层

```
副手拿药水  →  主手拿装备  →  右键
```

涂完物品的 tooltip 上会显示涂层：**效果 · 槽位 · 等级 · 剩余次数**。

### 洗掉涂层

| 清洗物 | 效果 |
|---|---|
| 💧 **水瓶** | 去掉**一层** |
| 🥛 **牛奶桶** | 去掉**全部** |
| 🍯 **蜂蜜瓶** | 只去掉**负面**效果 |

> 牛奶桶不消耗，水瓶与蜂蜜瓶会变成玻璃瓶。

### 可涂的物品

- 剑 / 镐 / 斧 / 锹 / 锄 / 三叉戟（原版武器与工具）
- 全部**人形护甲**（头 / 胸 / 腿 / 脚）
- 鞘翅、雕刻南瓜

> 马铠、狼铠这类**动物铠甲涂不上去** —— 它们穿不到人形护甲槽上，涂了也不会触发。

### 涂层次数

取决于药水类型（可在配置里改）：

| 药水 | 默认次数 |
|---|---|
| 饮用型 | **300** |
| 喷溅型 | **210** |
| 滞留型 | **210** |

**滞留型**额外好处：剑刃涂层每次触发会**连算 3 次**（相当于 ×3 伤害/时长）。

---

## 五个附魔

全部通过数据包 JSON 定义（`data/envenom/enchantment/`），**任何整合包都能改**。

| 附魔 | 等级 | 权重 | 效果 |
|---|---|---|---|
| **粘滞** `sticky` | I–III | 10 | 有概率**不消耗**涂层次数 |
| **药水增幅** `amplification` | I–V | 10 | 有概率**提高**施加的效果等级 |
| **叠层** `stacking` | I–III | 8 | 攻击时把目标身上已有的**负面效果顶高一级** |
| **注射** `injection` | I | 5 | 让效果能作用于**硬编码拒绝效果**的生物 |
| **薛定谔的祝福** `schrodingers_blessing` | I | 1 | 有概率把效果**替换成随机的另一种** |

> **哪些槽位吃附魔？**
> **只有剑刃吃全部五个。** 剑柄与盔甲**都不吃附魔** —— 这是刻意设计（附魔只强化"打敌人"的那条路）。

### 注射附魔是干什么的

原版有两个生物在自己的类里**无条件覆写**了 `addEffect` 让它直接返回 `false`：

```
末影龙（EnderDragon）   EnderDragon.java:946
凋灵  （WitherBoss）    WitherBoss.java:530
```

这种拒绝**不来自免疫机制** —— 它们的 `canBeAffected` 仍然返回 `true`，所以查免疫查不出来。
**注射附魔是唯一能绕过去的办法。**

> **为什么注射只写"专攻末影龙"？**
> 凋灵虽然也硬编码拒绝，但它是**亡灵** —— 而亡灵有另一个通用弱点：
> "瞬间治疗造成伤害"。所以凋灵吃**瞬间治疗**涂层就能被打得很痛，**不需要注射**。
> 注射排除了亡灵，专注解决"既硬编码拒绝、又不是亡灵"的末影龙。

> **对凋灵请涂「瞬间治疗」，对末影龙请涂「瞬间伤害」** —— 两者的方向是**反的**。

---

## 药水效果改造

本模组还改了**两个**原版效果的高等级行为：

### 中毒

| 等级 | 行为 |
|---|---|
| I – II | **原版逻辑** |
| **III 起** | 触发间隔**固定 10 tick** |
| III – IV | 单次伤害按原版 DPS 连续（1.667 / 3.333） |
| **V 起** | 单次伤害**线性递增**：`10 + 2.4 × (等级-5)` |
| 全部 | **不致死** —— 最高扣到剩 1 血 |

### 再生

| 等级 | 行为 |
|---|---|
| I – IV | **原版逻辑** |
| **V 起** | **每 tick** 回血，回复量 = `2^等级 / 50` |

> 两者都用 `@Inject` 实现（不是 `@Overwrite`），**尽量不与其它模组的 mixin 冲突**。

---

## 授权

**GPL-3.0 + 附加非商用条款** —— 详见 [LICENSE](LICENSE)。

| | |
|---|---|
| ✅ | 游玩、查看源码、修改、重新发布 |
| ✅ | **打包进免费整合包**（无需授权、无需通知） |
| ✅ | 接受自愿捐赠 |
| 📖 | **改了再发布 → 必须同样开源** |
| ❌ | **禁止任何商业用途**：售卖 / 付费下载 / 付费服务器 / 广告分成 / 捆绑销售 |

> 严格说这不是一个 SPDX 标准协议 —— GPL-3.0 本身允许商用，作者作为版权持有人附加了非商用限制。
> 若你需要纯 GPL-3.0 授权（允许商用），请开 Issue 联系。

---

## 从源码构建

需要 **JDK 21**。

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

产物在 `build/libs/envenom-<版本>.jar`。

**离线构建**（依赖已缓存时）：加 `--offline`。

<details>
<summary>Windows 一键脚本</summary>

仓库自带 `build.ps1`（固定 JDK21 与 Gradle 缓存，默认离线）：

```powershell
.\build.ps1          # 构建
.\build.ps1 -Clean   # 清理后构建
.\build.ps1 -Online  # 允许联网（首次预热依赖用）
```

</details>

---

## 项目结构

```
src/main/java/com/terminalfoam/envenom/
├── Envenom.java                    模组入口
├── coating/
│   ├── Coating.java                涂层数据（效果/等级/时长/次数/槽位）
│   ├── CoatingSlot.java            三个槽位的枚举
│   └── EnvenomDataComponents.java  物品数据组件（涂层、冷却）
├── config/EnvenomConfig.java       配置（涂层次数）
├── enchantment/
│   └── EnvenomEnchantments.java    附魔引用（数据包 JSON 注册）
├── handler/
│   ├── CoatingApplicationHandler.java  涂药 / 清洗
│   ├── CoatingEffectHandler.java       触发管线（核心）
│   ├── CoatingTooltipHandler.java      tooltip 显示
│   ├── TriggerCooldown.java            冷却读写
│   ├── EffectOutcome.java              效果合并判定
│   └── InjectionHandler.java           注射附魔
└── mixin/
    ├── PoisonMixin.java            中毒改造
    ├── RegenerationMixin.java      再生改造
    └── EnderDragonHurtMixin.java   让无来源魔法伤害能打到末影龙

src/main/resources/
├── data/envenom/enchantment/       五个附魔的 JSON 定义
├── data/envenom/tags/item/         可涂物品标签
└── assets/envenom/lang/            中英文本地化
```

---

## 反馈

- 🐛 **Bug / 崩溃** → 开 [Issue](../../issues)，**请附上 `logs/latest.log`**
- 💡 **建议 / 平衡性** → 也开 Issue
- 🔧 **想改？** Fork 之后提 PR —— 记得按 LICENSE 同样开源

---

<div align="center">

**Enjoy.**
</div>
