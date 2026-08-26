# 0005: The outbox ring buffer is a POD struct in RTC memory, not an object

## Context

`firmware/components/telemetry_outbox` needs to survive deep sleep without
losing buffered readings. ESP32's RTC slow memory (`RTC_NOINIT_ATTR`) is
preserved across deep sleep but is restored by the linker as raw bytes — no
constructor runs, no vtable is re-established, nothing is zeroed. If the
buffer's element type had a vtable (i.e. any virtual function, including
through inheritance) or a non-trivial constructor/destructor (e.g. a
`std::string` or `std::vector` member), an OTA update between two sleep
cycles could leave stale, mismatched vtable pointers or heap pointers sitting
in RTC memory, which is undefined behavior the moment that memory is touched
after wake.

## Decision

`TelemetryRecord` and `OutboxRing` (`telemetry_outbox.h`) are plain,
`__attribute__((packed))` structs with only fixed-width integer members. No
virtual functions, no `std::string`/`std::vector`/smart pointers, no
non-trivial constructors. The `TelemetryOutbox` class that wraps them (the
ESPHome `Component`) is ordinary heap-allocated C++ and is not subject to
this constraint — it does not live in RTC memory, only the ring buffer inside
it does, via a namespace-scope `static RTC_NOINIT_ATTR OutboxRing g_ring;` in
the `.cpp` file, not a class member.

## Consequences

- This is a deliberate absence of object-oriented design in one narrow spot.
  Contrast with `platform/domain`, which is OOD-heavy by design — see
  `docs/ARCHITECTURE.md`, "Where OOD is the wrong tool" for the general
  principle this follows.
- `static_assert(sizeof(TelemetryRecord) == 20, ...)` in the header is a
  tripwire: any change to the struct's layout must be a deliberate,
  reviewed change to the wire format (bump `WIRE_VERSION`), not an
  accidental side effect of adding a field.
- Growing the ring buffer's capacity or record size must be checked against
  the ESP32-C3's total RTC slow memory budget (8KB), not assumed to fit.
