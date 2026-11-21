# LibVLC: chamadas JNI dependem dos nomes originais.
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class io.github.mbsantos97.camerasaovivo.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.mbsantos97.camerasaovivo.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# NanoHTTPD (configuração pelo celular)
-dontwarn fi.iki.elonen.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
