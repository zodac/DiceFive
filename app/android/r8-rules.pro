# Add project specific R8 rules here.
# https://developer.android.com/build/shrink-code

# Let R8 merge/inline more freely across access boundaries for smaller, more optimized output.
-allowaccessmodification

# CrashActivity hands Compose resources their context through this field, by name, in a process where the library's own
# provider never starts (CrashActivityTest checks it's still there).
-keepclassmembers class org.jetbrains.compose.resources.AndroidContextProvider {
    private static android.content.Context ANDROID_CONTEXT;
}
