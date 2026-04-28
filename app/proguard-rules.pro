-keep class org.opencv.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.docscan.model.** { *; }
-keep class com.docscan.scanner.** { *; }
-dontwarn org.opencv.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn javax.annotation.**
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
