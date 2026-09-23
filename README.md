# 保单管家 PolicyKeeper

一款**纯本地**的 Android 保单记录 App。车险可以管好几辆车，人身险可以管一家人，
每份保单支持「原价 − 返款 = 实际净保费」的记账口径，并逐年对比同一辆车的保费变化。

> 所有数据只保存在手机本地，不联网、不上报（仅在手动点击「检查更新」时访问 GitHub 查询版本）。

## 功能

- **投保对象管理**：车辆（车牌号 + 车型）与人员（姓名 + 与本人关系）
- **保费三层口径**：原价（实付金额）− 返款（优惠返点）= 实际净保费
- **返款状态**：区分「待收 / 已收」，首页汇总待收返款
- **续保提醒**：车险提前 90 天进入「可续保」，人身险到期日当天才提醒
- **状态机**：生效中 / 可续保 / 今天到期 / 未生效 / 已过期 / 已续保，全 App 单一入口判定
- **逐年对比**：同一辆车或同一个人，历年的保费涨跌一目了然
- **统计图表**：历年支出趋势、险种构成、按投保对象汇总；可切换「净保费 / 原价」口径
- **备份与导出**：`.pkbak` 完整备份（含附件）与 CSV 明细导出
- **应用内更新**：从 GitHub Releases 检查新版本并直接下载安装

## 下载安装

到 [Releases](../../releases) 页面下载最新 `policykeeper-vX.Y.apk`，在手机上允许「安装未知应用」后安装即可。

首次安装后，后续可以在 App 的 **设置 → 检查更新** 里直接升级。

## 自行构建

### 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 34 |
| Build Tools | 34.0.0 |
| Gradle | 8.7（仓库自带 Wrapper） |

### 命令行构建

```bash
# Debug 包，可直接安装
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release 包（需要先配置签名，见下一节）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

Windows 下用 `gradlew.bat`。若 SDK 不在默认位置，在项目根目录新建 `local.properties`：

```properties
sdk.dir=C:/path/to/your/android-sdk
```

也可以直接用 Android Studio 打开本目录构建。

### 签名配置

签名密钥与口令**不入库**。首次构建 Release 包前，先准备两样东西：

1. 把密钥库放到 `keystore/policykeeper.jks`（没有的话用下面的命令生成）

   ```bash
   keytool -genkeypair -v -keystore keystore/policykeeper.jks \
     -alias policykeeper -keyalg RSA -keysize 2048 -validity 10000
   ```

2. 复制 `keystore.properties.example` 为 `keystore.properties`，填入真实口令：

   ```properties
   storeFile=keystore/policykeeper.jks
   storePassword=你的密钥库口令
   keyAlias=policykeeper
   keyPassword=你的密钥口令
   ```

   > 这两项都写在 `.gitignore` 里，不会被提交。
   > 缺少 `keystore.properties` 时，Release 变体会构建成**未签名**包，但 Debug 构建不受影响。

## 自动构建与发版

推送代码后 GitHub Actions 会自动编译，产物在仓库的 **Actions → 构建 APK → Artifacts** 里下载。

要发布一个 App 内可检测到的新版本：

1. 修改 `app/build.gradle.kts` 里的 `versionCode`（必须递增）与 `versionName`
2. 提交并推送
3. 打 tag 并推送，CI 会自动编译、签名、创建 Release 并附上 APK

   ```bash
   git tag v1.6
   git push origin v1.6
   ```

发版前需要在仓库 **Settings → Secrets and variables → Actions** 里配置 4 个 Secret，
用于在 CI 中还原签名密钥（因为密钥文件不随代码入库）：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | 密钥库文件的 Base64 文本 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥口令 |

`KEYSTORE_BASE64` 的生成方式：

```bash
# macOS / Linux
base64 -i keystore/policykeeper.jks | pbcopy        # macOS 直接进剪贴板
base64 keystore/policykeeper.jks > keystore.b64     # Linux
```

Windows PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\policykeeper.jks")) | Set-Clipboard
```

## 应用内更新的配置

App 通过 GitHub Releases API 查询最新版本，需要知道仓库地址。有两种方式：

**方式一（推荐）**：在 `gradle.properties` 中取消注释并填写

```properties
policykeeper.github.owner=你的GitHub用户名
policykeeper.github.repo=PolicyKeeper
```

**方式二**：直接改 `app/build.gradle.kts` 中 `defaultConfig` 里的默认值。

未配置时点击「检查更新」会提示「尚未配置更新源」，不会发起任何网络请求。

更新流程：检查版本 → 弹出新版本说明 → 下载到缓存目录 → 交给系统安装器。
Android 8.0 及以上需要用户授权「安装未知应用」，App 会引导跳转到对应设置页。

## 数据与隐私

- 数据以单文件 JSON 存放在应用私有目录（`files/data.json`），附件在 `files/attachments/`
- App 不含任何埋点、统计或第三方 SDK
- 网络权限仅用于「检查更新」，且只在你主动点击时发起
- 换手机前请在 **设置 → 导出备份** 保存 `.pkbak` 文件

## 项目结构

```
app/src/main/java/com/baodan/keeper/
├── model/Models.kt          # 数据模型与保单状态枚举
├── data/Store.kt            # 单例数据仓库：唯一状态入口、排序、统计、导入导出
├── ui/
│   ├── home/                # 总览：年度净保费、待续保（车/人分组）
│   ├── policy/              # 保单列表、编辑表单、状态与配色映射
│   ├── stats/               # 统计图表、同一对象逐年对比
│   ├── insured/             # 投保对象管理（底部表单）
│   └── settings/            # 备份恢复、CSV 导出、检查更新
├── util/Updater.kt          # GitHub Releases 更新检查与下载安装
├── util/Utils.kt            # 日期与金额格式化
└── widget/BarChartView.kt   # 自绘柱状图
```

## 技术栈

Kotlin + XML 布局 + ViewBinding，Material 3 设计语言。

刻意**不引入** Room / KSP / Hilt / Compose，全部依赖都走 Java 注解处理器之外的路径，
以保证克隆下来就能稳定构建、零注解处理配置成本。

## 设计说明

界面遵循一套集中定义的 token 体系（`res/values/colors.xml`、`themes.xml`）：

- 色彩分四层：品牌层 / 中性层 / 文字三级层级 / 状态层（每个状态色配同色系浅底）
- 圆角两级：卡片 18dp、控件 14dp，不混用
- 保单状态的文案与配色统一由 `PolicyStatusUi` 映射，禁止在页面内硬编码

## 许可

暂未指定开源协议。
