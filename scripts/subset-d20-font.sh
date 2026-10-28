#!/usr/bin/env bash
# Builds the D20 dice's font from the untouched Andika Bold original in app/licensing/sources/: cut down
# to the digits 0-9, with Andika's crossed seven (its "seven.Bar" glyph) as the 7, so a 7 is never read
# as a 1 (whose foot Andika draws by default), and renamed "DiceFive D20", because the OFL keeps the
# Reserved Font Names Andika and SIL for the unmodified font. The copyright and licence entries stay as
# they are.
#
# Needs fonttools (the sandbox's python3-fonttools).
#
# Usage: scripts/subset-d20-font.sh
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_font="${root_dir}/app/licensing/sources/Andika-Bold.ttf"
output="${root_dir}/app/shared/src/commonMain/composeResources/font/dicefive_d20.ttf"

python3 -I - "${source_font}" "${output}" <<'PYTHON'
import sys
from fontTools import subset
from fontTools.ttLib import TTFont

source, output = sys.argv[1:]
font = TTFont(source)
# The 7 is the crossed one.
for table in font["cmap"].tables:
    if ord("7") in table.cmap:
        table.cmap[ord("7")] = "seven.Bar"

options = subset.Options()
options.layout_features = []
options.name_IDs = ["*"]
options.name_languages = ["*"]
options.drop_tables += ["Silt"]
subsetter = subset.Subsetter(options)
subsetter.populate(text="0123456789")
subsetter.subset(font)

family, style = "DiceFive D20", "Bold"
full = f"{family} {style}"
postscript = f"{family.replace(' ', '')}-{style}"
names = font["name"]
# Everything that names the font; 0 (copyright), 13 and 14 (licence) stay as they are.
for record in list(names.names):
    if record.nameID in (1, 2, 3, 4, 6, 16, 17, 21, 22):
        names.removeNames(nameID=record.nameID)
for name_id, value in ((1, family), (2, style), (3, full), (4, full), (6, postscript)):
    names.setName(value, name_id, 3, 1, 0x409)
font.save(output)
PYTHON

ls -l "${output}"
