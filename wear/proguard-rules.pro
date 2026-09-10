# A stack trace in the on-watch debug log has to be readable as it is. A crash on a sideloaded
# watch is read days later, from whichever APK happened to be installed, and an obfuscated trace
# is useless without the mapping file from that exact build - which nothing keeps. R8 still
# shrinks and optimises; it only stops renaming, and keeps real line numbers.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
