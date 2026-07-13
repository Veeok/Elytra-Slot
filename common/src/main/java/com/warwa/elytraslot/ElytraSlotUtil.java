package com.warwa.elytraslot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.function.Predicate;

public class ElytraSlotUtil {
    private static final String DEDICATED_TRINKETS_SLOT = "chest/elytra/0";
    private static final Method TRINKETS_GET_ATTACHMENT = findTrinketsGetAttachment();
    private static final Method TRINKETS_GET_SLOT_ACCESS = findTrinketsGetSlotAccess();
    private static final Method TRINKETS_EQUIPPED = findTrinketsEquipped();
    private static final Method TRINKET_ACCESS_IS_VALID = findTrinketAccessMethod("isValid");
    private static final Method TRINKET_ACCESS_GET = findTrinketAccessMethod("get");
    private static final Method TRINKET_ACCESS_SET = findTrinketAccessMethod("set", ItemStack.class);
    private static final Method TRINKET_ACCESS_INVENTORY = findTrinketAccessMethod("inventory");
    private static final Method TRINKET_ACCESS_NAME = findTrinketAccessMethod("getSerializedName");
    private static final Method TRINKETS_REGISTER_PREDICATE = findTrinketsRegisterPredicate();

    private static boolean warnedTrinketsQueryFailure = false;
    private static boolean registeredTrinketsPredicate = false;

    public static boolean isElytraLike(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.GLIDER);
    }

    public static boolean isTrinketsAvailable() {
        return TRINKETS_GET_ATTACHMENT != null && TRINKETS_GET_SLOT_ACCESS != null;
    }

    public static boolean usesDedicatedTrinketsSlot(Player player) {
        return getDedicatedTrinketsAccess(player) != null;
    }

    /**
     * Returns {@code null} when the dedicated Trinkets slot is unavailable; an empty result
     * means the slot exists but has no elytra equipped.
     */
    public static ItemStack getDedicatedTrinketsElytra(Player player) {
        Object access = getDedicatedTrinketsAccess(player);
        if (access == null || TRINKET_ACCESS_GET == null) return null;

        try {
            Object stack = TRINKET_ACCESS_GET.invoke(access);
            return stack instanceof ItemStack itemStack ? itemStack : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure("[elytraslot] Failed to read the dedicated Trinkets elytra slot", ex);
            return null;
        }
    }

    public static boolean setDedicatedTrinketsElytra(Player player, ItemStack stack) {
        Object access = getDedicatedTrinketsAccess(player);
        if (access == null || TRINKET_ACCESS_SET == null) return false;

        try {
            if (!(TRINKET_ACCESS_SET.invoke(access, stack) instanceof Boolean changed) || !changed) {
                return false;
            }
            markTrinketsAccessChanged(access);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure("[elytraslot] Failed to write the dedicated Trinkets elytra slot", ex);
            return false;
        }
    }

    /**
     * Moves an old standalone-slot stack into the dedicated Trinkets slot once it exists.
     * If a player already filled the new slot, the old stack is returned to their inventory
     * (or dropped) rather than leaving it hidden in legacy data.
     */
    public static boolean migrateLegacyElytra(Player player, ElytraSlotContainer legacyContainer) {
        if (player.level().isClientSide() || !usesDedicatedTrinketsSlot(player)) return false;

        ItemStack legacyStack = legacyContainer.getItem(0);
        if (legacyStack.isEmpty()) return false;

        ItemStack dedicatedStack = getDedicatedTrinketsElytra(player);
        if (dedicatedStack == null) return false;

        if (dedicatedStack.isEmpty()) {
            if (!setDedicatedTrinketsElytra(player, legacyStack.copy())) return false;
            legacyContainer.setItemSilent(0, ItemStack.EMPTY);
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] migrated legacy stack={} into dedicated Trinkets slot player={}",
                legacyStack, player.getName().getString()
            );
            return true;
        }

        ItemStack recovered = legacyStack.copy();
        legacyContainer.setItemSilent(0, ItemStack.EMPTY);
        if (!player.getInventory().add(recovered)) {
            player.drop(recovered, false);
        }
        ElytraSlotConstants.LOGGER.warn(
            "[elytraslot] dedicated Trinkets slot was occupied; returned legacy stack={} player={}",
            recovered, player.getName().getString()
        );
        return true;
    }

    /** Registers the predicate used by the optional {@code chest/elytra} Trinkets data slot. */
    public static void registerTrinketsElytraPredicate() {
        if (registeredTrinketsPredicate || TRINKETS_REGISTER_PREDICATE == null) return;

        try {
            Class<?> predicateClass = Class.forName("eu.pb4.trinkets.api.TrinketsApi$TrinketPredicate");
            Object predicate = Proxy.newProxyInstance(
                predicateClass.getClassLoader(),
                new Class<?>[] {predicateClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("test")) {
                        if (!(args[0] instanceof ItemStack stack) || !(args[2] instanceof Player player)) {
                            return false;
                        }
                        return isElytraLike(stack)
                            && !isElytraLike(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST))
                            && !hasExternalElytra(player);
                    }
                    if (method.getName().equals("toString")) return "elytraslot:elytra predicate";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                }
            );
            TRINKETS_REGISTER_PREDICATE.invoke(
                null,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(ElytraSlotConstants.MOD_ID, "elytra"),
                predicate
            );
            registeredTrinketsPredicate = true;
            ElytraSlotConstants.LOGGER.info("[elytraslot] registered dedicated Trinkets elytra-slot predicate");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure("[elytraslot] Failed to register the dedicated Trinkets elytra-slot predicate", ex);
        }
    }

    public static boolean hasExternalElytra(Player player) {
        return hasTrinketsElytra(player);
    }

    private static boolean hasTrinketsElytra(Player player) {
        if (TRINKETS_GET_ATTACHMENT == null || TRINKETS_EQUIPPED == null || TRINKET_ACCESS_NAME == null) return false;

        try {
            Object attachment = TRINKETS_GET_ATTACHMENT.invoke(null, player);
            if (attachment == null) return false;

            Predicate<ItemStack> predicate = ElytraSlotUtil::isElytraLike;
            Object result = TRINKETS_EQUIPPED.invoke(attachment, predicate, true);
            if (!(result instanceof Collection<?> accesses)) return false;

            for (Object access : accesses) {
                Object name = TRINKET_ACCESS_NAME.invoke(access);
                if (!DEDICATED_TRINKETS_SLOT.equals(name)) return true;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure(
                "[elytraslot] Failed to query Trinkets equipped elytra state; falling back to standalone behavior",
                ex
            );
            return false;
        }
    }

    private static Method findTrinketsGetAttachment() {
        try {
            Class<?> api = Class.forName("eu.pb4.trinkets.api.TrinketsApi");
            return api.getMethod("getAttachment", net.minecraft.world.entity.LivingEntity.class);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but getAttachment signature was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketsGetSlotAccess() {
        try {
            Class<?> attachment = Class.forName("eu.pb4.trinkets.api.TrinketAttachment");
            return attachment.getMethod("getSlotAccess", String.class, int.class);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but getSlotAccess signature was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketsEquipped() {
        try {
            Class<?> attachment = Class.forName("eu.pb4.trinkets.api.TrinketAttachment");
            return attachment.getMethod("equipped", Predicate.class, boolean.class);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but equipped signature was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketAccessMethod(String name, Class<?>... parameters) {
        try {
            Class<?> access = Class.forName("eu.pb4.trinkets.api.TrinketSlotAccess");
            return access.getMethod(name, parameters);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets slot access is missing {}", name, ex);
            return null;
        }
    }

    private static Method findTrinketsRegisterPredicate() {
        try {
            Class<?> api = Class.forName("eu.pb4.trinkets.api.TrinketsApi");
            Class<?> predicate = Class.forName("eu.pb4.trinkets.api.TrinketsApi$TrinketPredicate");
            return api.getMethod("registerTrinketPredicate", net.minecraft.resources.Identifier.class, predicate);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but registerTrinketPredicate signature was not recognized", ex);
            return null;
        }
    }

    private static Object getDedicatedTrinketsAccess(Player player) {
        if (!isTrinketsAvailable() || TRINKET_ACCESS_IS_VALID == null) return null;

        try {
            Object attachment = TRINKETS_GET_ATTACHMENT.invoke(null, player);
            if (attachment == null) return null;
            Object access = TRINKETS_GET_SLOT_ACCESS.invoke(attachment, "chest/elytra", 0);
            if (access == null || !(TRINKET_ACCESS_IS_VALID.invoke(access) instanceof Boolean valid) || !valid) {
                return null;
            }
            return access;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure("[elytraslot] Failed to access the dedicated Trinkets elytra slot", ex);
            return null;
        }
    }

    private static void markTrinketsAccessChanged(Object access) throws ReflectiveOperationException {
        if (TRINKET_ACCESS_INVENTORY == null) return;
        Object inventory = TRINKET_ACCESS_INVENTORY.invoke(access);
        if (inventory instanceof Container container) {
            container.setChanged();
        }
    }

    private static void warnTrinketsFailure(String message, Throwable ex) {
        if (!warnedTrinketsQueryFailure) {
            warnedTrinketsQueryFailure = true;
            ElytraSlotConstants.LOGGER.warn(message, ex);
        }
    }
}
