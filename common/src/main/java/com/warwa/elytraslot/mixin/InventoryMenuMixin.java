package com.warwa.elytraslot.mixin;

import com.warwa.elytraslot.ElytraSlotConstants;
import com.warwa.elytraslot.ElytraSlotUtil;
import com.warwa.elytraslot.IElytraSlotPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the standalone Elytra Slot to the player's {@link InventoryMenu}.
 * The slot is appended to the end of the menu's slot list.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void elytraslot$addElytraSlot(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
        InventoryMenu menu = (InventoryMenu) (Object) this;
        var container = ((IElytraSlotPlayer) player).elytraslot_getElytraContainer();

        int insertedAtIndex = menu.slots.size();
        menu.addSlot(new Slot(container, 0, -25, 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                if (!ElytraSlotUtil.isElytraLike(stack)) {
                    ElytraSlotConstants.LOGGER.debug(
                        "[elytraslot] elytraSlot.mayPlace DENY not-elytra-like stack={}", stack
                    );
                    return false;
                }
                ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
                boolean chestHasElytra = ElytraSlotUtil.isElytraLike(chestItem);
                if (chestHasElytra) {
                    ElytraSlotConstants.LOGGER.debug(
                        "[elytraslot] elytraSlot.mayPlace DENY vanilla chest already has elytra chestItem={}",
                        chestItem
                    );
                    return false;
                }
                if (ElytraSlotUtil.hasExternalElytra(player)) {
                    ElytraSlotConstants.LOGGER.debug(
                        "[elytraslot] elytraSlot.mayPlace DENY external elytra already equipped player={}",
                        player.getName().getString()
                    );
                    return false;
                }
                return true;
            }

            @Override
            public boolean mayPickup(Player p) {
                ItemStack inSlot = this.getItem();
                if (!inSlot.isEmpty()
                    && !p.isCreative()
                    && EnchantmentHelper.has(inSlot, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                    ElytraSlotConstants.LOGGER.debug(
                        "[elytraslot] elytraSlot.mayPickup DENY binding-curse player={} stack={}",
                        p.getName().getString(), inSlot
                    );
                    return false;
                }
                return super.mayPickup(p);
            }

            @Override
            public Identifier getNoItemIcon() {
                return Identifier.fromNamespaceAndPath("elytraslot", "container/slot/elytra");
            }
        });
        ElytraSlotConstants.LOGGER.debug(
            "[elytraslot] elytraSlot added to InventoryMenu at index={} player={}",
            insertedAtIndex, player.getName().getString()
        );
    }
}
