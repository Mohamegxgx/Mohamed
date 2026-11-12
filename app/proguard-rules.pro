# ELBAGORRY Money ProGuard / R8 Rules
-keepattributes *Annotation*, SourceFile, LineNumberTable

# kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.data.local.entity.**$$serializer { *; }
-keep,includedescriptorclasses class com.example.data.backup.**$$serializer { *; }
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Jetpack Glance ActionCallback
-keep class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }

