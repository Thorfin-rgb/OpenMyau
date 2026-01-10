package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.util.TimerUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    public final FloatProperty delay = new FloatProperty("delay", 5.5F, 0.0F, 10.0F);
    public final FloatProperty duration = new FloatProperty("duration", 1.5F, 1.0F, 5.0F);
    // New adaptive features
    public final BooleanProperty adaptiveMode = new BooleanProperty("adaptive-mode", true); // Enables adaptive adjustments based on situation
    public final FloatProperty closeRangeMultiplier = new FloatProperty("close-range-multiplier", 0.8F, 0.5F, 1.5F, this.adaptiveMode::getValue); // Reduce delay/duration when close to target
    public final FloatProperty lowHealthMultiplier = new FloatProperty("low-health-multiplier", 1.2F, 0.5F, 2.0F, this.adaptiveMode::getValue); // Increase delay/duration when health is low
    public final FloatProperty lowHealthThreshold = new FloatProperty("low-health-threshold", 10.0F, 1.0F, 20.0F, this.adaptiveMode::getValue); // Health threshold for low health adaptation

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying) && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    private float getAdaptiveMultiplier() {
        if (!this.adaptiveMode.getValue()) return 1.0F;
        
        float multiplier = 1.0F;
        
        // Close range adaptation: if target is within 3 blocks, apply close range multiplier
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            float distance = mc.thePlayer.getDistanceToEntity(mc.objectMouseOver.entityHit);
            if (distance <= 3.0F) {
                multiplier *= this.closeRangeMultiplier.getValue();
            }
        }
        
        // Low health adaptation: if health is below threshold, apply low health multiplier
        if (mc.thePlayer.getHealth() < this.lowHealthThreshold.getValue()) {
            multiplier *= this.lowHealthMultiplier.getValue();
        }
        
        return multiplier;
    }

    public Wtap() {
        super("WTap", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.active) {
            if (!this.stopForward && !this.canTrigger()) {
                this.active = false;
                this.delayTicks = 0L;
                this.durationTicks = 0L;
            } else if (this.delayTicks > 0L) {
                this.delayTicks -= 50L;
            } else {
                if (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                    this.stopForward = true;
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                }
                if (this.durationTicks <= 0L) {
                    this.active = false;
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) event.getPacket()).getAction() == Action.ATTACK
                    && !this.active
                    && this.timer.hasTimeElapsed(500L)
                    && mc.thePlayer.isSprinting()) {
                this.timer.reset();
                this.active = true;
                this.stopForward = false;
                float multiplier = this.getAdaptiveMultiplier();
                this.delayTicks = (long) (50.0F * this.delay.getValue() * multiplier);
                this.durationTicks = (long) (50.0F * this.duration.getValue() * multiplier);
            }
        }
    }
}
