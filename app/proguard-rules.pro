# rocat-app ProGuard/R8 rules.
# Tambahkan aturan keep/hide di sini sesuai kebutuhan release.

# Rhino 1.7.15 memakai java.beans.* untuk JavaToJSONConverters yang tidak ada
# di Android; R8 akan menganggapnya error saat minify tanpa aturan ini.
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

# Rhino memanggil fungsi/metode scripting secara refleksi (invokeNamedFunction),
# pastikan entry point yang diakses dari JS tidak dihilangkan/obfuskat.
-keep class org.mozilla.javascript.** { *; }
