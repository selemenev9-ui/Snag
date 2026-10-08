# yt-dlp/chaquopy internals use reflection; keep them intact.
-keep class com.yausername.** { *; }
-keep class com.chaquo.** { *; }
-keepclassmembers class * extends com.chaquo.python.PyObject { *; }
-dontwarn com.yausername.**
-dontwarn org.schabi.**
