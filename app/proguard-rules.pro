# Keep the JS bridge class and its methods (reflection via addJavascriptInterface).
-keepclassmembers class com.argusforge.app.Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.argusforge.app.** { *; }
