package com.warwa.elytraslot.mixin.client;

import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotContainer;
import com.warwa.elytraslot.ElytraSlotUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places and draws the standalone Elytra Slot in the creative inventory only when
 * that standalone slot exists. If Trinkets Updated provides the dedicated elytra
 * slot, the Trinkets UI owns the slot instead.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin
    extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

    private CreativeInventoryScreenMixin() { super(null, null, null); }

    @Shadow private static CreativeModeTab selectedTab;

    private static final Identifier TAB_INVENTORY_TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/creative_inventory/tab_inventory.png");

    private static final int SHIELD_FRAME_U = 34;
    private static final int SHIELD_FRAME_V = 19;
    private static final int FRAME_SIZE     = 18;

    private static final int ELYTRA_SLOT_X   = 127;
    private static final int ELYTRA_SLOT_Y   = 20;
    private static final int ELYTRA_FRAME_X  = 126;
    private static final int ELYTRA_FRAME_Y  = 19;

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void elytraslot$placeElytraSlotInCreativeInventory(CreativeModeTab tab, CallbackInfo ci) {
        if (tab.getType() != CreativeModeTab.Type.INVENTORY) return;

        LocalPlayer player = this.minecraft.player;
        if (player == null) return;

        InventoryMenu inventoryMenu = player.inventoryMenu;

        Slot ourSlot = null;
        for (Slot s : inventoryMenu.slots) {
            if (s.container instanceof ElytraSlotContainer) {
                ourSlot = s;
                break;
            }
        }
        if (ourSlot == null) {
            if (ElytraSlotUtil.usesTrinketsSlot(player)) return;

            ElytraSlotConstants.LOGGER.warn(
                "[elytraslot] selectTab(INVENTORY): no ElytraSlotContainer found in player.inventoryMenu — skipping"
            );
            return;
        }

        CreativeModeInventoryScreen.ItemPickerMenu pickerMenu =
            (CreativeModeInventoryScreen.ItemPickerMenu) this.menu;

        pickerMenu.slots.removeIf(slot -> slot.container instanceof ElytraSlotContainer);

        Slot wrapper = new CreativeModeInventoryScreen.SlotWrapper(
            ourSlot, ourSlot.index, ELYTRA_SLOT_X, ELYTRA_SLOT_Y);
        pickerMenu.slots.add(wrapper);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void elytraslot$drawElytraSlotFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo ci) {
        if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;
        if (!elytraslot$hasStandaloneSlotWrapper()) return;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            TAB_INVENTORY_TEXTURE,
            this.leftPos + ELYTRA_FRAME_X,
            this.topPos  + ELYTRA_FRAME_Y,
            (float) SHIELD_FRAME_U,
            (float) SHIELD_FRAME_V,
            FRAME_SIZE, FRAME_SIZE,
            256, 256
        );
    }

    private boolean elytraslot$hasStandaloneSlotWrapper() {
        for (Slot slot : this.menu.slots) {
            if (slot != null && slot.container instanceof ElytraSlotContainer) return true;
        }
        return false;
    }
}
