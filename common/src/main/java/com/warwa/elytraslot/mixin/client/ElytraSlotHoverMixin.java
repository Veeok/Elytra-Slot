package com.warwa.elytraslot.mixin.client;

import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Elytra Slot clickable when Trinkets opens an armor-group overlay.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 100)
public abstract class ElytraSlotHoverMixin {

    @Shadow protected AbstractContainerMenu menu;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
    private void elytraslot$preferElytraSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        Slot elytraSlot = elytraslot$findElytraSlot();
        if (elytraSlot == null || !elytraSlot.isActive()) return;
        if (!elytraslot$isMouseOverSlot(elytraSlot, mouseX, mouseY)) return;

        Slot current = cir.getReturnValue();
        if (current != elytraSlot) {
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] hover override mouse=({}, {}) rel=({}, {}) from={} to={}",
                mouseX, mouseY,
                Math.round(mouseX) - this.leftPos,
                Math.round(mouseY) - this.topPos,
                elytraslot$describeSlot(current),
                elytraslot$describeSlot(elytraSlot)
            );
        }
        cir.setReturnValue(elytraSlot);
    }

    private Slot elytraslot$findElytraSlot() {
        for (Slot slot : this.menu.slots) {
            if (slot != null && slot.container instanceof ElytraSlotContainer) {
                return slot;
            }
        }
        return null;
    }

    private boolean elytraslot$isMouseOverSlot(Slot slot, double mouseX, double mouseY) {
        int slotX = this.leftPos + slot.x;
        int slotY = this.topPos + slot.y;
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    private static String elytraslot$describeSlot(Slot slot) {
        if (slot == null) return "null";

        ItemStack stack = slot.getItem();
        String item = stack.isEmpty() ? "empty" : stack.toString();
        return slot.getClass().getName()
            + "{index=" + slot.index
            + ", containerSlot=" + slot.getContainerSlot()
            + ", pos=" + slot.x + "," + slot.y
            + ", container=" + slot.container.getClass().getName()
            + ", item=" + item
            + "}";
    }
}
