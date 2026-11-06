#!/usr/bin/env python3
"""
Plays a game on the last release, upgrades the app over it, and confirms that game resumes where it was left.
android-release.yml's upgrade-check job runs it on an emulator; .claude/EMULATOR.md explains the job.

It drives the app with adb and UiAutomator's dump rather than the Baseline Profile journey (Journeys.kt): the journey
runs the play app built from this commit, while this has to drive a release already out, whose labels can differ from
this commit's. Every label it needs is in the constants below, and a game saved by the old app is compared to the new
app's board by what's saved (names, totals, rolls left, and dice), not by how they're worded. UiAutomator can't see
which dice are held, so a roll shows it: on the old app to prove the holds took, and on the new one that they came back.

Usage:        .github/scripts/ci_upgrade_check.py <old.apk> <new.apk> <directory for a failure's files>
              Both APKs must be the github app, signed with one key, with the new one's versionCode the higher.

Exit status:  0   the game resumed and could be played on
              1   the old app couldn't be driven: it says nothing about the upgrade, so the job tries once more
              2   the upgrade lost or changed the game, or the new app couldn't play it: never retried

Requirements: adb, with one device connected
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree
from pathlib import Path

PACKAGE = "net.zodac.dicefive.github"
ACTIVITY = f"{PACKAGE}/net.zodac.dicefive.MainActivity"

# What the app shows. A release that relabels one of these needs the old label kept here beside the new one until a
# release with the new one is the last release.
PLAY_LABELS = ("Play",)
START_GAME_LABELS = ("Start Game",)
CONTINUE_LABELS = ("Continue",)
CRASH_TITLE_LABELS = ("Something went wrong",)
CUP_DESCRIPTION = "Dice cup"
DIE_DESCRIPTION = re.compile(r"Die (\d+)\b")
FIRST_SCORE_BOX = "Chance"
SECOND_SCORE_BOX = "Ones"
BANNER_DESCRIPTION = re.compile(r"(Achievement earned|Styles? unlocked): ")
DICE_COUNT = 5
HELD_DICE = 4

# Generous: on a hosted runner's emulator a single dump can take several seconds.
STEP_TIMEOUT_SECONDS = 90
TAP_RETRY_SECONDS = 20
POLL_SECONDS = 1
SAVE_SETTLE_SECONDS = 5


class DriveFailure(Exception):
    """The app didn't do what a step needed."""


class Verdict(Exception):
    """The upgrade lost or changed the game, or the upgraded app couldn't play it."""


def adb(*args, check=True, timeout=300):
    """adb's stdout. Failing with check on, it raises with what adb said."""
    result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout, check=False)
    if check and result.returncode != 0:
        raise DriveFailure(f"adb {' '.join(args)} failed: {result.stdout} {result.stderr}".strip())
    return result.stdout


def parse_screen(xml_text):
    """The nodes of a UiAutomator dump."""
    return list(ElementTree.fromstring(xml_text).iter("node"))


def read_screen():
    """The screen's nodes, or an empty list when it couldn't be read just now (UiAutomator refuses mid-change)."""
    adb("shell", "rm", "-f", "/sdcard/screen.xml", check=False)
    adb("shell", "uiautomator", "dump", "/sdcard/screen.xml", check=False, timeout=120)
    xml_text = adb("exec-out", "cat", "/sdcard/screen.xml", check=False)
    try:
        return parse_screen(xml_text)
    except ElementTree.ParseError:
        return []


def find_text(nodes, labels):
    return next((node for node in nodes if node.get("text") in labels), None)


def find_description(nodes, start):
    """The node whose description is start, or begins with it and a comma ("Die 1, 6"; never "Die 10")."""
    for node in nodes:
        description = node.get("content-desc", "")
        if description == start or description.startswith(f"{start},"):
            return node
    return None


def find_cup(nodes):
    """The dice cup, however a release words the rolls left after its name."""
    return next((node for node in nodes if node.get("content-desc", "").startswith(CUP_DESCRIPTION)), None)


def dice(nodes):
    """The dice on screen, in order of their numbers, as (number, description, node)."""
    found = []
    for node in nodes:
        match = DIE_DESCRIPTION.match(node.get("content-desc", ""))
        if match:
            found.append((int(match.group(1)), node.get("content-desc"), node))
    return sorted(found, key=lambda die: die[0])


def numbers_in(text):
    return [int(number) for number in re.findall(r"\d+", text)]


def snapshot(nodes):
    """
    What a saved game has to bring back: each visible name and total (the board's only plain text), the rolls left, and
    each die's value. Only the numbers of a description are kept, so rewording one in a release isn't taken for a lost
    game.
    """
    cup = find_cup(nodes)
    board_text = sorted(node.get("text") for node in nodes if node.get("text"))
    return {
        "names and totals": board_text,
        "rolls left": numbers_in(cup.get("content-desc", "")) if cup is not None else None,
        "dice": dice_values(nodes),
    }


def dice_values(nodes):
    return [numbers_in(description) for _, description, _ in dice(nodes)]


def differences(before, after):
    """Each part of a snapshot that isn't what it was, in words. Empty if the game is the same."""
    return [f"{part}: was {before[part]}, now {after[part]}" for part in before if before[part] != after[part]]


def centre(node):
    left, top, right, bottom = (int(number) for number in re.findall(r"\d+", node.get("bounds")))
    return (left + right) // 2, (top + bottom) // 2


def tap(node):
    x, y = centre(node)
    adb("shell", "input", "tap", str(x), str(y))


def check_not_crashed(nodes):
    """The app's crash page, which is also where a saved game that can't be resumed goes, ends the check at once."""
    if find_text(nodes, CRASH_TITLE_LABELS) is not None:
        raise DriveFailure("the app's crash page is showing")


def wait_for(what, condition, timeout=STEP_TIMEOUT_SECONDS):
    """
    The first answer of condition(nodes) that isn't None, False, or empty, read from the screen until timeout, or a
    failure naming what. Not by truth: a node without children, as most are, is false to Python.
    """
    deadline = time.monotonic() + timeout
    nodes = []
    while time.monotonic() < deadline:
        nodes = read_screen()
        check_not_crashed(nodes)
        answer = condition(nodes)
        if answer is not None and answer is not False and answer != []:
            return answer
        time.sleep(POLL_SECONDS)
    on_screen = " | ".join(filter(None, (node.get("text") or node.get("content-desc") for node in nodes)))
    raise DriveFailure(f"{what} never happened (on screen: {on_screen[:600]})")


def tap_until(what, find, done):
    """
    Taps what find(nodes) returns until done(nodes) says it took: a starved emulator drops taps. Tapped again only after
    TAP_RETRY_SECONDS without done, and never while done holds, so a slow tap isn't doubled.
    """
    deadline = time.monotonic() + STEP_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        target = wait_for(what, lambda nodes: True if done(nodes) else find(nodes))
        if target is True:
            return
        tap(target)
        retry_at = time.monotonic() + TAP_RETRY_SECONDS
        while time.monotonic() < retry_at:
            nodes = read_screen()
            check_not_crashed(nodes)
            if done(nodes):
                return
            time.sleep(POLL_SECONDS)
    raise DriveFailure(f"tapping {what} never took")


def has_cup(nodes):
    return find_cup(nodes) is not None


def cup_description(nodes):
    """What the cup says, which counts the rolls left. Compared whole: with none left it may hold no number."""
    cup = find_cup(nodes)
    return cup.get("content-desc") if cup is not None else None


def rolls_left(nodes):
    return numbers_in(cup_description(nodes) or "")


def settled_after_roll(nodes):
    """All the dice are down, the boxes take a score, and no banner is over the dice."""
    box = find_description(nodes, SECOND_SCORE_BOX)
    no_banner = not any(BANNER_DESCRIPTION.match(node.get("content-desc", "")) for node in nodes)
    return len(dice(nodes)) == DICE_COUNT and box is not None and box.get("clickable") == "true" and no_banner


def tap_changing_cup(what, find):
    """Taps what find(nodes) returns until the cup's description changes: a roll uses a roll, and a score starts a turn."""
    before = wait_for("the dice cup", cup_description)
    tap_until(what, find, lambda nodes: cup_description(nodes) not in (None, before))


def roll():
    """Rolls, and returns the dice once they've landed."""
    tap_changing_cup("the dice cup", lambda nodes: find_cup(nodes))
    wait_for("the dice to land", settled_after_roll)
    return dice_values(read_screen())


def score(box):
    tap_changing_cup(f"the {box} box", lambda nodes: find_description(nodes, box))


def hold_dice():
    """Taps the first HELD_DICE dice, once each: a second tap would release one. The next roll shows whether they took."""
    for number in range(1, HELD_DICE + 1):
        tap(wait_for(f"die {number}", lambda nodes, wanted=f"Die {number}": find_description(nodes, wanted)))
        time.sleep(POLL_SECONDS)


def installed_times():
    dump = adb("shell", "dumpsys", "package", PACKAGE)
    first = re.search(r"firstInstallTime=(.+)", dump)
    last = re.search(r"lastUpdateTime=(.+)", dump)
    version = re.search(r"versionName=(\S+)", dump)
    return (first and first.group(1).strip(), last and last.group(1).strip(), version and version.group(1))


def launch():
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def play_on_old_app(old_apk):
    """A fresh install of the old app with a game left mid-turn. Returns the board as it was left."""
    adb("uninstall", PACKAGE, check=False)
    adb("install", "--no-incremental", str(old_apk))
    print(f"Installed the old app, version {installed_times()[2]}", flush=True)
    launch()
    tap_until("Play", lambda nodes: find_text(nodes, PLAY_LABELS), lambda nodes: find_text(nodes, START_GAME_LABELS) is not None)
    tap_until("Start Game", lambda nodes: find_text(nodes, START_GAME_LABELS), has_cup)
    roll()
    score(FIRST_SCORE_BOX)
    landed = roll()
    hold_dice()
    rolled = roll()
    if rolled[:HELD_DICE] != landed[:HELD_DICE]:
        raise DriveFailure(f"the holds didn't take: {landed}, then {rolled}")
    wait_for("the dice to settle", settled_after_roll)
    left = snapshot(read_screen())
    print(f"The game as it was left: {left}", flush=True)
    if len(left["dice"]) != DICE_COUNT or not left["rolls left"]:
        raise DriveFailure(f"the board couldn't be read: {left}")
    return left


def upgrade(new_apk):
    """The new app over the old one, from the background, as a store's update would find it."""
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(SAVE_SETTLE_SECONDS)
    first_before, _, old_version = installed_times()
    adb("install", "-r", "--no-incremental", str(new_apk))
    first_after, _, new_version = installed_times()
    if first_after != first_before or new_version == old_version:
        raise Verdict(f"not an upgrade: {old_version} installed {first_before}, then {new_version} installed {first_after}")
    print(f"Upgraded {old_version} to {new_version}", flush=True)


def resume_on_new_app(left):
    """Continues the game on the new app: the same board, and one that still plays."""
    launch()
    tap_until("Continue", lambda nodes: find_text(nodes, CONTINUE_LABELS), has_cup)
    wait_for("the dice to come back", lambda nodes: len(dice(nodes)) == DICE_COUNT)
    resumed = snapshot(read_screen())
    print(f"The game as it resumed: {resumed}", flush=True)
    changed = differences(left, resumed)
    if changed:
        raise Verdict("the game changed in the upgrade: " + "; ".join(changed))
    rolled = roll()
    if rolled[:HELD_DICE] != left["dice"][:HELD_DICE]:
        raise Verdict(f"the held dice didn't come back held: {left['dice']}, then {rolled}")
    score(SECOND_SCORE_BOX)
    roll()
    print("The game resumed as it was left, and plays on", flush=True)


def save_failure_files(directory):
    """Best effort: a capture that fails mustn't hide the failure itself."""
    directory.mkdir(parents=True, exist_ok=True)
    try:
        (directory / "upgrade-failure.xml").write_text(adb("exec-out", "cat", "/sdcard/screen.xml", check=False))
        (directory / "upgrade-failure-logcat.txt").write_text(adb("logcat", "-d", check=False))
        with (directory / "upgrade-failure.png").open("wb") as screen:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=screen, timeout=60, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        print(f"The failure's files weren't all saved: {error}", file=sys.stderr)


def run(old_apk, new_apk, failure_directory):
    """The whole check, returning the exit status in this file's header."""
    try:
        left = play_on_old_app(old_apk)
    except (DriveFailure, subprocess.SubprocessError) as error:
        print(f"::warning::The old app couldn't be driven: {error}", flush=True)
        save_failure_files(failure_directory)
        return 1
    try:
        upgrade(new_apk)
        resume_on_new_app(left)
    except (DriveFailure, Verdict, subprocess.SubprocessError) as error:
        print(f"::error::The upgrade check failed: {error}", flush=True)
        save_failure_files(failure_directory)
        return 2
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(f"usage: {sys.argv[0]} <old.apk> <new.apk> <directory for a failure's files>")
    sys.exit(run(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])))
