# Hikari Metrics (Phase 6)

Source: authenticated `GET /actuator/prometheus` (`hikaricp_connections_*`).

Sampling interval ≈ 1.5 s during the 8-job window (197 samples).

| Series | Observation |
|--------|-------------|
| `hikaricp_connections_max` | **5** verified at test start |
| `hikaricp_connections_active` | peak **3** |
| `hikaricp_connections_pending` | peak **0** |
| `hikaricp_connections_timeout_total` | **0** delta during test |

Earlier failed iterations (concurrency 8 then 4 with parallel creates) produced timeouts and `Could not open JPA EntityManager for transaction` — documented as anti-pattern, not production config.
