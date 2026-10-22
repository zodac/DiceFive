#!/usr/bin/env bash
# Prints the changes going into a release as Markdown: a "## Changes since X.Y.Z" heading (naming
# the previous release, or plain "## Changes" if there isn't one), then every commit since that
# release, by its subject line ("[Category] Short description" - see .githooks/commit-msg),
# grouped under one "### Category" heading each, categories in alphabetical order
# (case-insensitive) and commits oldest-first within them:
#
#     ## Changes since 1.2.3
#
#     ### Gameplay
#     - [[1f5dd9f2]](https://github.com/zodac/DiceFive/commit/1f5dd9f2) Add the Tricolour game mode
#
# Used by the release workflow to build a GitHub release's description, after RELEASE_NOTES.md.
# Run it locally to preview what the next release will list.
#
# Android's releases are tagged android-v<version> (plain v<version> up to 1.0.1). "Since the previous
# release" means since the newest such tag reachable from HEAD other than this release's own tag (so
# re-running a release that already has a tag still lists the same changes); a plain v<version> tag
# counts only when there is no android-v one. With none, the whole history is listed. Left out: merge
# commits, and the release workflow's own version-bump commits (authored by github-actions[bot]). Subjects that don't follow the format - anything from before it
# was enforced - are listed under "Other", after every real category.
#
# Each commit links to its page on GitHub, using the "origin" remote (converted from an SSH URL if
# needed) as the repo.
#
# Usage: scripts/release-changelog.sh [<version>]   (defaults to VERSION)
set -euo pipefail

if [ $# -gt 1 ]; then
  echo "Usage: $0 [<version>]" >&2
  exit 1
fi
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version="${1:-$(tr -d '[:space:]' < "$root_dir/VERSION")}"

tag_prefixes=("android-v" "v")
this_tag="${tag_prefixes[0]}$version"

previous_tag=""
for tag_prefix in "${tag_prefixes[@]}"; do
  previous_tag="$(git -C "$root_dir" tag --merged HEAD --list "$tag_prefix*" --sort=-version:refname \
    | grep -vxF "$this_tag" | head -n 1 || true)"
  [ -n "$previous_tag" ] && break
done
if [ -n "$previous_tag" ]; then
  range="$previous_tag..HEAD"
else
  range="HEAD"
fi

repo_url="$(git -C "$root_dir" remote get-url origin \
  | sed -E 's#^git@([^:]+):#https://\1/#; s#\.git$##')"

if [ -n "$previous_tag" ]; then
  echo "## Changes since ${previous_tag#"$tag_prefix"}"
else
  echo "## Changes"
fi
echo

# Unit separator between fields: it can't appear in a subject line, unlike a tab or a pipe.
sep=$'\x1f'
git -C "$root_dir" log --no-merges --reverse --abbrev=8 --format="%h${sep}%an${sep}%s" "$range" \
  | awk -F "$sep" -v repo_url="$repo_url" '
      $2 == "github-actions[bot]" { next }
      {
        hash = $1; subject = $3
        category = ""; description = subject
        close_at = index(subject, "] ")
        if (substr(subject, 1, 1) == "[" && close_at > 2) {
          candidate = substr(subject, 2, close_at - 2)
          if (candidate !~ /[][]/) {
            category = candidate
            description = substr(subject, close_at + 2)
          }
        }
        # Group case-insensitively under the first spelling seen; real categories sort before
        # "Other" by a leading 0/1 on the sort key.
        key = tolower(category)
        if (!(key in label)) label[key] = (category == "" ? "Other" : category)
        rank = (category == "" ? "1" : "0")
        printf "%s%s\t%s\t- [[%s]](%s/commit/%s) %s\n", rank, key, label[key], hash, repo_url, hash, description
      }' \
  | LC_ALL=C sort -s -t $'\t' -k1,1 \
  | awk -F '\t' '
      $2 != current {
        if (NR > 1) print ""
        print "### " $2
        current = $2
      }
      { print $3 }
      END { if (NR == 0) print "_No changes since the previous release._" }'
