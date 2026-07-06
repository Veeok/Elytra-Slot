package com.warwa.elytraslot.fabric;

import com.warwa.elytraslot.ElytraSlotConstants;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ElytraSlotModFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ElytraSlotConstants.LOGGER.info("Elytra Slot mod loaded! (Fabric)");

        if (FabricLoader.getInstance().isModLoaded("trinkets_updated")
            || FabricLoader.getInstance().isModLoaded("trinkets")) {
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] Trinkets detected — running standalone Elytra Slot with compatibility guards"
            );
        } else {
            ElytraSlotConstants.LOGGER.info("[elytraslot] Trinkets not detected — running standalone");
        }

        // F fix: register S2C payload type + broadcast dispatcher.
        ElytraSlotNetworkFabric.register();
    }
}
