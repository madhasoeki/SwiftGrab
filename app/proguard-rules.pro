# Proguard rules for SwiftGrab

# Turn off obfuscation to protect youtubedl-android, yt-dlp metadata serialization, JNI, and process execution
-dontobfuscate

# Keep youtubedl-android, ffmpeg, aria2c, and common classes
-keep class com.yausername.** { *; }

# Keep Apache Commons Compress and IO for native binary zip extraction
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**

# Keep Jackson JSON deserialization for yt-dlp metadata
-keep class com.fasterxml.jackson.** { *; }
-dontwarn java.beans.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.bootstrap.**

# Room
-keep class * extends androidx.room.RoomDatabase
