# ---------------------------------------------------------------------------
# Zeus release rules
#
# JGit is the app's Git engine. It looks up ResourceBundles (JGitText.json
# .properties, RepoText, DfsText, ...) *by class name*, discovers transports
# through string-factory wiring and relies on stable field/method identity in
# several internal storage classes. Obfuscating or shrinking it produces
# opaque runtime failures (the "repos never clone" bug family). Android apps
# shipping JGit are expected to keep it verbatim, so the whole library is
# excluded from minification below. The size cost is fine for this app.
# ---------------------------------------------------------------------------

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions, SourceFile, LineNumberTable

# --- JGit: keep everything, globally ---
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# --- SLF4J (JGit logging facade) ---
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# --- JavaEWAH (JGit bitmaps) ---
-keep class com.googlecode.javaewah.** { *; }
-dontwarn com.googlecode.javaewah.**

# --- OkHttp / Okio: runtime reflection over platform classes ---
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- kotlinx.serialization ---
-keep class kotlinx.serialization.** { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.zeus.code.model.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }

# --- App model & config surface touched reflectively (BuildConfig is kept automatically) ---
-keep class com.zeus.code.model.** { *; }

# --- Keep exception constructors so error mapping/logging stays meaningful ---
-keep class * extends java.lang.Exception {
    <init>(...);
}

# ----------------------------------------------------------------------
# Resource lookup safety: JGit resolves "*.properties" l10n bundles whose
# names are derived from class names; keeping the classes (above) keeps the
# names aligned. Also keep META-INF resources intact (see build.gradle
# packaging block, which must not exclude META-INF services).
# ----------------------------------------------------------------------
