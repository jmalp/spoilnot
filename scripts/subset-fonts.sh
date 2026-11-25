#!/usr/bin/env bash
# Writes the app's bundled fonts (app/src/main/res/font) as subsets of the full fonts in tools/fonts (after v1,
# review EFF-07, DECISIONS 2026-11-22). Needs HarfBuzz's hb-subset (`brew install harfbuzz`). Run it after changing
# a font or the ranges below, then `./gradlew testDevDebugUnitTest --tests '*FontSubsetTest'`, which fails if a
# string uses a character the subsets lack.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v hb-subset >/dev/null || { echo "subset-fonts: hb-subset not found (brew install harfbuzz)" >&2; exit 1; }

# Manrope, for all the app's text: the Latin and Latin Extended ranges the web app's font files cover
# (@fontsource-variable/manrope's unicode.json), every OpenType feature, the whole weight axis.
LATIN="0000-00FF,0131,0152-0153,02BB-02BC,02C6,02DA,02DC,0304,0308,0329,2000-206F,20AC,2122,2191,2193,2212,2215,FEFF,FFFD"
LATIN_EXT="0100-02BA,02BD-02C5,02C7-02CC,02CE-02D7,02DD-02FF,1D00-1DBF,1E00-1E9F,1EF2-1EFF,2020,20A0-20AB,20AD-20C0,2113,2C60-2C7F,A720-A7FF"
hb-subset tools/fonts/manrope.ttf --unicodes="$LATIN,$LATIN_EXT" --layout-features='*' \
  --output-file=app/src/main/res/font/manrope.ttf

# Fredoka, for the app's name on Welcome only: printable ASCII and Latin-1 at the one weight used (SemiBold, normal
# width), so a static font.
hb-subset tools/fonts/fredoka.ttf --unicodes="0020-007E,00A0-00FF" --layout-features='*' \
  --variations="wght=600,wdth=100" --output-file=app/src/main/res/font/fredoka.ttf

ls -l tools/fonts/*.ttf app/src/main/res/font/*.ttf
