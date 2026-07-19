# ==========================================
# MySecret ProGuard 规则
# ==========================================

# ---- Gson 数据模型（反射访问字段名，不能混淆）----
-keep class com.mysecret.data.model.** { *; }
# Gson 用到的 TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ---- jcifs-ng（SMB 客户端，大量反射）----
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# slf4j 在 jcifs 的日志依赖里，Android 上不实现，stub 掉
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---- 加密相关（javax.crypto 反射）----
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# ---- Kotlin 协程 / 元数据 ----
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
