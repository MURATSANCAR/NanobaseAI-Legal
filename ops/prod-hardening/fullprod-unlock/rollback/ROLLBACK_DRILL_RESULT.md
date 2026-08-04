# Rollback drill result

Finished: 2026-08-04T22:06:04.836216+00:00

| Step | Evidence | OK? |
|---|---|---|
| Flag false | env false + recreate | PASS |
| Parse without SC | /tmp/rollback-off-result.json | PASS |
| Flag true | env true + recreate | PASS |
| Parse with SC | /tmp/rollback-on-result.json | PASS |

Overall: **PASS**
