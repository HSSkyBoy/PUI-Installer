-dontobfuscate
-dontoptimize

-keep class com.panl.installer.** { *; }
-keep interface com.panl.installer.** { *; }
-keep class com.panl.pui.** { *; }
-keep interface com.panl.pui.** { *; }

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
