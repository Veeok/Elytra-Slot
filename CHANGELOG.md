# Changelog — elytraslot-multiloader

All dates in local time. Format: chronological, most recent on top.

---

## 2026-07-13 — v2.0.1: Trinkets dedicated-slot fixes

### Fixed

- The standalone Elytra Slot no longer appears alongside Trinkets when Trinkets is installed.
- Right-clicking an elytra now places it directly in the dedicated Trinkets `chest/elytra` slot instead of a generic Charm slot.
- If Trinkets has not yet initialized the dedicated slot, elytra right-click is blocked rather than equipping into an incorrect Trinkets slot.
- Existing standalone-slot elytras still migrate into the dedicated Trinkets slot when the player joins.

### Verification

- `./gradlew.bat :fabric:build` passed.

---

## 2026-07-07 — v2.0.0 PR update: Trinkets priority integration

Compatibility update for Trinkets Updated. Trinkets remains optional. Elytra Slot still works normally without Trinkets installed.

### Added

- **Optional Trinkets Updated integration** through reflection. Elytra Slot checks for Trinkets API classes at runtime instead of making Trinkets a required dependency.
- **Dedicated Trinkets elytra slot** at `chest/elytra` when Trinkets Updated is installed.
- **Trinkets slot data files**:
  - `data/trinkets/entities/elytraslot.json` assigns `chest/elytra` to players.
  - `data/trinkets/slots/chest/elytra.json` defines the dedicated elytra slot with one item capacity and the Elytra Slot empty-slot icon.
  - `data/trinkets/tags/item/chest/elytra.json` allows `minecraft:elytra` in the dedicated slot.
- **Migration path** from the old standalone Elytra Slot container into Trinkets `chest/elytra` when possible.
- **Fabric metadata suggestion** for `trinkets_updated` in `fabric.mod.json` without making it a hard dependency.

### Changed

- Trinkets now has priority when the dedicated `chest/elytra` slot is available.
- Elytra Slot hides its standalone inventory slot when Trinkets provides the dedicated elytra slot.
- Right-click equip now falls through to Trinkets when the dedicated Trinkets slot is active.
- Shift-click routing now quietly falls through to vanilla or Trinkets when the standalone slot is hidden.
- Inventory and creative inventory UI drawing now only shows the standalone Elytra Slot panel when the standalone slot actually exists.
- Most noisy runtime logs were demoted from `INFO` to `DEBUG`, including equip, sync, save/load, quick-move, creative slot, and glide damage logs.

### Fixed

- Elytra Slot no longer tries to use, render, or damage its standalone slot while Trinkets already has an elytra-like item equipped.
- Inserting into the standalone Elytra Slot is blocked when an external Trinkets elytra is already equipped.
- If a stored standalone-slot elytra cannot be migrated to Trinkets because the Trinkets slot is missing or occupied, the standalone slot stays visible as a fallback so the item is not trapped.
- Fixed a broken Javadoc link that prevented the build from passing.

### Verification

- Earlier local check: `./gradlew.bat :fabric:build` passed after the first compatibility/logging update.
- A fresh build and gameplay test are still required after the dedicated Trinkets slot changes.
- Test matrix needed:
  - Fabric client + server without Trinkets.
  - Fabric client + server with Trinkets Updated installed.
  - Existing world/player with an elytra already stored in Elytra Slot before installing Trinkets.
  - Trinkets `chest/elytra` already occupied before migration.

---

## 2026-04-19 — v2.0.0 (full parity pass)

Comprehensive pass through every deviation in `VANILLA_PARITY_AUDIT.md`. All items now either FIXED or deliberately deferred as documented carve-outs (F1 non-Player scope, D7 cosmetic drop order, C1 deep Curios/Trinkets interop).

### Added

- **`ElytraSlotSyncPayload` (common)** — S2C `CustomPacketPayload` carrying `(UUID, ItemStack)`. Registered on both loaders.
- **`ElytraSyncDispatcher` (common)** — loader-agnostic broadcast contract with static holder; each loader registers its implementation at init.
- **`client/ElytraSlotSyncClient` (common)** — client-side handler that writes received stack into the target player's container via `setItemSilent`.
- **`fabric/ElytraSlotNetworkFabric`** — registers payload via `PayloadTypeRegistry.clientboundPlay()` and broadcasts via `PlayerLookup.tracking(entity)` + `ServerPlayNetworking.send` loop including self.
- **`fabric/ElytraSlotModFabricClient`** (Fabric `ClientModInitializer`) — registers S2C receiver via `ClientPlayNetworking.registerGlobalReceiver`.
- **`neoforge/ElytraSlotNetworkNeoForge`** — listens for `RegisterPayloadHandlersEvent` on the mod bus; uses `PacketDistributor.sendToPlayersTrackingEntityAndSelf` for broadcast.
- **`fabric.mod.json`** — added `client` entrypoint mapping to `ElytraSlotModFabricClient`.
- Explicit `[elytraslot] ...` INFO log at every server and client code path (equip cycle stages, sync broadcast+receive, quickMove branches, mayPlace/mayPickup decisions, startup registration confirmations, Curios/Trinkets detection results).

### Fixed

- **G1** — `ElytraEquipEffects.onSlotChanged` now runs the full equip pipeline server-side: sound + `GameEvent.EQUIP/UNEQUIP` (existing) PLUS stop-OLD attribute modifiers via `stack.forEachModifier(CHEST, (h, m) -> attrs.getInstance(h).removeModifier(m))`, stop-OLD location-based effects via `EnchantmentHelper.stopLocationBasedEffects`, apply-NEW attribute modifiers via `addTransientModifier`, run-NEW location-based effects via `EnchantmentHelper.runLocationChangedEffects`, advancement trigger via `CriteriaTriggers.INVENTORY_CHANGED.trigger`, and remote broadcast via `ElytraSyncDispatcher.broadcastSlotChange`.
- **G2** — `ElytraEquipMixin.onSwapWithEquipmentSlot` now honors `Equippable.swappable()`. If the in-hand item's Equippable has `swappable=false`, we return early and let vanilla handle it.
- **UI2** — Custom slot's `Slot.mayPickup` override now checks `EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE` (Curse of Binding) and blocks non-creative pickup, matching vanilla `ArmorSlot.mayPickup` exactly.
- **UI3 + UI4 + UI5 + SE2** — `InventoryMenuQuickMoveMixin` rewritten to use vanilla's `AbstractContainerMenu.moveItemStackTo(stack, start, end, reverse)` helper which provides:
  - Pass 1: merge onto existing matching stacks (no `mayPlace` — vanilla assumes same-item merges are legal).
  - Pass 2: fill a single empty slot with `mayPlace` check enforced.
  The mixin also fires `onSlotChanged` after a successful "OUT" move so unequip side-effects propagate.
- **C1** — Curios (NeoForge via `ModList.isLoaded("curios")`) and Trinkets (Fabric via `FabricLoader.isModLoaded("trinkets")`) detected at mod init; WARN log if present. Deep integration deferred.
- **C2** — Wearing/unwearing the custom-slot elytra now fires `CriteriaTriggers.INVENTORY_CHANGED.trigger(sp, sp.getInventory(), newStack)`, which is how vanilla's "Suit Up" / "Cover Me With Diamonds" advancements are driven.
- **E5** — `InventoryScreenBgMixin` composited panel was missing two bevel-bulge pixels that vanilla's `inventory.png` places just inside each corner (UV `(3, 3)` white-highlight and UV `(172, 162)` dark-gray-shadow). The 3×3 corner blits don't reach them and the solid-gray center fill overwrote their positions. Added two 1×1 blits after the center fill.

### Documentation

- `HANDOFF.md` — fully rewritten as the single current-state reference. Status table for every deviation group; architectural diagram; debug-logging catalog; project layout tree; next-steps guidance.
- `VANILLA_PARITY_AUDIT.md` — updated header section noting the 2026-04-19 fix pass.
- `RESEARCH.md` — preserved as the bytecode/API reference used by the fixes.
- `CHANGELOG.md` — this file.

### Deliberately deferred

- **F1** — non-Player mobs gliding from our custom slot. Requires moving `@Unique ElytraSlotContainer` from `Player` to `LivingEntity`. Impact of the current scope: zero (no modpack feature requests it).
- **D7** — death-drop order. Our elytra drops AFTER vanilla's `inventory.dropAll` rather than interleaved with `destroyVanishingCursedItems`. Same items on the ground, same pickup behavior; only `ClientboundAddEntityPacket` order differs.
- **C1 deep** — full Curios/Trinkets interop. Detection + WARN only. Full implementation estimated ~42h across both loaders + ongoing maintenance per upstream API bump.

---

## 2026-04-18 / 2026-04-19 — debugging + E4 + E5 fixes

### Fixed

- **E4** — `@At("TAIL")` on `WingsLayer.submit` never fired for the common "vanilla chest slot empty, custom slot has elytra" case because vanilla's `submit` has TWO `return` opcodes: an early return when `state.chestEquipment` has no `Equippable`, and a final return after rendering. Mixin's TAIL targets only the last opcode. Switched to `@At("RETURN")` which injects before every return opcode. Evidence gathered via HEAD+TAIL diagnostic logs (HEAD fired 1057×, TAIL 58×) and confirmed by `-Dmixin.debug.export=true` showing the transformed bytecode.
- **Panel corner pixel art** (precursor to E5) — investigated an earlier hypothesis that `inventory_panel.png` had wrong pixels at corners; reverted those texture edits after discovering `InventoryScreenBgMixin` procedurally draws the panel from vanilla `inventory.png` slices and the bitmap asset is vestigial.

### Infrastructure during debugging

- Added temporary `LOGGER.info` diagnostic tracers in `ElytraLayerMixin` (HEAD + TAIL + per-guard), `HumanoidRenderStateMixin` (render-state extract with identity-hash correlation), `ElytraSlotContainer` (setItem, clearContent, removeAllItems, removeItemNoUpdate, removeItem with stack traces), and `CapeLayerMixin` (unconditional invoke log). All diagnostic tracers were removed after the root cause was identified — only the final `drawing`/`onSlotChanged`/equip-side debug lines remain in production.
- Enabled `-Dmixin.debug.export=true` temporarily to dump transformed `WingsLayer.class` for bytecode-level inspection. Reverted in `fabric/build.gradle` after the E4 fix.

---

## Pre-2026-04-18 — initial audit fixes (A1–A3, B1/B3, C1, D1–D3, E1–E3)

See the original `HANDOFF.md` audit table (preserved in `VANILLA_PARITY_AUDIT.md` section 2) for the detailed deviation descriptions and per-item fixes. Summary:

- **A1–A3** — Break-path enchantment stop + damage-pick ordering.
- **B1** — Restrict right-click intercept to CHEST-slotted Equippables.
- **B3** — `!firstTick` guard added to `onSlotChanged` via `LivingEntityAccessor`.
- **C1** — NBT-based persistence chosen over platform-specific attachment (intentional).
- **D1–D3** — `KEEP_INVENTORY` gamerule respected, `PREVENT_EQUIPMENT_DROP` (vanishing curse) honored, respawn carry-over matches vanilla tri-gate.
- **E1** — Mixin compatibility level lowered from JAVA_25 to JAVA_21.
- **E2** — `CapeLayerMixin` hides cape when custom-slot elytra is worn.
- **E3** — Elytra render uses inlined `getPlayerElytraTexture` helper + tint `0`.

---

## Built artifacts (current)

| Loader | Path | SHA-256 |
|---|---|---|
| Fabric | `fabric/build/libs/elytraslot-fabric-26.1.2-2.0.0.jar` | `234d6bf1b315b5d0ddbe9ba2cfab85b57df87684bad9b0259421bee6cc2b3114` |
| NeoForge | `neoforge/build/libs/elytraslot-neoforge-26.1.2-2.0.0.jar` | `385408c800e2800dc910bdf02a760cde1ae5d863bf167ff0cdbc44fe759a2dae` |
