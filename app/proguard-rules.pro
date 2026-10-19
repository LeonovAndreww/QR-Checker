# Gson works here only through the JsonObject/JsonArray tree, with no reflection over
# classes, so it needs no rules.

# ML Kit's barcode library ships without a rule for its JNI classes: the native decoder
# reads the RecognitionOptions fields and BarhopperV3.nativePointer by name. R8 in AGP 9
# sees the fields only written and removes them, and the decoder then fails on every
# frame and every PDF page.
-keep class com.google.android.libraries.barhopper.** { *; }
