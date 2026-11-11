# Proguard rules for SwiftGrab

# Keep youtubedl-android, ffmpeg, and aria2c JNI and core classes
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# Keep Jackson JSON deserialization for yt-dlp metadata
-keep class com.fasterxml.jackson.** { *; }
-dontwarn java.beans.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.bootstrap.**

# Room
-keep class * extends androidx.room.RoomDatabase
