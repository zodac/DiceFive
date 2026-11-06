#!/usr/bin/env python3
"""
Tests the parts of .github/scripts/ci_upgrade_check.py that need no device: reading the board, and what counts as the
game having changed.

Usage: python3 -I scripts/tests/ci-upgrade-check-test.py   (from the repository root)
"""
import importlib.util
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parents[2] / ".github" / "scripts" / "ci_upgrade_check.py"
spec = importlib.util.spec_from_file_location("ci_upgrade_check", SCRIPT)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


def board(*nodes):
    """A UiAutomator dump of nodes given as (text, description, clickable)."""
    body = "".join(
        f'<node text="{text}" content-desc="{description}" clickable="{clickable}" bounds="[10,20][30,60]" />'
        for text, description, clickable in nodes
    )
    return check.parse_screen(f"<hierarchy>{body}</hierarchy>")


LEFT = (
    ("Player 1", "", "false"),
    ("26", "", "false"),
    ("", "Ones", "true"),
    ("", "Dice cup, 1 roll left", "true"),
    ("", "Die 2, 5", "true"),
    ("", "Die 1, 6", "true"),
    ("", "Die 3, 5", "true"),
    ("", "Die 4, 5", "true"),
    ("", "Die 5, 1", "true"),
)


class UpgradeCheckTest(unittest.TestCase):

    def test_a_snapshot_holds_the_names_totals_rolls_and_dice_in_order(self):
        self.assertEqual(
            {"names and totals": ["26", "Player 1"], "rolls left": [1], "dice": [[1, 6], [2, 5], [3, 5], [4, 5], [5, 1]]},
            check.snapshot(board(*LEFT)),
        )

    def test_a_rewording_in_a_release_is_not_a_changed_game(self):
        reworded = [node if node[1] != "Die 1, 6" else ("", "Die 1 shows 6", "true") for node in LEFT]
        reworded = [node if not node[1].startswith("Dice cup") else ("", "Dice cup: 1 left", "true") for node in reworded]
        self.assertEqual([], check.differences(check.snapshot(board(*LEFT)), check.snapshot(board(*reworded))))

    def test_a_changed_die_total_or_lost_game_is_named(self):
        before = check.snapshot(board(*LEFT))
        changed_die = [node if node[1] != "Die 1, 6" else ("", "Die 1, 2", "true") for node in LEFT]
        self.assertEqual(
            ["dice: was [[1, 6], [2, 5], [3, 5], [4, 5], [5, 1]], now [[1, 2], [2, 5], [3, 5], [4, 5], [5, 1]]"],
            check.differences(before, check.snapshot(board(*changed_die))),
        )
        changed_total = [node if node[0] != "26" else ("0", "", "false") for node in LEFT]
        self.assertEqual(
            ["names and totals: was ['26', 'Player 1'], now ['0', 'Player 1']"],
            check.differences(before, check.snapshot(board(*changed_total))),
        )
        self.assertEqual(4 - 1, len(check.differences(before, check.snapshot(board()))))

    def test_a_description_is_found_whole_or_before_a_comma(self):
        nodes = board(("", "Die 10, 4", "true"), ("", "Die 1, 6", "true"), ("", "Chance", "true"))
        self.assertEqual("Die 1, 6", check.find_description(nodes, "Die 1").get("content-desc"))
        self.assertEqual("Chance", check.find_description(nodes, "Chance").get("content-desc"))
        self.assertIsNone(check.find_description(nodes, "Chan"))

    def test_the_board_counts_as_settled_only_with_all_dice_down_scoreable_boxes_and_no_banner(self):
        self.assertTrue(check.settled_after_roll(board(*LEFT)))
        self.assertFalse(check.settled_after_roll(board(*LEFT[:-1])))
        waiting = [node if node[1] != "Ones" else ("", "Ones", "false") for node in LEFT]
        self.assertFalse(check.settled_after_roll(board(*waiting)))
        self.assertFalse(check.settled_after_roll(board(*LEFT, ("", "Achievement earned: First Roll", "false"))))

    def test_the_crash_page_ends_a_step(self):
        with self.assertRaises(check.DriveFailure):
            check.check_not_crashed(board(("Something went wrong", "", "false")))
        check.check_not_crashed(board(*LEFT))


if __name__ == "__main__":
    unittest.main()
