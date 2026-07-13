package com.warwa.elytraslot.fabric;

import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotUtil;
import com.warwa.elytraslot.IElytraSlotPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public class ElytraSlotModFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ElytraSlotConstants.LOGGER.info("Elytra Slot mod loaded! (Fabric)");

        if (FabricLoader.getInstance().isModLoaded("trinkets_updated")
            || FabricLoader.getInstance().isModLoaded("trinkets")) {
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] Trinkets detected — enabling dedicated chest/elytra slot"
            );
        } else {
            ElytraSlotConstants.LOGGER.info("[elytraslot] Trinkets not detected — running standalone");
        }

        ElytraSlotUtil.registerTrinketsElytraPredicate();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            var legacyContainer = ((IElytraSlotPlayer) player).elytraslot_getElytraContainer();
            ElytraSlotUtil.migrateLegacyElytra(player, legacyContainer);
        });

        // F fix: register S2C payload type + broadcast dispatcher.
        ElytraSlotNetworkFabric.register();
    }
}
