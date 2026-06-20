# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep line numbers for readable crash traces (no source file name).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses

# --- kotlinx.serialization ---
# (The lib ships consumer rules, but keep our @Serializable models + generated
#  serializers explicitly so R8 can't strip/obfuscate the wire DTOs.)
-keepclassmembers @kotlinx.serialization.Serializable class com.food.opencook.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.food.opencook.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.food.opencook.data.remote.dto.** { *; }

# Room entities / enums used in DB + serialization.
-keepclassmembers enum * { *; }
