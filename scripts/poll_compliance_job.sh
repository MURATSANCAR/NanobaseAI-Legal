#!/usr/bin/env bash
# Poll a SpecAI compliance analysis job with safe SQL quoting.
# Usage:
#   scripts/poll_compliance_job.sh <job_id>
# Optional env:
#   API=http://127.0.0.1:8098
#   DATABASE_CONTAINER=actenora-prodlike-postgres
#   DATABASE_NAME=specai
#   LEGAL_ENV=/etc/nanobaseai/legal.env
#   ORG_ID=11111111-1111-1111-1111-111111111111
set -euo pipefail

JOB_ID="${1:-}"
if [[ -z "${JOB_ID}" ]]; then
  echo "usage: $0 <compliance_job_id>" >&2
  exit 2
fi
if ! [[ "${JOB_ID}" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "error: job_id must be a UUID, got: ${JOB_ID}" >&2
  exit 2
fi

ORG_ID="${ORG_ID:-11111111-1111-1111-1111-111111111111}"
LEGAL_ENV="${LEGAL_ENV:-/etc/nanobaseai/legal.env}"
DATABASE_CONTAINER="${DATABASE_CONTAINER:-actenora-prodlike-postgres}"
DATABASE_NAME="${DATABASE_NAME:-specai}"

if [[ ! -r "${LEGAL_ENV}" ]]; then
  echo "error: cannot read ${LEGAL_ENV}" >&2
  exit 1
fi

DATABASE_USER="$(sudo grep '^DATABASE_USER=' "${LEGAL_ENV}" | cut -d= -f2-)"
DATABASE_PASSWORD="$(sudo grep '^DATABASE_PASSWORD=' "${LEGAL_ENV}" | cut -d= -f2-)"
if [[ -z "${DATABASE_USER}" || -z "${DATABASE_PASSWORD}" ]]; then
  echo "error: DATABASE_USER/PASSWORD missing in ${LEGAL_ENV}" >&2
  exit 1
fi

SQL=$(cat <<SQL
select set_config('app.current_organization_id', '${ORG_ID}', true);
select
  job.id as job_id,
  job.status as job_status,
  job.processed_requirement_count,
  job.completed_count,
  job.failed_count,
  job.manual_review_count,
  job.updated_at,
  coalesce((
    select count(*) from compliance_evaluation ce
    join ontology_concept sc on sc.id = ce.suggested_decision_concept_id
    where ce.analysis_job_id = job.id
      and sc.concept_code = 'INSUFFICIENT_INFORMATION'
  ), 0) as insufficient_information_count,
  coalesce((
    select sum(rmt.candidate_count) from requirement_matching_task rmt
    where rmt.compliance_job_id = job.id
  ), 0) as candidate_count_total,
  (
    select rmt.error_code
    from requirement_matching_task rmt
    where rmt.compliance_job_id = job.id
      and rmt.error_code is not null
    order by rmt.completed_at desc nulls last
    limit 1
  ) as last_error_code
from compliance_analysis_job job
where job.id = '${JOB_ID}'::uuid;
SQL
)

set +e
OUTPUT="$(sudo docker exec -e PGPASSWORD="${DATABASE_PASSWORD}" \
  "${DATABASE_CONTAINER}" \
  psql -U "${DATABASE_USER}" -d "${DATABASE_NAME}" -v ON_ERROR_STOP=1 -c "${SQL}" 2>&1)"
STATUS=$?
set -e

if [[ ${STATUS} -ne 0 ]]; then
  echo "error: psql failed for job ${JOB_ID}" >&2
  echo "${OUTPUT}" >&2
  exit 1
fi

if ! grep -q "${JOB_ID}" <<<"${OUTPUT}"; then
  echo "error: compliance job not found: ${JOB_ID}" >&2
  echo "${OUTPUT}" >&2
  exit 1
fi

echo "${OUTPUT}"
