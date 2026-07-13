package com.warwa.elytraslot.fabric;

import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotSyncPayload;
import com.warwa.elytraslot.ElytraSyncDispatcher;
import com.warwa.elytraslot.IElytraSlotPlayer;
import com.warwa.elytraslot.ElytraSlotUtil;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric-side wiring for the elytra-slot sync payload (F fix). Called from
 * {@link ElytraSlotModFabric#onInitialize()}.
 *
 * <p>Steps:
 * <ol>
 *   <li>Registers the S2C payload type via
 *       {@code PayloadTypeRegistry.playS2C().register(TYPE, CODEC)}.</li>
 *   <li>Installs a {@link ElytraSyncDispatcher} implementation that, when the server
 *       signals a slot change, iterates {@code PlayerLookup.tracking(player)} and also
 *       includes the player themselves if they are a ServerPlayer.</li>
 * </ol>
 *
 * <p>Client-side reception is wired in {@code ElytraSlotModFabricClient}.
 */
public final class ElytraSlotNetworkFabric {
    private ElytraSlotNetworkFabric() {}

    public static void register() {
        ElytraSlotConstants.LOGGER.debug("[elytraslot] Fabric network init BEGIN");

        PayloadTypeRegistry.clientboundPlay().register(
            ElytraSlotSyncPayload.TYPE,
            ElytraSlotSyncPayload.STREAM_CODEC
        );
        ElytraSlotConstants.LOGGER.debug("[elytraslot] Fabric clientboundPlay payload type registered: {}",
            ElytraSlotSyncPayload.TYPE.id());

        ElytraSyncDispatcher.register((Player player, ItemStack newStack) -> {
            // Trinkets synchronizes its own dedicated chest/elytra inventory.
            if (ElytraSlotUtil.usesDedicatedTrinketsSlot(player)) return;
            if (player.level().isClientSide()) {
                ElytraSlotConstants.LOGGER.warn(
                    "[elytraslot] FabricDispatcher.broadcastSlotChange called client-side — skipping");
                return;
            }
            ElytraSlotSyncPayload payload = new ElytraSlotSyncPayload(player.getUUID(), newStack);
            int sent = 0;
            for (ServerPlayer tracker : PlayerLookup.tracking(player)) {
                ServerPlayNetworking.send(tracker, payload);
                sent++;
            }
            if (player instanceof ServerPlayer self) {
                ServerPlayNetworking.send(self, payload);
                sent++;
            }
            ElytraSlotConstants.LOGGER.debug(
                "[elytraslot] FabricDispatcher.broadcastSlotChange player={} stack={} sent={}",
                player.getName().getString(), newStack, sent
            );
        });

        // Equipment changes only reach clients already tracking the player. Send a snapshot
        // when tracking starts so a pre-equipped elytra renders without being toggled first.
        EntityTrackingEvents.START_TRACKING.register((entity, tracker) -> {
            if (!(entity instanceof Player player)) return;
            if (ElytraSlotUtil.usesDedicatedTrinketsSlot(player)) return;

            ItemStack stack = ((IElytraSlotPlayer) player).elytraslot_getElytraStack().copy();
            ServerPlayNetworking.send(tracker, new ElytraSlotSyncPayload(player.getUUID(), stack));
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] initial sync tracked={} tracker={} stack={}",
                player.getName().getString(), tracker.getName().getString(), stack
            );
        });

        ElytraSlotConstants.LOGGER.debug("[elytraslot] Fabric network init END");
    }
}
