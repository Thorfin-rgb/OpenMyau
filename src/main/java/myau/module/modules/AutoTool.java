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

public class AutoTool
extends Module {
    private static final Minecraft mc = Minecraft.func_71410_x();
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
        KillAura killAura = (KillAura)Myau.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) {
            return false;
        }
        return TeamUtil.isEntityLoaded((Entity)killAura.getTarget()) && killAura.isAttackAllowed();
    }

    private int findShearsSlot() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = AutoTool.mc.field_71439_g.field_71071_by.func_70301_a(i);
            if (stack == null || !(stack.func_77973_b() instanceof ItemShears)) continue;
            return i;
        }
        return -1;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.currentToolSlot != -1 && this.currentToolSlot != AutoTool.mc.field_71439_g.field_71071_by.field_70461_c) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }
            if (AutoTool.mc.field_71476_x != null && AutoTool.mc.field_71476_x.field_72313_a == MovingObjectPosition.MovingObjectType.BLOCK && AutoTool.mc.field_71474_y.field_74312_F.func_151470_d() && !AutoTool.mc.field_71439_g.func_71039_bw() && !this.isKillAura()) {
                if (AutoTool.mc.field_71441_e.func_180495_p(AutoTool.mc.field_71476_x.func_178782_a()).func_177230_c() == Blocks.field_150325_L) {
                    int slot;
                    if (this.tickDelayCounter >= (Integer)this.switchDelay.getValue() && (!((Boolean)this.sneakOnly.getValue()).booleanValue() || KeyBindUtil.isKeyDown(AutoTool.mc.field_71474_y.field_74311_E.func_151463_i())) && (slot = this.findShearsSlot()) != -1 && AutoTool.mc.field_71439_g.field_71071_by.field_70461_c != slot) {
                        if (this.previousSlot == -1) {
                            this.previousSlot = AutoTool.mc.field_71439_g.field_71071_by.field_70461_c;
                        }
                        AutoTool.mc.field_71439_g.field_71071_by.field_70461_c = this.currentToolSlot = slot;
                    }
                    ++this.tickDelayCounter;
                } else {
                    ++this.tickDelayCounter;
                }
            } else {
                if (((Boolean)this.switchBack.getValue()).booleanValue() && this.previousSlot != -1) {
                    AutoTool.mc.field_71439_g.field_71071_by.field_70461_c = this.previousSlot;
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
