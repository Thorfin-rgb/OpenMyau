package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.module.modules.KillAura;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

public class AutoTool extends Module {
    // Use deobfuscated accessor consistent with other modules
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;
    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);

    public AutoTool() {
        super("AutoTool", false);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) {
            return false;
        }
        return TeamUtil.isEntityLoaded((Entity)killAura.getTarget()) && killAura.isAttackAllowed();
    }

    private int findShearsSlot() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = AutoTool.mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemShears)) continue;
            return i;
        }
        return -1;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            // reset if current slot changed externally
            if (this.currentToolSlot != -1 && this.currentToolSlot != AutoTool.mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }

            // Check objectMouseOver and relevant conditions using deobfuscated fields/methods
            if (AutoTool.mc.objectMouseOver != null
                    && AutoTool.mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && AutoTool.mc.gameSettings.keyBindAttack.isKeyDown()
                    && !AutoTool.mc.thePlayer.isUsingItem()
                    && !this.isKillAura()) {

                // Compare block at targeted position to leaves (field_150325_L -> leaves)
                if (AutoTool.mc.theWorld.getBlockState(AutoTool.mc.objectMouseOver.getBlockPos()).getBlock() == Blocks.leaves) {
                    int slot;
                    if (this.tickDelayCounter >= (Integer)this.switchDelay.getValue()
                            && (!((Boolean)this.sneakOnly.getValue()).booleanValue() || KeyBindUtil.isKeyDown(AutoTool.mc.gameSettings.keyBindSneak.getKeyCode()))
                            && (slot = this.findShearsSlot()) != -1
                            && AutoTool.mc.thePlayer.inventory.currentItem != slot) {

                        if (this.previousSlot == -1) {
                            this.previousSlot = AutoTool.mc.thePlayer.inventory.currentItem;
                        }
                        AutoTool.mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                    }
                    ++this.tickDelayCounter;
                } else {
                    ++this.tickDelayCounter;
                }
            } else {
                if (((Boolean)this.switchBack.getValue()).booleanValue() && this.previousSlot != -1) {
                    AutoTool.mc.thePlayer.inventory.currentItem = this.previousSlot;
                }
                this.currentToolSlot = -1;
                this.previousSlot = -1;
                this.tickDelayCounter = 0;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
    }
}
