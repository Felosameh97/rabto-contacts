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

-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-keep class com.example.domain.model.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.ui.navigation.** { *; }
-keep class com.example.telecom.** { *; }
-keep class com.example.widget.** { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}

-dontwarn org.osmdroid.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn com.google.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.grpc.**
-dontwarn kotlinx.serialization.**
