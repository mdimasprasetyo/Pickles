# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class app.pickles.engine.**$$serializer { *; }
-keepclassmembers class app.pickles.engine.** {
    *** Companion;
}
-keepclasseswithmembers class app.pickles.engine.** {
    kotlinx.serialization.KSerializer serializer(...);
}
