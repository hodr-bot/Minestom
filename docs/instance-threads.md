# Instance-thread model (fork)

One platform thread per instance. Each instance ticks its own chunks then its
own entities, sequentially. Gameplay IO stays on virtual threads. No global
tick barrier. Tick cadence uses `TICK_MS` / `SERVER_TICKS_PER_SECOND`, never
hard-coded millis.

## Ownership (the one rule)

Every gameplay object belongs to exactly one instance thread at a time:

- Entity, Chunk, EntityTracker, per-instance scheduler: touched only by the
  owner instance thread. Any other thread must go through the instance mailbox.
- Read-only shared data (registries, block/palette definitions): free from any
  thread.
- Global mutable state (teams, tab list broadcast, connection manager,
  global scheduler): owned by the conductor thread (`TickSchedulerThread`,
  kept as monitor + global services). Instance threads never touch it
  directly; they enqueue requests if needed.

Ownership handoff is by message passing only. No `ReentrantLock` on the
gameplay path. Existing `Chunk` read/write locks are kept as-is (uncontended,
they still protect IO-thread reads during save/load) — not our fight.

## Transfer state machine (entity A -> B)

SETTLED → PENDING_LEAVE → LEAVING → IN_TRANSIT → JOINING → SETTLED
A volatile `leaveFrom` field points to the tracker still holding the entity
while a leave is dispatched but not yet executed (null once the leave ran, or
for a first spawn). `instance`/position are assigned synchronously at
`setInstance` time (upstream timing), so `getInstance()`/`addViewer()` behave
between the call and the join; unsettled entities are skipped by ticks, and
`remove()` unregisters from `leaveFrom` when a leave is pending. Only the
owner thread mutates the entity and its registration. Stale tickets
(superseded id, cancelled event, removed entity) roll back or drop wherever
they surface; cancelling restores the pre-call position and instance.

1. `setInstance(J)` from any thread: build a `TransferTicket` (monotonic id
   per entity, last-writer-wins) and offer it to the source instance mailbox.
   If the entity has no instance yet, skip to step 3.
2. Source thread drains its mailbox at tick start (safe point, outside entity
   iteration): mark `LEAVING`, call `removeFromInstance`, fire
   `RemoveEntityFromInstanceEvent`, mark `IN_TRANSIT`, start async chunk load
   in J on a virtual thread (existing `loadOptionalChunk` path).
3. Load completion offers `TRANSFER_IN` to J's mailbox (stale ticket ids are
   dropped — this is the portal-spam guard, same role as the existing
   `setInstance` replacement counter in `Player`).
4. J's thread at tick start: register tracker, set fields, `spawn()`, fire
   `EntitySpawnEvent` (replacing `AddEntityToInstanceEvent` flow), mark
   `SETTLED`, complete the future.
5. Same-instance `setInstance` keeps the current synchronous teleport path.
6. Load failure: offer the entity back to the source mailbox; future fails.

The player packet queue travels with the player. Only the owner drains it.
During transit nobody drains (at most ~1 tick gap).

## Mailbox

One MPSC queue per instance thread (JCTools, already a dependency).
Message types: `TRANSFER_OUT`, `TRANSFER_IN`, `EXECUTE` (public
`instance.execute(task)` addition for plugins and cross-instance requests).
Drained at tick start, before chunks/entities.

## Instance loop (per instance thread)

Phase-offset start (`index * TICK_MS / N`, recomputed on register/unregister)
to smooth bursts. The thread owns the CPU budget (token bucket for local TPS):

- each slot: `budget = min(budget + grant, 2*grant)`; skip the gameplay tick
  while `budget < TICK_MS`, else tick and `budget -= elapsed`.
- `grant` is the effective budget: the manual override (`setTickBudget`) when
  set, otherwise an even share of the tick across registered instances
  (`TICK_MS / instance count`, back to auto with `setTickBudgetAutomatic`).
- skipped ticks skip everything gameplay (packets included); the queue is
  bounded and kicks on overflow, keepalive stays global so no false timeouts.
- never catch up: behind by more than K slots means dropping, same spirit as
  the current `SERVER_MAX_TICK_CATCH_UP` reset, but per instance.

Tick order inside a tick: mailbox -> instance scheduler/`InstanceTickEvent`
(existing `Instance.tick`) -> chunks (block handlers) -> entities (existing
`Entity.tick`, which already drains player packets) -> instance scheduler end.

## Assertions (hard, never `assert` keyword)

`checkOwner` helper throwing `IllegalStateException` on violation, always on:
entity mutation entry points, instance registration paths, mailbox misuse.
Hot per-block reads are out of scope (too hot); entry points suffice.

## What stays untouched

Socket IO threads, chunk load/save virtual threads, lighting async worker,
global scheduler + connection manager + packet flush on the conductor thread,
`Chunk` locks, the login/configuration sas (unowned players ticked by the
conductor until `setInstance` hands them to an instance thread).

## API compatibility

Public signatures unchanged. `Acquirable` is a no-op (always local on the
owner thread); `ThreadDispatcher`/`ThreadProvider`/`TickThread` internals are
dead but kept. Additions only: `execute`, `setTickBudget` /
`setTickBudgetAutomatic` / `isTickBudgetAutomatic`, transfer tickets
(internal records), `ModelInstance` + `ReadOnlyInstance`.

## Read-only model views (zero chunk duplication)

`ModelInstance` is a template: it holds chunks but is never registered and
never ticks (registration and thread start are refused loudly). `createView()`
returns a `ReadOnlyInstance` sharing the model chunks by reference.

- chunk reads (`getChunk`, loads, generation) go to the model; every write
  path (`setBlock`, `placeBlock`, `breakBlock`, `unloadChunk`, loader /
  supplier / generator setters) is rejected.
- entities are fully separated: each view has its own tracker and ticks its
  entities on its own thread; views never tick chunks (the model owns them).
- saves are no-ops (views own nothing); `getSrcInstance()` points at the model.
