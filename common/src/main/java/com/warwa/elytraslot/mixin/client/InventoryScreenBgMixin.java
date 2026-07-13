package com.warwa.elytraslot.mixin.client;

import com.warwa.elytraslot.ElytraSlotContainer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Left-side elytra-slot panel drawn as a 9-slice from the vanilla inventory texture.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenBgMixin extends AbstractContainerScreen<InventoryMenu> {

    private InventoryScreenBgMixin() { super(null, null, null); }

    private static final Identifier INVENTORY_TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/inventory.png");

    private static final int INV_W  = 176;
    private static final int INV_H  = 166;
    private static final int BORDER = 3;
    private static final int PANEL_BODY = 0xFFC6C6C6;

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (elytraslot$hasStandaloneSlot() && mx >= xo - 33 && mx < xo && my >= yo && my < yo + 32) {
            return false;
        }
        return super.hasClickedOutside(mx, my, xo, yo);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void elytraslot(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!elytraslot$hasStandaloneSlot()) return;

        int x = this.leftPos;
        int y = this.topPos;

        int panelW = 32;
        int panelH = 32;
        int panelX = x - panelW - 1;
        int panelY = y;

        int innerW = panelW - 2 * BORDER;
        int innerH = panelH - 2 * BORDER;
        int rX = panelX + panelW - BORDER;
        int bY = panelY + panelH - BORDER;
        int srcR = INV_W - BORDER;
        int srcB = INV_H - BORDER;

        blit(graphics, panelX, panelY, 0,    0,    BORDER, BORDER);
        blit(graphics, rX,     panelY, srcR, 0,    BORDER, BORDER);
        blit(graphics, panelX, bY,     0,    srcB, BORDER, BORDER);
        blit(graphics, rX,     bY,     srcR, srcB, BORDER, BORDER);

        blit(graphics, panelX + BORDER, panelY,          BORDER, 0,      innerW, BORDER);
        blit(graphics, panelX + BORDER, bY,              BORDER, srcB,   innerW, BORDER);
        blit(graphics, panelX,          panelY + BORDER, 0,      BORDER, BORDER, innerH);
        blit(graphics, rX,              panelY + BORDER, srcR,   BORDER, BORDER, innerH);

        graphics.fill(panelX + BORDER, panelY + BORDER, panelX + panelW - BORDER, panelY + panelH - BORDER, PANEL_BODY);

        blit(graphics, panelX + BORDER,                 panelY + BORDER,                 BORDER,          BORDER,          1, 1);
        blit(graphics, panelX + panelW - BORDER - 1,    panelY + panelH - BORDER - 1,    srcR - 1,        srcB - 1,        1, 1);

        int slotX = x - 26;
        int slotY = y + 7;
        blit(graphics, slotX, slotY, 7, 7, 18, 18);
    }

    private static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, x, y, (float) u, (float) v, w, h, 256, 256);
    }

    private boolean elytraslot$hasStandaloneSlot() {
        for (Slot slot : this.menu.slots) {
            if (slot.container instanceof ElytraSlotContainer) return true;
        }
        return false;
    }
}
