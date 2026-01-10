package myau.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

public final class Utils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Utils() {}

    public static boolean nullCheck() {
        return mc == null || mc.thePlayer == null || mc.theWorld == null;
    }

    public static boolean tryingToCombo() {
        try {
            return Mouse.isButtonDown(1) || mc.gameSettings.keyBindUseItem.isKeyDown();
        } catch (Throwable t) {
            return Mouse.isButtonDown(1);
        }
    }

    public static boolean holdingWeapon() {
        if (nullCheck()) return false;
        Item held = mc.thePlayer.getHeldItem() != null ? mc.thePlayer.getHeldItem().getItem() : null;
        if (held == null) return false;
        return (held instanceof ItemSword) || (held instanceof ItemTool);
    }
}
