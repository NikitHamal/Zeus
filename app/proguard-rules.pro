-keepattributes Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
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

# Prevent R8 from obfuscating JGit exception class names in stack traces
-keep class org.eclipse.jgit.errors.** {
    <fields>;
    <methods>;
}
-keep class org.eclipse.jgit.api.errors.** {
    <fields>;
    <methods>;
}
-keep class org.eclipse.jgit.transport.** {
    <fields>;
    <methods>;
}
