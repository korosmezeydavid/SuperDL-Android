# SuperDL ProGuard / R8 rules (release minify előkészítés)

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Launcher core
-keep class com.superdl.launcher.** { *; }

# TensorFlow Lite – modell betöltés és interpreter
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# ML Kit (QR, OCR)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Media-munkamenet (fulhallgato gomb, autoradio, zarkepernyo)
#
# MIERT KELL, ES MI TORTENT NELKULE (Mezei Geza, 2026-09-10, 1.63.6 kiadasi):
# a YouTube takarekos modja osszeomlott az elso setMetadata hivasnal:
#   NoClassDefFoundError: android/support/v4/util/ArrayMap
#   at android.support.v4.media.MediaMetadataCompat.<clinit>
#
# Az AndroidX a media-compat osztalyokat a REGI csomagneven tartja
# (android.support.v4.media.*), mert a binder-protokoll ezt varja. Az R8
# viszont ezeket az osztalyokat statikusan alig latja hasznalva — a
# MediaSession a rendszer fele reflexioval es binderen at dolgozik —, ezert
# kesz kidobni oket vagy a beluk hivatkozott segedosztalyokat.
#
# Ez a hibafajta NEM latszik forditaskor, es a fejlesztoi valtozatban sem:
# ott nincs tomorites. Csak a kiadasi APK-ban, es csak akkor, amikor a
# felhasznalo tenylegesen elinditja a lejatszast.
-keep class android.support.v4.media.** { *; }
-keep class android.support.v4.util.** { *; }
-keep class androidx.media.** { *; }
-dontwarn android.support.v4.**

# Speech / TTS reflection
-keep class android.speech.** { *; }

# Hidden API bypass (hotspot / tethering reflection)
-keep class org.lsposed.hiddenapibypass.** { *; }

# BroadcastReceiver / Service manifest entries
-keepclasseswithmembers class * {
    public <init>(...);
}
-keepclassmembers class * extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}
-keepclassmembers class * extends android.app.Service {
    public void onStartCommand(android.content.Intent, int, int);
}

# PDFBox optional JPEG2000 + BouncyCastle LDAP (not used on Android)
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-dontwarn javax.naming.NamingEnumeration
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn javax.naming.directory.SearchControls
-dontwarn javax.naming.directory.SearchResult

# Tink KeysDownloader opcionalis Google HTTP kliense (nem hasznaljuk)
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport