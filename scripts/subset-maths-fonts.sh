#!/usr/bin/env bash
# Builds the Maths and Poker dice's fonts from the untouched MathJax 2.7.9 originals in
# app/licensing/sources/: cut down to the characters the dice draw, and renamed to "DiceFive Maths",
# because the OFL keeps the Reserved Font Names MathJax_Main and MathJax_Math for the unmodified
# fonts. The copyright and licence entries stay as they are.
#
# A die that draws a new character needs it added below, then this re-run; MathsFontCoverageTest
# fails until it is. Needs fonttools (the sandbox's python3-fonttools).
#
# Usage: scripts/subset-maths-fonts.sh
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sources="$root_dir/app/licensing/sources"
fonts="$root_dir/app/shared/src/commonMain/composeResources/font"

# The Maths dice's digits, operators, brackets and "ln", and the Poker dice's ranks.
main_characters='023456!ln⌊⌋√A'
# The Maths dice's variable and constants.
italic_characters='xeπ'

subset() {
  python3 -I -m fontTools.subset "$1" --text="$2" --output-file="$3" --name-IDs='*' --name-languages='*' \
    --drop-tables+=FFTM
}

rename() {
  python3 -I - "$1" "$2" "$3" <<'PYTHON'
import sys
from fontTools.ttLib import TTFont

path, family, style = sys.argv[1:]
full = f"{family} {style}" if style != "Italic" else family
postscript = f"{family.replace(' ', '')}-{style}"
font = TTFont(path)
names = font["name"]
# Everything that names the font; 0 (copyright), 13 and 14 (licence) stay as they are.
for record in list(names.names):
    if record.nameID in (1, 2, 3, 4, 6, 16, 17):
        names.removeNames(nameID=record.nameID)
for name_id, value in ((1, family), (2, style), (3, full), (4, full), (6, postscript)):
    names.setName(value, name_id, 3, 1, 0x409)
# CFF outlines carry their own copy of the names.
cff = font["CFF "].cff
cff.fontNames = [postscript]
top = cff.topDictIndex[0]
top.FamilyName = family
top.FullName = full
font.save(path)
PYTHON
}

subset "$sources/MathJax_Main-Regular.otf" "$main_characters" "$fonts/dicefive_maths_regular.otf"
rename "$fonts/dicefive_maths_regular.otf" "DiceFive Maths" "Regular"
subset "$sources/MathJax_Math-Italic.otf" "$italic_characters" "$fonts/dicefive_maths_italic.otf"
rename "$fonts/dicefive_maths_italic.otf" "DiceFive Maths Italic" "Italic"

ls -l "$fonts"/dicefive_maths_*.otf
