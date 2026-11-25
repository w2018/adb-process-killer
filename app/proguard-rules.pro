# 进程杀手 R8 规则（minify + shrinkResources 开启）

# --- Shizuku：ShizukuChannel 通过反射调用 Shizuku.newProcess(...) ---
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# --- 无线 ADB 客户端 dadb 及其依赖 okio ---
-keep class dev.mobile.dadb.** { *; }
-dontwarn okio.**
-dontwarn org.jetbrains.annotations.**

# --- 通用：保留注解/泛型签名/内部类，Compose 与反射需要 ---
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- 说明：AndroidManifest 中声明的组件（Activity/Service/Receiver/
# --- AccessibilityService）由 R8 自动保留，无需手写 ---
