package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private EntityPlayer currentTarget = null; // For single target mode
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 10.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 10.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);
    // New features
    public final BooleanProperty singleTarget = new BooleanProperty("single-target", true); // Stick to one target once engaged
    public final BooleanProperty adaptiveSpeed = new BooleanProperty("adaptive-speed", false); // Adjust speed based on distance
    public final FloatProperty adaptiveMultiplier = new FloatProperty("adaptive-multiplier", 1.5F, 0.5F, 3.0F, this.adaptiveSpeed::getValue); // Speed multiplier for close targets
    public final FloatProperty closeRangeThreshold = new FloatProperty("close-range-threshold", 2.0F, 1.0F, 5.0F, this.adaptiveSpeed::getValue); // Distance for adaptive speed

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.range.getValue()) {
                return false;
            } else if (RotationUtil.angleToEntity(entityPlayer) > (float) this.fov.getValue()) {
                return false;
            } else if (RotationUtil.rayTrace(entityPlayer) != null) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.team.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botChecks.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean isInReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private EntityPlayer getBestTarget(List<EntityPlayer> targets) {
        if (this.singleTarget.getValue() && this.currentTarget != null && this.isValidTarget(this.currentTarget)) {
            return this.currentTarget; // Stick to current target if valid
        }
        // Otherwise, find the best target
        EntityPlayer best = targets.stream()
                .filter(this::isValidTarget)
                .min(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .orElse(null);
        if (best != null) {
            this.currentTarget = best; // Update current target
        }
        return best;
    }

    private float getAdaptiveSpeed(float baseSpeed, EntityPlayer target) {
        if (this.adaptiveSpeed.getValue() && target != null) {
            double distance = RotationUtil.distanceToEntity(target);
            if (distance <= this.closeRangeThreshold.getValue()) {
                return baseSpeed * this.adaptiveMultiplier.getValue();
            }
        }
        return baseSpeed;
    }

    public AimAssist() {
        super("AimAssist", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST && mc.currentScreen == null) {
            if (!(Boolean) this.weaponOnly.getValue()
                    || ItemUtil.hasRawUnbreakingEnchant()
                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
                boolean attacking = PlayerUtil.isAttacking();
                if (!attacking || !this.isLookingAtBlock()) {
                    if (attacking || !this.timer.hasTimeElapsed(350L)) {
                        List<EntityPlayer> inRange = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .collect(Collectors.toList());
                        if (!inRange.isEmpty()) {
                            if (inRange.stream().anyMatch(this::isInReach)) {
                                inRange.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
                            }
                            EntityPlayer player = this.getBestTarget(inRange);
                            if (player != null && !(RotationUtil.distanceToEntity(player) <= 0.0)) {
                                AxisAlignedBB axisAlignedBB = player.getEntityBoundingBox();
                                double collisionBorderSize = player.getCollisionBorderSize();
                                float[] rotation = RotationUtil.getRotationsToBox(
                                        axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize),
                                        mc.thePlayer.rotationYaw,
                                        mc.thePlayer.rotationPitch,
                                        180.0F,
                                        (float) this.smoothing.getValue() / 100.0F
                                );
                                float yaw = Math.min(Math.abs(this.getAdaptiveSpeed(this.hSpeed.getValue(), player)), 10.0F);
                                float pitch = Math.min(Math.abs(this.getAdaptiveSpeed(this.vSpeed.getValue(), player)), 10.0F);
                                Myau.rotationManager
                                        .setRotation(
                                                mc.thePlayer.rotationYaw + (rotation[0] - mc.thePlayer.rotationYaw) * 0.1F * yaw,
                                                mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                                                0,
                                                false
                                        );
                            }
                        } else {
                            this.currentTarget = null; // Reset if no valid targets
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode() && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            this.timer.reset();
        }
    }

    @Override
    public void onDisabled() {
        this.currentTarget = null; // Reset target on disable
    }
}
