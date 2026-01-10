package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import org.lwjgl.input.Mouse;

public final class Utils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Utils() {}

    /** Returns true if Minecraft instance or player/world is null. */
    public static boolean nullCheck() {
        return mc == null || mc.thePlayer == null || mc.theWorld == null;
    }

    /** Returns true when player is currently trying to combo (holding right mouse button typically). */
    public static boolean tryingToCombo() {
        // Heuristic: right mouse button held (use game settings or Mouse)
        try {
            return Mouse.isButtonDown(1) || mc.gameSettings.keyBindUseItem.isKeyDown();
        } catch (Throwable t) {
            return Mouse.isButtonDown(1);
        }
    }

    /** Returns true if player is holding a weapon (sword) or tool depending on your project's logic. */
    public static boolean holdingWeapon() {
        if (nullCheck()) return false;
        Item held = mc.thePlayer.getHeldItem() != null ? mc.thePlayer.getHeldItem().getItem() : null;
        if (held == null) return false;
        // treat swords as weapons; tools are considered separately in your code via ItemUtil
        return (held instanceof ItemSword) || (held instanceof ItemTool);
    }
}
