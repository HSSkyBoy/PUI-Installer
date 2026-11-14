# Installer-PUI

我也一样懒，所以下面的内容都是 哈基米-氢 写的

基于 **PUI 设计系统**重写的 Android 原生系统包安装器，用于替换 ColorOS 15–17、LineageOS 22–24 及原生 Android 15+ 上的 `com.android.packageinstaller`（AOSP PackageInstaller）。

界面全部采用 Jetpack Compose 与 PUI 组件库构建（灰底白卡、非凡洋红主色、20dp 圆角、悬浮胶囊导航栏）；底层基于原生 `PackageInstaller` 会话机制，具备系统级安装器特权（支持静默安装与静默卸载）。

```
PUIInstaller/
├── composeApp/src/commonMain/kotlin/com/panl/pui/    # PUI 视觉组件库
├── composeApp/src/androidMain/
│   ├── kotlin/com/panl/installer/
│   │   ├── core/          # APK 解析、签名识别、安装引擎、设置、通知
│   │   ├── i18n/          # 多语言支持（简/繁/粤、英、日、法、韩、德、俄等）
│   │   └── ui/            # 首页 / 安装页 / 卸载页 / 进阶设置 / 权限与能力检测
│   └── res/               # 自适应图标（含 -night 深色渐变底）、主题资源
├── magisk/                # Magisk / KernelSU / APatch 刷写模块源码
└── tools/                 # 构建、签名与模块打包脚本
```

## 一、核心功能

### 1. 桌面图标与拨号盘快捷唤起

* 默认提供完整的桌面入口图标。
* 支持在「进阶设置」中开启**隐藏桌面图标**；隐藏后可随时在系统拨号盘输入 **`*#*#078#*#*`** 快速唤起安装器。

### 2. 版本号对比与状态识别

* 数据源自 `ApkInspector`：通过 `getPackageArchiveInfo` 读取目标 APK 的 `versionName` 与 `versionCode`，并与 `getPackageInfo` 获取的已安装版本进行比对，自动识别 **`NEW`（全新安装） / `UPDATE`（更新） / `DOWNGRADE`（降级） / `REINSTALL`（重装）** 四种状态。
* 未安装时左侧显示「未安装」；检测到降级时整卡切换为错误警示色，并引导前往设置开启「允许降级安装」。
* 已安装应用列表（应用页）同样支持逐行展示当前版本号。

### 3. 浮窗安装界面（背景模糊）

可在 **设置 → 安装界面** 中自定义展示形态：

| 配置项 | 可选值 | 说明 |
| --- | --- | --- |
| 界面样式 | 浮窗 / 全屏 | 浮窗为底部或居中弹出的卡片，保留底层界面并施加系统级背景模糊；全屏为完整页面布局 |
| 浮窗位置 | 居中 / 底部 | 贴底时仅顶部两角圆角；居中时四角全圆，四周保留 14dp 边距 |

**实现要点（避坑记录）：**

* **清单声明半透明主题**：`ActivityManager` 在启动任务时便根据 `AndroidManifest.xml` 中的主题判断窗口是否 `translucent`，在 `onCreate` 中动态调用 `setTheme()` 为时已晚（会导致底层应用被隐藏、背景全黑）。因此 `InstallActivity` 在清单中默认绑定 `Theme.PuiInstaller.Sheet`，若用户配置为全屏模式，再于 `onCreate` 中切换回不透明的 `Theme.PuiInstaller`。
* **系统级背景模糊**：通过 `FLAG_BLUR_BEHIND` 配合 `LayoutParams.setBlurBehindRadius(26dp)` 实现，依赖系统属性 `ro.surface_flinger.supports_background_blur=1`；若设备不支持，则自动降级为 18% 不透明度的黑色遮罩。
* **状态机复用与热切换**：浮窗与全屏共用同一套 `InstallController` 状态机，仅容器与信息密度不同（浮窗仅保留「AOSP 公开密钥」等高危风险提示，完整证书详情在全屏版展示）。点击浮窗头部「⋯」可快速切换样式与位置，切换样式时会触发 `recreate()` 以重建窗口属性。

### 4. 实时活动（Live Updates）与 ColorOS 流体云

安装过程中会推送一条**进度型实时活动通知**，获系统提升后将常驻通知栏顶部，并在状态栏显示胶囊形态：

| 维度 | 实现细节 |
| --- | --- |
| 权限声明 | 清单声明 `POST_PROMOTED_NOTIFICATIONS`（普通权限，安装即自动授予） |
| 通知样式 | `Notification.ProgressStyle` 分两段展示进度（复制阶段 30% / 安装阶段 70%），未知进度时启用 `setProgressIndeterminate(true)` |
| 提升条件 | 请求 `requestPromotedOngoing`，并满足 `ongoing`、包含 `contentTitle`、无自定义 `RemoteViews`、通知渠道级别高于 `MIN` |
| 胶囊文案 | 通过 `setShortCriticalText` 设置：有明确进度时显示百分比（如 `86%`），不确定时显示「安装中」 |
| 通知渠道 | 实时进度走专用 `install_live`（`IMPORTANCE_DEFAULT`）；安装结果走 `install_status`（`IMPORTANCE_LOW`，阅后即走） |
| 功能开关 | 设置 → 通知 → 安装进度实时活动 |

在 ColorOS 17（Android 17）上的实测日志：

```text
Notification(channel=install_live ... flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING)

```

出现 `PROMOTED_ONGOING` 标志即表明系统已将其识别为实时活动。

> **关于 API 兼容与流体云适配：**
> * `Notification.Builder#setRequestPromotedOngoing()` 在 API 37 才纳入公开 SDK，由于本项目 `compileSdk` 为 36，目前通过直接写入 Extra 实现：
> ```kotlin
> builder.addExtras(Bundle().apply {
>     putBoolean("android.requestPromotedOngoing", true) // 等同于 EXTRA_REQUEST_PROMOTED_ONGOING
> })
> 
> ```
> 
> 
> * ColorOS 16 起，**流体云直接兼容 Android 16 的标准 Live Updates 规范**，无需接入 OPPO 私有 SDK。若个别机型未能正常上云，通常是系统侧对包名做了白名单限制，代码层面无需额外修改。
> 
> 

### 5. 安装后自动清理安装包

* 可在 **设置 → 安装 → 安装后删除安装包** 中开启（提供「删除前确认」二级开关）。
* 安装成功后优先清理内部暂存副本，随后按配置清理源文件；若源 URI 为 `content://`，则依次尝试 `ContentResolver.delete` → `DocumentsContract.deleteDocument` → 解析真实路径删除。
* 结果页会明确反馈「已删除安装包」或「安装包未能删除（没有写入权限）」；若关闭自动清理，结果页仍会提供手动「删除安装包」按钮。
* 应用已声明 `MANAGE_EXTERNAL_STORAGE`（设置页提供「文件访问权限」快捷入口），以支持直接读取并清理公共目录下的 `file://` 安装包。

### 6. AOSP 签名检测与 `SHOW_APP_INFO` 接管

**签名检测（`core/ApkSignature.kt`）：**

* 解析 APK 的 v2/v3 签名块与 X.509 证书，提取主题（Subject）、颁发者（Issuer）、SHA-256 / SHA-1 指纹、签名算法及有效期。
* 内置 AOSP 四把公开密钥指纹库，命中即触发风险标记：

| 密钥名称 | SHA-256 指纹 |
| --- | --- |
| `testkey` | `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc` |
| `platform` | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| `shared` | `28bbfe4a7b97e74681dc55c2fbb6ccb8d6c74963733f6a46ae74d8c3a6e879fd` |
| `media` | `465983f7791f2abeb43ea2cbdc7f21a8260b72bc08a55c839fc1a43bc741a81e` |

* 支持识别 Android 调试签名（`CN=Android Debug`）。命中风险签名时，安装页会弹出红色警告卡片，点击可进入独立的「签名检测」页查看完整签名者信息。

**应用信息与卸载接管（`ui/UninstallActivity.kt`）：**

```xml
<intent-filter>
    <action android:name="android.intent.action.SHOW_APP_INFO" />
    <data android:scheme="package" />
</intent-filter>

```

在 LibChecker 等工具中点击应用图标并选择本安装器，即可直达「应用信息 + 卸载」聚合页。该页面同时接管 `ACTION_DELETE`、`ACTION_UNINSTALL_PACKAGE` 与 `APPLICATION_DETAILS_SETTINGS`。

### 7. 注册应用商店（Registered App Store）能力自检

参照 [Android 官方 Registered App Stores 规范](https://developer.android.com/registered-app-stores/overview)，完成了代码侧的全部契约实现：

* 声明 `android.permission.INSTALL_PACKAGES_AS_REGISTERED_APP_STORE` 与 `android.permission.REQUEST_INSTALL_PACKAGES`；
* 完整接管安装、卸载与应用信息三类必需 Intent；
* 安装会话设置 `SessionParams.USER_ACTION_NOT_REQUIRED`（Android 16+ 一键安装语义）；
* 安装页完整披露应用名称、开发者（签名者）、版本号、体积及权限列表，满足 RAS 对信息透明度的要求；
* 提供「应用商店能力」页（`ui/AppStoreActivity.kt`），支持 9 项代码侧自检与注册流程说明。

> 注：RAS 正式资质需通过 Google 官方企业审核、年费及 MASA 安全认证，普通签名下系统会忽略一键安装标志。

### 8. 外观：实时模糊与毛玻璃调节

在 **设置 → 外观 / 导航栏** 中提供实时渲染参数调节（即时生效并持久化）：

| 控件 | 调节范围 | 作用 |
| --- | --- | --- |
| 顶部模糊与毛玻璃 | 开关 | 控制整屏是否挂载 AGSL 逐像素采样；关闭可显著降低 GPU 负载 |
| 顶部模糊深度 | 2–100 px | 控制顶部渐进模糊的最大半径 |
| 卡片展开模糊 | 0–120 px | 弹出 PUI 对话框卡片时，底层页面的动态模糊半径 |
| 导航栏 · 模糊半径 | 2–64 px | 悬浮胶囊导航栏的毛玻璃模糊半径 |
| 导航栏 · 背景不透明度 | 0–100 % | 胶囊玻璃底色的 Alpha 透明度（0% 为全透明） |

渲染管线沿用 PUI 双路径设计：顶部渐进模糊与悬浮胶囊在**同一个 AGSL 着色器**中合并计算（`puiProgressiveTopBlur`），对话框背景模糊则通过 `RenderEffect` 驱动（`puiRealtimeBlur`，支持 0→1 进度动画）。

---

## 二、包名与系统特权原理

系统原生安装器位于 `/system_ext/priv-app/PackageInstaller/`，使用 **AOSP `testkey**` 签名。本项目保持包名 `com.android.packageinstaller` 与其一致，从而获得以下系统级能力：

1. **覆盖升级保留特权**：可直接作为系统应用的更新版本覆盖安装（`UPDATED_SYSTEM_APP`），完整保留 `PRIVILEGED` 标志；
2. **静默安装与卸载**：持有 `INSTALL_PACKAGES` 与 `DELETE_PACKAGES` 权限，无需弹窗二次确认即可完成静默安装与卸载；
3. **无痛模块替换**：可通过 Magisk / KernelSU / APatch 模块直接挂载替换系统分区的原版 APK。

---

## 三、版本号编码规则

对外展示的 `versionName` 从 **`2.0.0`** 起迭代（如 `2.0.1`、`2.1.0`）。

由于系统镜像中已预装原版安装器（模块预设为 `17700004`），PackageManagerService 会拦截任何低于预装版本的覆盖安装（报错 `System app cannot be downgraded to older than its preloaded version`）。因此 `versionCode` 必须保留 `17700000` 前缀：

```text
versionCode = 17700000 + major * 100 + minor * 10 + patch

示例：
2.0.0 → 17700200
2.0.1 → 17700201
2.1.0 → 17700210

```

---

## 四、构建与部署

### 1. 编译与打包

需要 **JDK 17+** 环境，并在 `local.properties` 中正确配置 Android SDK 路径：

```bash
# 1. 编译、对齐并使用 AOSP testkey 签名（v1 + v2 + v3）
./tools/build.sh
# 产物：out/PUIInstaller-<versionName>-<versionCode>.apk

# 2. 打包 Magisk / KernelSU / APatch 刷写模块
python tools/build-module.py
# 产物：out/PUIInstaller-<versionName>-magisk.zip

```

> ⚠️ 构建脚本使用的 `keys/testkey.pk8` 与 `keys/testkey.x509.pem` 源自 AOSP 公开测试密钥，**切勿将其用于任何对外公开发布的商业或个人应用签名**。

### 2. 部署到设备

**方式 A：ADB 覆盖安装（推荐用于日常调试）**

```bash
adb install -r out/PUIInstaller-2.0.0-17700200.apk

```

系统会将其识别为原生安装器的官方更新，直接授予并保留 `PRIVILEGED` 特权。

**方式 B：Root 模块挂载**
直接在 Magisk / KernelSU / APatch 管理器中刷入生成的 `PUIInstaller-<versionName>-magisk.zip` 并重启。模块会通过 Magic Mount 将 APK 挂载至系统分区：

```text
/data/adb/modules/PUIAOSPPackageInstall/
└── system/system_ext/priv-app/PackageInstaller/PackageInstaller.apk

```

---

## 五、代码地图

| 功能模块 | 核心文件 |
| --- | --- |
| APK 信息解析（图标/标签/版本/权限） | `core/ApkInspector.kt` |
| 证书解析与 AOSP 公开密钥识别 | `core/ApkSignature.kt` |
| 安装 / 卸载会话调度引擎 | `core/InstallEngine.kt` |
| 安装包自动清理逻辑 | `core/InstallEngine.kt`（`SourceCleaner`） |
| 结果广播接收与实时活动通知 | `core/InstallResultReceiver.kt`、`core/UninstallResultReceiver.kt`、`core/Notifications.kt` |
| 安装页状态机 | `ui/install/InstallController.kt` |
| 安装页 UI（浮窗 / 全屏） | `ui/install/InstallScreen.kt` |
| 卸载与应用详情聚合页 | `ui/uninstall/UninstallScreen.kt` |
| 首页 / 应用列表 / 设置项 | `ui/main/*.kt` |
| 独立签名详情检测页 | `ui/SignatureActivity.kt` |
| 注册应用商店能力自检页 | `ui/AppStoreActivity.kt` |
| 通用 PUI 卡片与图标组件 | `ui/components/Widgets.kt` |

---

## 六、已知边界与限制

* **Intent Filter 必须同时兼容 `mimeType**`：部分第三方应用（如酷安）在发起 `ACTION_INSTALL_PACKAGE` 时会附带 `type=application/vnd.android.package-archive`。若清单仅声明 `<data android:scheme="content"/>` 将无法匹配并抛出 `ActivityNotFoundException`（表现为点击安装无响应）。因此清单中同时保留了「`scheme + mimeType`」与「仅 `scheme`」两组过滤器。
* **应用归档恢复（Unarchive）**：AOSP 的 `UNARCHIVE_DIALOG` 暂未接管，仍由原版系统组件处理；若彻底替换了原版 APK，恢复已归档应用时可能会提示找不到处理程序。
* **降级安装依赖反射**：`SessionParams.setRequestDowngrade` 属于 `@SystemApi`，代码通过反射调用；若受系统 Hidden API 策略限制，安装时将返回 `INSTALL_FAILED_VERSION_DOWNGRADE`。
* **非系统身份运行降级**：若未以系统特权身份安装（例如修改了包名作为普通应用安装），应用将失去 `INSTALL_PACKAGES` 权限，提交安装会话后会自动将系统的安装确认 Intent 原样转交由用户手动确认。
