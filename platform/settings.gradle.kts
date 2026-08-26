rootProject.name = "water-monitor-platform"

include(
    "domain",
    "testing-support",
    "ingest-gateway",
    "stream-processor",
    "alert-service",
    "operations-api",
    "timescale-sink",
)
