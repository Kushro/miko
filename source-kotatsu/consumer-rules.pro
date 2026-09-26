# The parsers library targets both Android and desktop JVM, so it references a few classes that are
# not present on Android (and vice versa). Nothing here is reached at runtime on Android.
-dontwarn org.koitharu.kotatsu.parsers.**

# Parser instances are created by a `when` factory generated at build time by the library itself
# (no reflection), so R8 keeps every parser through normal reachability. These rules only exist so a
# release build with obfuscation enabled keeps the public surface the adapter talks to.
-keep,allowoptimization class org.koitharu.kotatsu.parsers.model.** { public protected *; }
-keep,allowoptimization class org.koitharu.kotatsu.parsers.config.** { public protected *; }
