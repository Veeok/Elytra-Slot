package com.warwa.elytraslot.mixin;

import com.warwa.elytraslot.ElytraEquipEffects;
import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotContainer;
import com.warwa.elytraslot.ElytraSlotUtil;
import com.warwa.elytraslot.IElytraSlotPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuQuickMoveMixin extends AbstractContainerMenu {

    protected InventoryMenuQuickMoveMixin() { super(null, 0); }

    private static final int INV_START = 9;
    private static final int HOTBAR_END_EXCLUSIVE = 45;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void elytraslot$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        // Trinkets owns routing whenever it is installed, including before its attachment
        // finishes loading for a newly opened player menu.
        if (ElytraSlotUtil.isTrinketsAvailable()) return;

        InventoryMenu menu = (InventoryMenu) (Object) this;
        if (index < 0 || index >= menu.slots.size()) return;

        Slot sourceSlot = menu.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return;

        int elytraIdx = findElytraSlotIndex(menu, player);
        if (elytraIdx < 0) {
            ElytraSlotConstants.LOGGER.warn(
                "[elytraslot] quickMove custom slot not found for player={} — falling through to vanilla",
                player.getName().getString()
            );
            return;
        }

        if (index == elytraIdx) {
            ItemStack beforeMove = sourceSlot.getItem();
            ItemStack preMoveSnapshot = beforeMove.copy();
            ElytraSlotConstants.LOGGER.debug(
                "[elytraslot] quickMove OUT beforeMove={} player={}",
                beforeMove, player.getName().getString()
            );

            boolean moved = this.moveItemStackTo(beforeMove, INV_START, HOTBAR_END_EXCLUSIVE, false);
            if (!moved) {
                ElytraSlotConstants.LOGGER.debug(
                    "[elytraslot] quickMove OUT no destination available — return EMPTY"
                );
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }

            if (beforeMove.isEmpty()) {
                sourceSlot.setByPlayer(ItemStack.EMPTY, preMoveSnapshot);
            } else {
                sourceSlot.setChanged();
            }
            sourceSlot.onTake(player, beforeMove);

            ElytraEquipEffects.onSlotChanged(player, preMoveSnapshot, sourceSlot.getItem());

            cir.setReturnValue(preMoveSnapshot);
            ElytraSlotConstants.LOGGER.debug(
                "[elytraslot] quickMove OUT SUCCESS pre={} remainingInSource={}",
                preMoveSnapshot, sourceSlot.getItem()
            );
            return;
        }

        ItemStack stackInSource = sourceSlot.getItem();
        if (!ElytraSlotUtil.isElytraLike(stackInSource)) return;

        Slot elytraSlot = menu.slots.get(elytraIdx);
        if (!elytraSlot.mayPlace(stackInSource)) {
            ElytraSlotConstants.LOGGER.debug(
                "[elytraslot] quickMove IN elytraSlot.mayPlace=false — falling through to vanilla"
            );
            return;
        }

        ItemStack preMoveSnapshot = stackInSource.copy();
        ElytraSlotConstants.LOGGER.debug(
            "[elytraslot] quickMove IN beforeMove={} elytraIdx={} srcIdx={}",
            stackInSource, elytraIdx, index
        );

        boolean moved = this.moveItemStackTo(stackInSource, elytraIdx, elytraIdx + 1, false);
        if (!moved) {
            ElytraSlotConstants.LOGGER.debug(
                "[elytraslot] quickMove IN moveItemStackTo=false — falling through to vanilla"
            );
            return;
        }

        if (stackInSource.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY, preMoveSnapshot);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(player, stackInSource);
        cir.setReturnValue(preMoveSnapshot);
        ElytraSlotConstants.LOGGER.debug(
            "[elytraslot] quickMove IN SUCCESS pre={} remainingInSource={}",
            preMoveSnapshot, sourceSlot.getItem()
        );
    }

    private static int findElytraSlotIndex(InventoryMenu menu, Player player) {
        ElytraSlotContainer expected = ((IElytraSlotPlayer) player).elytraslot_getElytraContainer();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s != null && s.container == expected) return i;
        }
        return -1;
    }
}
