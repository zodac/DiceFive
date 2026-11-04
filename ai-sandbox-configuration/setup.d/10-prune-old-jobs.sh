#!/usr/bin/env bash
# Delete Claude's background jobs (~/.claude/jobs/<id>) once nothing in them has changed for SANDBOX_HISTORY_DAYS.
#
# cleanupPeriodDays (ai-sandbox/image/final/launch.sh) covers session transcripts and their side-car directories, not
# these, and a job's tmp/ can hold gigabytes: one kept a whole emulator image and SDK copy. It goes by the newest file in
# the job, so one still in use stays however long ago it began. A pinned job (jobs/pins.json) stays too.
set -uo pipefail

jobs_dir="${HOME}/.claude/jobs"
days="${SANDBOX_HISTORY_DAYS:-14}"

if [[ ! "${days}" =~ ^[1-9][0-9]*$ ]]; then
  warn "SANDBOX_HISTORY_DAYS is '${days}', not a number of days: old jobs are kept"
  exit 0
fi

removed=0
for job in "${jobs_dir}"/*/; do
  [[ -d "${job}" ]] || continue
  id="$(basename "${job}")"
  grep -qF "\"${id}\"" "${jobs_dir}/pins.json" 2> /dev/null && continue
  recent="$(find "${job}" -type f -mtime "-${days}" -print -quit)"
  [[ -n "${recent}" ]] && continue
  rm -rf -- "${job}" && removed=$((removed + 1))
done

if ((removed > 0)); then
  step "deleted ${removed} background job(s) unchanged for ${days} days"
else
  skip "old background jobs"
fi
