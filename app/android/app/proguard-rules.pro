# Keep Moshi-generated adapters and Room entities readable in release builds.
-keep class com.ambientnotes.app.data.** { *; }
-keep class com.ambientnotes.app.recognition.RecognitionResult { *; }
-dontwarn org.json.**
