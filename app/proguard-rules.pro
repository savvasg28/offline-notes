-keepattributes InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-keepclassmembers class com.savvasg.offlinenotes.** {
    *** Companion;
}
-keepclasseswithmembers class com.savvasg.offlinenotes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.savvasg.offlinenotes.**$$serializer { *; }

-keep class com.savvasg.offlinenotes.data.network.dto.** { *; }
