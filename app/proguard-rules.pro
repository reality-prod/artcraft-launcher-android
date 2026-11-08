# No reflection or serialization libs are used (org.json only), so defaults are enough.

# JNI: the Rust bridge looks these up by name.
-keep class app.artcraft.launcher.bridge.NativeCore { *; }
