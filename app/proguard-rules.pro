-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class dev.viko.sessao.**$$serializer { *; }
-keepclassmembers class dev.viko.sessao.** { *** Companion; }
-keepclasseswithmembers class dev.viko.sessao.** { kotlinx.serialization.KSerializer serializer(...); }
# Retrofit: interfaces da API e tipos genéricos das respostas.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface dev.viko.sessao.data.api.TmdbService { *; }
-dontwarn org.slf4j.**
