#!/usr/bin/env bash
# Writes app/shared/.../ui/icons/Symbols.kt: every Material Symbols glyph the app draws, as an ImageVector built from
# Google's own path data (github.com/google/material-design-icons, symbols/web). Rounded, filled (fill 1, weight 400,
# grade 0, optical size 24) unless an entry says otherwise.
#
# To add a glyph: add a line to GLYPHS below - Kotlin name, the Symbol's name as fonts.google.com/icons lists it, then
# "fill0" for an unfilled one and "mirror" for one that flips in a right-to-left language - and run this. Needs the
# network.
#
# Usage: scripts/material-symbols.sh
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${root_dir}/app/shared/src/commonMain/kotlin/net/zodac/dicefive/ui/icons/Symbols.kt"

python3 -I - "${output}" <<'PYTHON'
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

GLYPHS = """
AcUnit ac_unit
Acute acute
Add add
Air air
Anchor anchor
Android android
ArrowBack arrow_back mirror
ArrowDownward arrow_downward
ArrowDropDown arrow_drop_down
ArrowForward arrow_forward mirror
ArrowUpward arrow_upward
AssignmentTurnedIn assignment_turned_in
AutoAwesome auto_awesome
AvgPace avg_pace
Block block
Bolt bolt
Calculate calculate
Cancel cancel
Casino casino
Check check
CheckCircle check_circle
Close close
Crown crown
DeleteForever delete_forever
DeleteSweep delete_sweep
DeveloperMode developer_mode
Download download
EmojiEvents emoji_events
EmojiPeople emoji_people
EventRepeat event_repeat
FastForward fast_forward
Favorite favorite
FileOpen file_open
Filter4 filter_4
Filter7 filter_7
FilterNone filter_none
Flag flag
FormatPaint format_paint
Functions functions
Gavel gavel
GridView grid_view
GridViewOutlined grid_view fill0
Groups groups
HeartBroken heart_broken
Help help mirror
HelpOutline help fill0 mirror
History history
HomeWork home_work
HourglassEmpty hourglass_empty
House house
Info info
KeyboardArrowDown keyboard_arrow_down
KeyboardArrowLeft keyboard_arrow_left mirror
KeyboardArrowRight keyboard_arrow_right mirror
KeyboardArrowUp keyboard_arrow_up
KeyboardDoubleArrowUp keyboard_double_arrow_up
Landslide landslide
Lock lock
LockOpen lock_open
Logout logout mirror
Looks3 looks_3
Looks6 looks_6
Memory memory
MilitaryTech military_tech
ModeHeat mode_heat
MotionPhotosOff motion_photos_off
MoveUp move_up
MyLocation my_location
Numbers numbers
OpenInNew open_in_new mirror
OtherHouses other_houses
Palette palette
Pause pause
Person person
PhotoCamera photo_camera
PlayArrow play_arrow
Psychology psychology
Public public
QuestionMark question_mark
Quiz quiz
RecordVoiceOver record_voice_over
Remove remove
Repeat repeat
Replay replay
RestartAlt restart_alt
RocketLaunch rocket_launch
Savings savings
ScreenRotation screen_rotation
SentimentDissatisfied sentiment_dissatisfied
SentimentNeutral sentiment_neutral
Skull skull
SlowMotionVideo slow_motion_video
Sort sort mirror
Speed speed
SportsEsports sports_esports
Sprint sprint
Stairs2 stairs_2
Star star
Stars stars
StayCurrentLandscape stay_current_landscape
StayCurrentPortrait stay_current_portrait
Storefront storefront
Style style
Sync sync
SyncAlt sync_alt
Target target
ThumbUp thumb_up
Timer timer
TravelExplore travel_explore
TrendingDown trending_down mirror
TrendingUp trending_up mirror
Undo undo mirror
Upgrade upgrade
Vibration vibration
VolumeUp volume_up mirror
WavingHand waving_hand
WorkspacePremium workspace_premium
"""

BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web"


def fetch(line):
    kotlin, name, *flags = line.split()
    fill = "" if "fill0" in flags else "_fill1"
    with urllib.request.urlopen(f"{BASE}/{name}/materialsymbolsrounded/{name}{fill}_24px.svg", timeout=30) as response:
        svg = response.read().decode()
    paths = re.findall(r'<path[^>]* d="([^"]+)"', svg)
    if not paths or re.search(r"<(?!/?svg|path)[a-z]", svg):
        sys.exit(f"{name}: expected one or more plain paths")
    view_box = re.search(r'viewBox="([^"]+)"', svg)
    # Nearly every Symbol is on a 960 grid with y from -960 to 0; a few still carry the old 24 grid, with no viewBox.
    grid = 960 if view_box and view_box.group(1) == "0 -960 960 960" else 24
    if view_box and grid == 24:
        sys.exit(f"{name}: unexpected viewBox {view_box.group(1)}")
    mirror = ", autoMirror = true" if "mirror" in flags else ""
    grid_arg = "" if grid == 960 else ", grid = 24"
    note = f"{name}{', unfilled' if not fill else ''}"
    return f'    /** `{note}` */\n    val {kotlin}: ImageVector by lazy {{ symbol("{kotlin}", "{"".join(paths)}"{grid_arg}{mirror}) }}\n'


lines = [l for l in GLYPHS.strip().splitlines() if l.strip()]
if [l.split()[0] for l in lines] != sorted(l.split()[0] for l in lines):
    sys.exit("Keep GLYPHS in alphabetical order")
with ThreadPoolExecutor(16) as pool:
    entries = list(pool.map(fetch, lines))

source = f"""package net.zodac.dicefive.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Generated by scripts/material-symbols.sh - add a glyph there and run it, rather than editing this by hand.

/**
 * Every Material Symbols glyph the app draws: Google's Material Symbols (Apache 2.0, github.com/google/material-design-icons),
 * Rounded and filled (fill 1, weight 400, grade 0, optical size 24) unless its note says unfilled. Each is built from
 * Google's own path data the first time it's used. A glyph that points a way (an arrow, a question mark) flips in a
 * right-to-left language, as Material's AutoMirrored icons did.
 */
object Symbols {{
{"".join(entries)}}}

/**
 * [pathData] as a 24dp [ImageVector], filled in black for the caller to tint. On a 960 [grid] the path's y runs from -960
 * at the top to 0, as Symbols draws; the older 24 grid runs from 0.
 */
private fun symbol(name: String, pathData: String, grid: Int = 960, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = grid.toFloat(),
        viewportHeight = grid.toFloat(),
        autoMirror = autoMirror,
    )
        .addGroup(translationY = if (grid == 960) 960f else 0f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
"""
with open(sys.argv[1], "w") as f:
    f.write(source)
print(f"{len(entries)} glyphs")
PYTHON
