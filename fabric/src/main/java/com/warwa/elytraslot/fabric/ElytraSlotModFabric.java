package com.warwa.elytraslot.fabric;

import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ElytraSlotModFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ElytraSlotConstants.LOGGER.info("Elytra Slot mod loaded! (Fabric)");

        boolean trinketsLoaded = FabricLoader.getInstance().isModLoaded("trinkets_updated")
            || FabricLoader.getInstance().isModLoaded("trinkets");

        if (trinketsLoaded) {
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] Trinkets Updated detected — enabling optional dedicated Trinkets elytra slot integration"
            );
            ElytraSlotUtil.registerTrinketsElytraOnlyPredicate();
        } else {
            ElytraSlotConstants.LOGGER.info("[elytraslot] Trinkets not detected — running standalone");
        }

        // F fix: register S2C payload type + broadcast dispatcher.
        ElytraSlotNetworkFabric.register();
    }
}
