package com.warwa.elytraslot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

public class ElytraSlotUtil {
    public static final String TRINKETS_ELYTRA_SLOT_ID = "elytraslot/elytra";
    public static final Identifier TRINKETS_ELYTRA_ONLY_PREDICATE_ID = Identifier.fromNamespaceAndPath("elytraslot", "elytra_only");

    private static final int MAX_TRINKETS_SLOT_SCAN = 32;

    private static final Method TRINKETS_GET_ATTACHMENT = findTrinketsGetAttachment();
    private static final Method TRINKETS_GET_SLOT_ACCESS = findTrinketsGetSlotAccess();
    private static final Method TRINKETS_IS_EQUIPPED = findTrinketsIsEquipped();
    private static final Method TRINKET_SLOT_ACCESS_GET = findTrinketSlotAccessGet();
    private static final Method TRINKET_SLOT_ACCESS_SET = findTrinketSlotAccessSet();
    private static final Method TRINKET_SLOT_ACCESS_IS_VALID = findTrinketSlotAccessIsValid();

    private static boolean warnedTrinketsQueryFailure = false;
    private static boolean trinketsElytraOnlyPredicateRegistered = false;

    public static boolean isElytraLike(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.GLIDER);
    }

    public static boolean isTrinketsAvailable() {
        return TRINKETS_GET_ATTACHMENT != null;
    }

    /**
     * Registers the predicate used by the dedicated Trinkets slot data file.
     *
     * <p>This keeps Trinkets optional: the implementation talks to Trinkets via
     * reflection and a dynamic proxy instead of compiling against Trinkets API types.
     * The predicate only accepts elytra-like items, using Minecraft's GLIDER data
     * component so vanilla elytras and compatible modded elytras can equip, while
     * unrelated Trinkets items cannot use the dedicated elytra slot.
     */
    public static void registerTrinketsElytraOnlyPredicate() {
        if (trinketsElytraOnlyPredicateRegistered || !isTrinketsAvailable()) return;

        try {
            Class<?> api = Class.forName("eu.pb4.trinkets.api.TrinketsApi");
            Class<?> predicateType = Class.forName("eu.pb4.trinkets.api.TrinketsApi$TrinketPredicate");
            Method register = api.getMethod("registerTrinketPredicate", Identifier.class, predicateType);

            Object predicate = Proxy.newProxyInstance(
                predicateType.getClassLoader(),
                new Class<?>[] { predicateType },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ElytraSlotTrinketsElytraOnlyPredicate";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }

                    if ("test".equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof ItemStack stack) {
                        return isElytraLike(stack);
                    }

                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }
            );

            register.invoke(null, TRINKETS_ELYTRA_ONLY_PREDICATE_ID, predicate);
            trinketsElytraOnlyPredicateRegistered = true;
            ElytraSlotConstants.LOGGER.info(
                "[elytraslot] Registered Trinkets predicate {} for dedicated elytra slot",
                TRINKETS_ELYTRA_ONLY_PREDICATE_ID
            );
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            ElytraSlotConstants.LOGGER.warn(
                "[elytraslot] Trinkets API found, but failed to register the elytra-only Trinkets predicate. The dedicated Trinkets slot will reject inserts until this is fixed.",
                ex
            );
        }
    }

    /**
     * Returns true when Trinkets Updated is installed and the dedicated
     * {@code elytraslot/elytra} slot from this mod's data files is available to the player.
     */
    public static boolean usesTrinketsSlot(Player player) {
        if (player == null) return false;
        return isTrinketsAvailable() && findDedicatedTrinketsElytraSlot(player) != null;
    }

    public static boolean hasExternalElytra(Player player) {
        return hasTrinketsElytra(player);
    }

    /**
     * Moves a previously stored standalone-slot elytra into the dedicated Trinkets slot.
     * The caller is responsible for clearing the original standalone slot after this
     * returns true.
     */
    public static boolean tryMoveCustomElytraToTrinkets(Player player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return true;
        if (!isElytraLike(stack)) return false;

        Object access = findEmptyDedicatedTrinketsElytraSlot(player);
        if (access == null) return false;

        return setTrinketsSlotStack(access, stack.copy());
    }

    private static boolean hasTrinketsElytra(Player player) {
        if (TRINKETS_GET_ATTACHMENT == null || TRINKETS_IS_EQUIPPED == null) return false;

        try {
            Object attachment = TRINKETS_GET_ATTACHMENT.invoke(null, player);
            if (attachment == null) return false;

            Predicate<ItemStack> predicate = ElytraSlotUtil::isElytraLike;
            Object result = TRINKETS_IS_EQUIPPED.invoke(attachment, predicate, true);
            return result instanceof Boolean equipped && equipped;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure(
                "[elytraslot] Failed to query Trinkets equipped elytra state; falling back to standalone behavior",
                ex
            );
            return false;
        }
    }

    private static Object findDedicatedTrinketsElytraSlot(Player player) {
        return findDedicatedTrinketsElytraSlot(player, false);
    }

    private static Object findEmptyDedicatedTrinketsElytraSlot(Player player) {
        return findDedicatedTrinketsElytraSlot(player, true);
    }

    private static Object findDedicatedTrinketsElytraSlot(Player player, boolean requireEmpty) {
        if (TRINKETS_GET_ATTACHMENT == null
            || TRINKETS_GET_SLOT_ACCESS == null
            || TRINKET_SLOT_ACCESS_GET == null
            || TRINKET_SLOT_ACCESS_IS_VALID == null) {
            return null;
        }

        try {
            Object attachment = TRINKETS_GET_ATTACHMENT.invoke(null, player);
            if (attachment == null) return null;

            for (int i = 0; i < MAX_TRINKETS_SLOT_SCAN; i++) {
                Object access = TRINKETS_GET_SLOT_ACCESS.invoke(attachment, TRINKETS_ELYTRA_SLOT_ID, i);
                if (access == null) {
                    if (i == 0) return null;
                    continue;
                }
                if (!isValidTrinketsSlotAccess(access)) continue;
                if (!requireEmpty || getTrinketsSlotStack(access).isEmpty()) return access;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure(
                "[elytraslot] Failed to query the dedicated Trinkets elytra slot; falling back to standalone behavior",
                ex
            );
        }
        return null;
    }

    private static boolean isValidTrinketsSlotAccess(Object access) throws ReflectiveOperationException {
        Object result = TRINKET_SLOT_ACCESS_IS_VALID.invoke(access);
        return result instanceof Boolean valid && valid;
    }

    private static ItemStack getTrinketsSlotStack(Object access) throws ReflectiveOperationException {
        Object result = TRINKET_SLOT_ACCESS_GET.invoke(access);
        return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private static boolean setTrinketsSlotStack(Object access, ItemStack stack) {
        if (TRINKET_SLOT_ACCESS_SET == null) return false;
        try {
            Object result = TRINKET_SLOT_ACCESS_SET.invoke(access, stack);
            return result instanceof Boolean set && set;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnTrinketsFailure(
                "[elytraslot] Failed to move the standalone elytra into the dedicated Trinkets slot",
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

    private static Method findTrinketsIsEquipped() {
        try {
            Class<?> attachment = Class.forName("eu.pb4.trinkets.api.TrinketAttachment");
            return attachment.getMethod("isEquipped", Predicate.class, boolean.class);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but isEquipped signature was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketSlotAccessGet() {
        try {
            Class<?> access = Class.forName("eu.pb4.trinkets.api.TrinketSlotAccess");
            return access.getMethod("get");
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but TrinketSlotAccess#get was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketSlotAccessSet() {
        try {
            Class<?> access = Class.forName("eu.pb4.trinkets.api.TrinketSlotAccess");
            return access.getMethod("set", ItemStack.class);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but TrinketSlotAccess#set was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketSlotAccessIsValid() {
        try {
            Class<?> access = Class.forName("eu.pb4.trinkets.api.TrinketSlotAccess");
            return access.getMethod("isValid");
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but TrinketSlotAccess#isValid was not recognized", ex);
            return null;
        }
    }

    private static void warnTrinketsFailure(String message, Throwable ex) {
        if (!warnedTrinketsQueryFailure) {
            warnedTrinketsQueryFailure = true;
            ElytraSlotConstants.LOGGER.warn(message, ex);
        }
    }
}
