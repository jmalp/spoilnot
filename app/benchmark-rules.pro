# Benchmark build only: keep R8's shrinking and optimization (as in release) but not its renaming, so the baseline
# profile generated from this build names the app's real classes and methods. AGP maps those names onto each
# release build's R8 output.
-dontobfuscate
