package com.warwa.elytraslot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public class ElytraSlotUtil {
    private static final Method TRINKETS_GET_ATTACHMENT = findTrinketsGetAttachment();
    private static final Method TRINKETS_IS_EQUIPPED = findTrinketsIsEquipped();

    public static boolean isElytraLike(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.GLIDER);
    }

    public static boolean hasExternalElytra(Player player) {
        return hasTrinketsElytra(player);
    }

    private static boolean hasTrinketsElytra(Player player) {
        if (TRINKETS_GET_ATTACHMENT == null || TRINKETS_IS_EQUIPPED == null) return false;

        try {
            Object attachment = TRINKETS_GET_ATTACHMENT.invoke(null, player);
            if (attachment == null) return false;

            Predicate<ItemStack> predicate = ElytraSlotUtil::isElytraLike;
            Object result = TRINKETS_IS_EQUIPPED.invoke(attachment, predicate, true);
            return result instanceof Boolean equipped && equipped;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            ElytraSlotConstants.LOGGER.warn(
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
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but getAttachment signature was not recognized", ex);
            return null;
        }
    }

    private static Method findTrinketsIsEquipped() {
        try {
            Class<?> attachment = Class.forName("eu.pb4.trinkets.api.TrinketAttachment");
            return attachment.getMethod("isEquipped", Predicate.class, boolean.class);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            ElytraSlotConstants.LOGGER.warn("[elytraslot] Trinkets API found, but isEquipped signature was not recognized", ex);
            return null;
        }
    }
}
