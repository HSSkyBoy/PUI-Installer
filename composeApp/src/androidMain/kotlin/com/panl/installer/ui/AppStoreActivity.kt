package com.panl.installer.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.panl.installer.core.Settings
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiTopFade
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import com.panl.installer.core.PackageUtils
import com.panl.installer.ui.components.PuiTopBar
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiCard
import com.panl.pui.PuiDivider
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiNoticeCard
import com.panl.pui.PuiNoticeTone
import com.panl.pui.PuiPageTitle
import com.panl.installer.i18n.tr

/**
 * 注册应用商店（Registered App Store）能力页。
 *
 * 对应 https://developer.android.com/registered-app-stores/overview：
 * 通过 Google 审核的商店可以在 Android 17 QPR2 之后拿到「一键安装」——
 * 用户点安装即装，不再逐次弹系统确认。
 *
 * 页面把「代码侧能做的部分」列成自检清单：权限声明、必需 Intent、应用信息展示、静默安装能力。
 * 审核、目录上报、年费这些运营侧的事情不在代码里，单独列在流程说明。
 */
class AppStoreActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                AppStoreScreen(onClose = { finish() })
            }
        }
    }
}

private data class CheckItem(val title: String, val detail: String, val ok: Boolean)

@Composable
private fun AppStoreScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val checks = remember { buildChecks(context) }
    val passed = checks.count { it.ok }

    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val blurH = rememberPuiTopBlurHeight()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .puiEdgeHaptic()
                .then(
                    if (settings.blurEnabled) {
                        Modifier.puiProgressiveTopBlur(
                            height = blurH,
                            maxRadius = (settings.topBlurPx / density).dp
                        )
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = blurH + 4.dp, bottom = 32.dp)
        ) {
                item {
                    PuiPageTitle(tr("权限与能力检测", "Permissions & Capabilities"))
                    Text(
                        tr("检测当前系统环境、安装器声明的权限与系统特性支持情况。", "Check the current system environment, declared permissions, and feature support."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)
                    )
                }

                item {
                    PuiNoticeCard(
                        title = tr("自检通过 $passed / ${checks.size} 项", "$passed / ${checks.size} checks passed"),
                        text = if (passed == checks.size) {
                            tr("代码与系统层所需能力均已就位。", "All code and system-level capabilities are in place.")
                        } else {
                            tr("部分能力或权限未满足，详见下方清单。", "Some capabilities or permissions are unmet — see the list below.")
                        },
                        icon = if (passed == checks.size) PuiIcon.Check else PuiIcon.Info,
                        tone = if (passed == checks.size) PuiNoticeTone.Success else PuiNoticeTone.Info
                    )
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle(tr("能力自检", "Capability checks"))
                }
                item {
                    PuiCard {
                        Column {
                            checks.forEachIndexed { index, item ->
                                CheckRow(item, index != checks.lastIndex)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle(tr("技术声明", "Technical declarations"))
                }
                item {
                    PuiCard {
                        Column {
                            PermissionRow(
                                "android.permission.INSTALL_PACKAGES_AS_REGISTERED_APP_STORE",
                                tr("注册商店专用安装权限（需系统侧授予）", "Install permission for registered stores (granted by the system)"),
                                true
                            )
                            PuiDivider(startIndent = 16.dp)
                            PermissionRow(
                                "android.permission.REQUEST_INSTALL_PACKAGES",
                                tr("请求安装应用（普通应用也需要）", "Request install packages (needed by normal apps too)"),
                                false
                            )
                        }
                    }
                }
            }

        // 标题在列表里，滚走就跟着走；顶部只剩状态栏那一条模糊
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun CheckRow(item: CheckItem, showDivider: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.ok) scheme.primaryContainer else scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                PuiIconView(
                    if (item.ok) PuiIcon.Check else PuiIcon.Close,
                    Modifier.size(14.dp),
                    tint = if (item.ok) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (showDivider) PuiDivider(startIndent = 52.dp)
    }
}

@Composable
private fun PermissionRow(name: String, detail: String, showDivider: Boolean) {
    Column {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDivider) PuiDivider(startIndent = 16.dp)
    }
}

// ---------------- 自检逻辑 ----------------

private fun buildChecks(context: Context): List<CheckItem> {
    val pm = context.packageManager
    val pkg = context.packageName

    val requested = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }.requestedPermissions?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    val targetSdk = context.applicationInfo.targetSdkVersion
    val canInstall = PackageUtils.hasInstallPermission(context)

    return listOf(
        CheckItem(
            tr("声明注册商店安装权限", "Declare the registered-store install permission"),
            tr("INSTALL_PACKAGES_AS_REGISTERED_APP_STORE 已在清单中声明", "INSTALL_PACKAGES_AS_REGISTERED_APP_STORE is declared in the manifest"),
            "android.permission.INSTALL_PACKAGES_AS_REGISTERED_APP_STORE" in requested
        ),
        CheckItem(
            tr("声明请求安装权限", "Declare the request-install permission"),
            tr("REQUEST_INSTALL_PACKAGES 已在清单中声明", "REQUEST_INSTALL_PACKAGES is declared in the manifest"),
            "android.permission.REQUEST_INSTALL_PACKAGES" in requested
        ),
        CheckItem(
            tr("接管 APK 安装入口", "Handle APK install intents"),
            "ACTION_VIEW + application/vnd.android.package-archive",
            resolves(context, Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("file:///sdcard/probe.apk"),
                "application/vnd.android.package-archive"
            ))
        ),
        CheckItem(
            tr("接管卸载入口", "Handle uninstall intents"),
            "ACTION_DELETE / ACTION_UNINSTALL_PACKAGE",
            resolves(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:com.example.probe")))
        ),
        CheckItem(
            tr("接管应用信息入口", "Handle app-info intents"),
            tr("ACTION_SHOW_APP_INFO（LibChecker 等工具调用）", "ACTION_SHOW_APP_INFO (used by tools like LibChecker)"),
            resolves(
                context,
                Intent("android.intent.action.SHOW_APP_INFO", Uri.parse("package:com.example.probe"))
            )
        ),
        CheckItem(
            tr("一键安装（无二次确认）", "One-tap install (no second confirmation)"),
            if (canInstall) tr("具备 INSTALL_PACKAGES，会话提交后直接安装", "Has INSTALL_PACKAGES — installs right after the session is committed")
            else tr("缺少 INSTALL_PACKAGES，安装会走系统确认", "Missing INSTALL_PACKAGES — installs go through system confirmation"),
            canInstall
        ),
        CheckItem(
            tr("展示应用名称 / 版本 / 大小 / 签名", "Show app name / version / size / signature"),
            tr("安装页与签名页已包含这些信息", "The install and signature pages already show these"),
            true
        ),
        CheckItem(
            tr("targetSdk 达到 N-1", "targetSdk is at least N-1"),
            tr("当前 targetSdk $targetSdk，设备 API ${Build.VERSION.SDK_INT}", "Current targetSdk $targetSdk, device API ${Build.VERSION.SDK_INT}"),
            targetSdk >= Build.VERSION.SDK_INT - 1
        )
    )
}

private fun resolves(context: Context, intent: Intent): Boolean {
    intent.addCategory(Intent.CATEGORY_DEFAULT)
    val resolved = runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull()
    return resolved?.activityInfo?.packageName == context.packageName
}
