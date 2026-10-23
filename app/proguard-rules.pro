# kotlinx.serialization: keep generated serializers for @Serializable DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class io.github.tendo505.seatlock.android.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.tendo505.seatlock.android.**$$serializer { *; }

# Retrofit: keep service interface methods and generic signatures.
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface io.github.tendo505.seatlock.android.data.SeatLockApi { *; }
