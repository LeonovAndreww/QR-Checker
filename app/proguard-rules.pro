# Gson works here only through the JsonObject/JsonArray tree, with no reflection over
# classes, so it needs no rules.

# ML Kit's registrars are created by reflection through their no-argument constructors.
# The library's rule keeps only the classes, and R8 in AGP 9 then drops the
# constructors: ML Kit fails to start, the scan screen crashes and files yield no codes.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# The native barcode decoder reads these fields by name; R8 sees them only written.
-keep class com.google.android.libraries.barhopper.** { *; }
