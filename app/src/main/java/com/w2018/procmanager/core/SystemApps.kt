package com.w2018.procmanager.core

/**
 * 系统关键应用信息：用于「禁止冻结」保护与对用户的作用/风险说明。
 * 只做只读判断，不执行任何操作。
 */
object SystemApps {

    /** 关键系统应用：禁止冻结，避免系统功能异常。 */
    private val critical = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.settings",
        "com.android.launcher3",
        "com.android.launcher",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.android.shell",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.server.telecom",
        "com.android.telecom",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.keychain",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.cellbroadcastreceiver",
        "com.android.mms",
    )

    fun isCritical(pkg: String): Boolean =
        pkg in critical || pkg == "android" || pkg.startsWith("com.android.providers")

    /** 风险等级：0=低（用户应用）；1=高（系统/谷歌框架组件，需强确认）；2=禁止（关键系统，禁止冻结）。 */
    fun riskLevel(pkg: String): Int = when {
        isCritical(pkg) -> 2
        pkg.startsWith("com.android.") || pkg.startsWith("com.google.") ||
            pkg.startsWith("com.qualcomm.") || pkg.startsWith("com.mediatek.") -> 1
        else -> 0
    }

    private val purpose = mapOf(
        "com.android.systemui" to "系统界面：状态栏、通知栏、导航键、锁屏",
        "com.android.phone" to "电话服务：拨号、通话、短信与移动网络",
        "com.android.settings" to "系统设置：各项系统参数配置入口",
        "com.android.launcher3" to "桌面启动器：显示桌面与图标",
        "com.android.launcher" to "桌面启动器：显示桌面与图标",
        "com.google.android.gms" to "Google 服务框架：账户、推送、定位等基础服务",
        "com.android.vending" to "应用商店：应用安装与更新",
        "com.android.permissioncontroller" to "权限管理：应用权限授予与运行时弹窗",
        "com.android.providers.media" to "媒体存储：照片、音视频等媒体数据库",
        "com.android.providers.telephony" to "电话数据：短信与联系人数据库",
        "com.android.server.telecom" to "通话管理：来电去电调度",
        "com.android.bluetooth" to "蓝牙服务：蓝牙连接与传输",
        "com.android.nfc" to "NFC 服务：近场通信",
        "com.android.keychain" to "密钥库：证书与密钥管理",
    )

    /** 应用「作用」描述（系统应用用）。 */
    fun purposeOf(pkg: String, label: String): String =
        purpose[pkg] ?: "$label 系统组件，为系统或其他应用提供后台支撑"

    /** 冻结/停用该应用的「风险」描述。 */
    fun riskOf(pkg: String): String = when {
        !isCritical(pkg) && !(pkg.startsWith("com.android") || pkg.startsWith("com.google")) ->
            "停用后该应用将无法启动，可在本页随时恢复；一般不影响系统。"
        isCritical(pkg) ->
            "关键系统应用，停用可能导致界面/通话/设置等系统功能异常，已禁止冻结。"
        else ->
            "系统组件，停用可能影响依赖它的功能（如推送、同步、硬件服务），请谨慎操作。"
    }
}
