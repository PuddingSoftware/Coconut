# Mob AI

## In-game demo

The demo server registers `/aidemo` and `/mobai`. Stand on an open, loaded patch of ground and run
`/aidemo spawn` to create three named sheep in front of you and receive 16 wheat. Hold wheat in your main hand
and walk away to attract them; put it away to let ordinary utility goals take over. Punch a sheep to watch it
flee. Demo mobs have 20 health, and the demo attack handler applies 2 damage to them per accepted attack.

| Command | Effect |
| --- | --- |
| `/aidemo spawn` | Spawn a passive flock and receive wheat. |
| `/aidemo food` | Place wheat five blocks ahead and make your nearest sheep hungry. Put away held wheat and stay back so you do not pick up its food. |
| `/aidemo rest` | Make your nearest sheep tired so it can choose rest on safe ground. |
| `/aidemo wander` | Make your nearest sheep bored so it can choose exploration. |
| `/aidemo group` | Make your nearest sheep seek company; keep another sheep nearby. |
| `/aidemo danger` | Briefly set your nearest demo mob on fire to demonstrate the hard-priority escape goal. No terrain is changed. |
| `/aidemo aggressive` | Spawn a zombie that targets survival/adventure players and retaliates when hit. Use `/gamemode survival` to try combat. |
| `/aidemo inspect` | Show the complete AI snapshot of your nearest demo mob. |
| `/aidemo simulation full` | Force your nearest demo mob into full simulation. |
| `/aidemo simulation essentials_only` | Suspend ordinary goals and run essential behaviors at reduced frequency. |
| `/aidemo simulation inactive` | Suspend AI while the engine continues its entity simulation. |
| `/aidemo simulation auto` | Restore automatic distance rings. |
| `/aidemo clear` | Remove your demo mobs and command-created food in the current instance. |

Scenario commands select your nearest matching demo mob within 32 blocks. Food/rest/wander/group select only
sheep. Other players' demo mobs and existing entities are not changed. Ordinary behavior scenarios update the
abstract needs and let the real scheduler choose; threats and hazards still take precedence. Put away held wheat
when observing these scenarios. Command-created food expires after two minutes; dead demo mobs disappear after
their death animation.

For a particular entity, use `/mobai <uuid>` (the spawn message includes its UUID), or a selector such as
`/mobai @n[type=sheep]`. The report includes active goals, scores, controls, memories, navigation, and failures.
The implementation is in `demo/ai/DemoMob.java` and `demo/commands/MobAiDemoCommand.java` under `demo/src/main/java/net/minestom`.

## Library setup

`EntityMob` has an opt-in ground-mob AI. Existing mobs retain their manual navigation until `enableAI()` is called.
The new implementation lives in `net.minestom.server.entity.ai`; the older implementation under `entity.old`
is retained for existing callers.

```java
EntityMob sheep = new EntityMob(EntityType.SHEEP);
MobBrain brain = sheep.enableAI();
sheep.setInstance(instance, new Pos(0, 64, 0));
```

The defaults include escape danger, damage reaction, combat, wander, idle, follow, approach food, socialize,
rest, and a compatible look-at-player goal. Passive mobs flee when damaged. Aggressive targeting, diets,
group affinity, attraction, and damage response are game rules supplied through `MobAiPolicy`:

```java
MobAiPolicy policy = new MobAiPolicy() {
    @Override
    public DamageResponse damageResponse(EntityMob mob) {
        return DamageResponse.RETALIATE; // FLEE and DEFEND are also supported
    }

    @Override
    public boolean isHostile(EntityMob mob, LivingEntity other) {
        return other instanceof Player;
    }
};
MobBrain brain = mob.enableAI(new AiSettings(), policy);
```

The default food policy accepts pickable item entities with a `FOOD` component. Override `isFood` for species
that eat wheat or other non-player food. Consumption respects `PickupItemEvent` and removes one item.
Combat emits `EntityAttackEvent`, following Minestom's existing attack convention. Your game's event handler
applies damage, armor, faction rules, and other combat effects; alternatively override `MobAiPolicy.attack`.
`DEFEND` limits pursuit to the area around the damage reaction's starting position. `RETALIATE` pursues the
attacker's last observed location while that memory remains valid.

## Tick pipeline and ownership

Each active AI tick expires memory, updates due sensors, evaluates hard goals, optionally selects ordinary
goals, executes active goals, and applies controller requests. Movement executes every tick in full simulation;
routine selection defaults to every 10 ticks. Sensor offsets derive from the mob UUID so mobs spawned together
do not all perform expensive sensing on the same tick. One bounded nearby-entity sensor feeds all entity-based
goals, with at most `maxPerceivedEntities()` visibility checks per update.

Goals declare exclusive `MOVE`, `LOOK`, `JUMP`, `INTERACT`, and `COMBAT` channels. Compatible goals coexist.
For example, wandering and looking at a player can run together. Navigation requires both `MOVE` and `JUMP`.
Controller methods verify these leases. The scheduler grants leases before `start`, calls `tick` while running,
then calls `stop` with a `TerminationReason` and releases all controller requests even when cleanup throws.

Ordinary goals are checked for eligibility before utility is evaluated. The scheduler compares a challenger
against every conflicting active goal. Both the incumbent's minimum duration and switching margin must be
satisfied. Hard priorities use larger numbers first and bypass ordinary hysteresis; equal hard priorities retain
the incumbent. Completed, interrupted, invalid-target, failed, simulation-change, instance-change, and removal
terminations are visible in the debug history. A failing goal receives a cooldown before it is reconsidered.

Extend `Goal` to supply `canStart`, `utility`, `start`, `tick`, and `stop`. A null result from `tick` means the goal
is still running, including while waiting for navigation. `DestinationGoal` supplies a reusable travel lifecycle.
Register goals with `brain.scheduler().add(goal)`; inspect or remove defaults through `scheduler().goals()` and `remove(goal)`.
`CompositeGoal` gives child actions start/tick/stop hooks under their parent's leases. `CombatGoal` demonstrates
approaching, strafing, attacking, and recovering, and cleans up controller requests between actions.

## Memory and events

`Blackboard` has typed keys with three lifetimes: current observations, expiring remembered information, and
long-lived internal state. Only internal keys may be stored without expiration. Default observations last at
most 40 ticks; remembered sightings, damage, food, and visits last at most 200 ticks. Each remembered entity
sighting has its own timestamp, so sensing another entity cannot prolong an old sighting indefinitely.

Entity memories store UUIDs and positions. `brain.resolve(uuid)` rejects removed/dead entities and entities
outside the current instance before controllers act. Following uses current or remembered locations instead
of tracking an unseen target's live position. Set an explicit follow target on the entity thread:

```java
brain.memory().set(Memories.FOLLOW_TARGET, player.getUuid());
```

The internal `NEEDS` record contains hunger, fatigue, boredom, isolation, and recent activity in `[0, 1]`.
These abstract values drift during full simulation. Rest reduces fatigue, food reduces hunger, and grouping
reduces isolation. Successful exploration records recent destinations and the last exploration tick.

Accepted damage automatically updates threat memory through `EntityMob.damage`. Cancelled or immune damage
does not trigger retaliation. Applications may invalidate a known entity with `brain.invalidateEntity(uuid)`.
Call `brain.conditionsChanged()` when relevant blocks, doors, or game rules change; this discards observations,
invalidates failed destinations, cancels outdated path requests, and allows new decisions. Sensors also permit
failed goals to retry when a newly detected food target changes or moves to another block.

All brain mutation, including enabling/disabling AI, sensor/goal registration, event hooks, and debug capture,
belongs on the entity's owning tick thread. Use `mob.scheduleNextTick(...)` when calling from another thread.

## Navigation and failures

Navigation owns destinations, path requests, waypoint following, jumping, stuck detection, repathing, and a
bounded failure history. Goals never mutate paths. A queued or pending request remains active without issuing
duplicates. Async completions only publish an immutable result; it is consumed on the entity tick. Cancelling
or replacing a request detaches its result and signals its cancellation flag, preventing late paths from being
installed after interruption, sleep, removal, or an instance change.

The default pathfinder is asynchronous, searches loaded chunks, and caps search iterations and path length.
The shared `PathRequestBudget` admits at most 8 new searches per instance tick and 32 in-flight searches per
instance. Admission is FIFO, with expiring queue leases for mobs whose engine ticks stop. In-flight permits are
released when the actual search ends, including exceptional completion. Custom pathfinders and a shared budget
can be supplied through the four-argument `enableAI` overload.

Moving targets trigger repathing only after sufficient movement and a minimum interval. Stuck or obstructed
paths have a bounded retry count. An exhausted best-effort path is a failure unless the actual destination was
reached. Failed destinations are remembered temporarily (100 ticks by default), exposed in navigation snapshots,
and excluded from goal eligibility until their cooldown expires or relevant conditions change.
`ApproachFoodGoal` terminates on path failure and frees its channels for other goals. Hazard escape keeps trying
other sensed safe destinations and requests swimming jumps while submerged.

Terrain sensing checks loaded collision space and supporting surfaces. This is a ground-mob implementation;
it does not provide flight or underwater route planning. Drowning/lava escape can request upward swimming and
seek nearby dry ground using the existing ground pathfinder. The engine remains responsible for physical
simulation, air depletion, fire damage, and other world rules.

Legacy `findPath`/`setPath` calls are rejected while AI owns navigation. `clearPath()` interrupts the current
movement goal. Disable AI to return to manual navigation. Use `brain.navigation().snapshot()` to inspect AI
paths; the old `getNavigationDataSnapshot()` describes only legacy navigation.

## Simulation levels

Automatic distance rings use the nearest player in the same instance. Full simulation enters at 32 blocks and
exits at 40. Essentials-only simulation enters at 96 blocks and exits at 112. No nearby players means inactive.
Ring checks are staggered, defaulting to every 20 ticks. These distances and all scheduling/navigation timings
are configurable through `AiSettings`.

* **Full:** sensors and selection at their configured frequencies; active goals/controllers every tick.
* **Essentials only:** hard goals and essential local sensors/controllers run every 4 ticks by default. Ordinary
  goals, needs drift, and the expensive entity-perception sensor are suspended. Accepted damage requests an
  earlier essential update. Terrain sampling only runs when escaping danger or reacting to damage.
* **Inactive:** the AI performs only activation-ring checks. The engine continues its normal entity ticks,
  physics, damage, fire, and event handling. Damage may update memory but does not force an inactive mob awake.

Transitions terminate active actions, cancel paths, and invalidate observations. Internal needs and remembered
information remain, subject to expiration and target validation on resume. No abandoned path or child action is
resumed blindly. Instance changes additionally discard world-specific memories and targets. For engine-managed
simulation, call `brain.setSimulationLevel(...)`; this disables automatic rings until `useAutomaticSimulation()`.

## Inspection

`brain.snapshot()` returns detached data containing goals, eligibility, utility scores, hard priorities and
override decisions, cooldowns, control ownership, memories with remaining lifetimes, navigation state,
pathfinding failures, and recent termination reasons. `snapshot.describe()` renders a readable text report.

An optional command captures this data on the selected mob's tick thread:

```java
MinecraftServer.getCommandManager().register(
        new MobAiDebugCommand("mobai", (sender, command) -> sender instanceof Player));
```

Use `/mobai @n[type=sheep]` or `/mobai <uuid>`. Supply your application's command access condition when registering
the command. Registration is explicit so the library does not install commands or change application permissions.
