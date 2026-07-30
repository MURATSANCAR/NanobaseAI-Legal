# Compliance Connection Pool Test

With small Hikari pool and concurrent long model waits, prepare/persist must release
connections so polling/cancel/heartbeat still acquire connections. Pending connections
must not grow unbounded while models run.
