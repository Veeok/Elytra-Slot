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
 * Adds our standalone elytra slot to the player's {@link InventoryMenu} when
 * Trinkets Updated is not providing the dedicated {@code chest/elytra} slot.
 * The standalone slot is appended to the end of the menu's slot list — its
 * index is therefore {@code menu.slots.size() - 1} at the time it's added.
 *
 * <p>When Trinkets Updated is installed and the dedicated Trinkets slot exists,
 * Trinkets is preferred and this standalone slot is not added. Any item already
 * stored in the standalone slot is moved into the dedicated Trinkets slot when
 * possible. If migration cannot happen, the standalone slot remains visible as
 * a safe fallback so the item is never trapped.
 *
 * <p>Overrides:
 * <ul>
 *   <li>{@link Slot#mayPlace} — only elytra-like items, and only when the vanilla
 *       chest slot doesn't also have an elytra-like item.</li>
 *   <li>{@link Slot#mayPickup} — honors {@code PREVENT_ARMOR_CHANGE} (Curse of
 *       Binding), matching vanilla {@code ArmorSlot.mayPickup} (UI2 fix).</li>
 *   <li>{@link Slot#getNoItemIcon} — points at our empty-slot elytra sprite.</li>
 * </ul>
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void elytraslot$addElytraSlot(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
        InventoryMenu menu = (InventoryMenu) (Object) this;
        var container = ((IElytraSlotPlayer) player).elytraslot_getElytraContainer();

        if (ElytraSlotUtil.usesTrinketsSlot(player)) {
            ItemStack storedStandaloneElytra = container.getItem(0);
            if (storedStandaloneElytra.isEmpty()) {
                ElytraSlotConstants.LOGGER.debug(
                    "[elytraslot] Trinkets dedicated elytra slot available; standalone slot hidden player={}",
                    player.getName().getString()
                );
                return;
            }

            if (ElytraSlotUtil.tryMoveCustomElytraToTrinkets(player, storedStandaloneElytra)) {
                container.setItemSilent(0, ItemStack.EMPTY);
                ElytraSlotConstants.LOGGER.debug(
                    "[elytraslot] migrated standalone elytra into Trinkets slot={} player={}",
                    ElytraSlotUtil.TRINKETS_ELYTRA_SLOT_ID,
                    player.getName().getString()
                );
                return;
            }

            ElytraSlotConstants.LOGGER.warn(
                "[elytraslot] Trinkets dedicated elytra slot is available, but the stored standalone elytra could not be migrated. Keeping standalone slot visible as a fallback. player={} stack={}",
                player.getName().getString(),
                storedStandaloneElytra
            );
        }

        int insertedAtIndex = menu.slots.size(); // this is the index this addSlot will land at
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

            /**
             * UI2 fix: mirror vanilla {@code ArmorSlot.mayPickup}. Block non-creative
             * pickup when the stack carries the {@code PREVENT_ARMOR_CHANGE} component
             * (Curse of Binding). Without this, Curse of Binding is ineffective on the
             * custom slot.
             */
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
