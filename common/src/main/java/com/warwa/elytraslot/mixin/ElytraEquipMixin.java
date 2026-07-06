package com.warwa.elytraslot.mixin;

import com.warwa.elytraslot.ElytraEquipEffects;
import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotUtil;
import com.warwa.elytraslot.IElytraSlotPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link Equippable#swapWithEquipmentSlot(ItemStack, Player)} to route
 * elytras into the standalone Elytra Slot when Trinkets Updated is not providing
 * the dedicated Trinkets elytra slot.
 */
@Mixin(value = Equippable.class, priority = 500)
public abstract class ElytraEquipMixin {

    @Shadow public abstract boolean canBeEquippedBy(Holder<EntityType<?>> type);

    @Inject(method = "swapWithEquipmentSlot", at = @At("HEAD"), cancellable = true)
    private void onSwapWithEquipmentSlot(ItemStack inHand, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (!ElytraSlotUtil.isElytraLike(inHand)) return;

        Equippable inHandEquippable = inHand.get(DataComponents.EQUIPPABLE);
        if (inHandEquippable == null || inHandEquippable.slot() != EquipmentSlot.CHEST) return;

        if (!inHandEquippable.swappable()) {
            ElytraSlotConstants.LOGGER.debug(
                "[elytraslot] ElytraEquipMixin.onSwap gate-trip swappable=false stack={}", inHand
            );
            return;
        }

        if (ElytraSlotUtil.isElytraLike(player.getItemBySlot(EquipmentSlot.CHEST))) {
            return;
        }
        if (ElytraSlotUtil.usesTrinketsSlot(player)) {
            return;
        }
        if (ElytraSlotUtil.hasExternalElytra(player)) {
            return;
        }

        if (!player.canUseSlot(EquipmentSlot.CHEST)
            || !this.canBeEquippedBy(player.typeHolder())) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        IElytraSlotPlayer slotPlayer = (IElytraSlotPlayer) player;
        ItemStack inEquipmentSlot = slotPlayer.elytraslot_getElytraStack();

        if (EnchantmentHelper.has(inEquipmentSlot, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
            && !player.isCreative()) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (ItemStack.isSameItemSameComponents(inHand, inEquipmentSlot)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ElytraSlotConstants.LOGGER.debug(
            "[elytraslot] swapWithEquipmentSlot inHand={} existing={} count={} creative={}",
            inHand, inEquipmentSlot, inHand.getCount(), player.isCreative()
        );

        if (!player.level().isClientSide()) {
            player.awardStat(Stats.ITEM_USED.get(inHand.getItem()));
        }

        ItemStack oldForEffects = inEquipmentSlot.copy();

        InteractionResult result;
        if (inHand.getCount() <= 1) {
            ItemStack swappedToHand = inEquipmentSlot.isEmpty() ? inHand : inEquipmentSlot.copyAndClear();
            ItemStack swappedToEquipment = player.isCreative() ? inHand.copy() : inHand.copyAndClear();
            slotPlayer.elytraslot_setElytraStack(swappedToEquipment);
            ElytraEquipEffects.onSlotChanged(player, oldForEffects, swappedToEquipment);
            result = InteractionResult.SUCCESS.heldItemTransformedTo(swappedToHand);
        } else {
            ItemStack swappedToInventory = inEquipmentSlot.copyAndClear();
            ItemStack swappedToEquipment = inHand.consumeAndReturn(1, player);
            slotPlayer.elytraslot_setElytraStack(swappedToEquipment);
            ElytraEquipEffects.onSlotChanged(player, oldForEffects, swappedToEquipment);
            if (!swappedToInventory.isEmpty() && !player.getInventory().add(swappedToInventory)) {
                player.drop(swappedToInventory, false);
            }
            result = InteractionResult.SUCCESS.heldItemTransformedTo(inHand);
        }
        cir.setReturnValue(result);
    }
}
