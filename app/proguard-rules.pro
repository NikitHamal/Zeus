-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.zeus.code.model.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**

# Keep exception classes and messages readable for better error reporting
-keepattributes Exceptions
-keepclassmembers class * extends java.lang.Throwable {
    void printStackTrace(java.io.PrintStream);
    void printStackTrace(java.io.PrintWriter);
}
-keep class * extends java.lang.Exception {
    <init>(...);
}
