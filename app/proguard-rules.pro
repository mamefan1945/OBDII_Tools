-keep class com.obdiitools.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @dagger.* *;
}
