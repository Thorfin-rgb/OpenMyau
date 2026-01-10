package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean clickPending = false;
    private long clickDelay = 0L;
    public final IntProperty minCPS = new IntProperty("min-cps", 8, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-cps", 12, 1, 20);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
    public final FloatProperty range = new FloatProperty("range", 3.0F, 3.0F, 8.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxVertical = new FloatProperty("hit-box-vertical", 0.1F, 0.0F, 1.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxHorizontal = new FloatProperty("hit-box-horizontal", 0.2F, 0.0F, 1.0F, this.breakBlocks::getValue);
    public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
    public final BooleanProperty onlyPlayers = new BooleanProperty("only-players", true, this.blockHit::getValue);
    public final BooleanProperty onRightMBHold = new BooleanProperty("on-right-mb-hold", true, this.blockHit::getValue);
    public final IntProperty waitMsMin = new IntProperty("wait-ms-min", 110, 1, 500, this.blockHit::getValue);
    public final IntProperty waitMsMax = new IntProperty("wait-ms-max", 150, 1, 500, this.blockHit::getValue);
    public final IntProperty hitPerMin = new IntProperty("hit-per-min", 1, 1, 10, this.blockHit::getValue);
    public final IntProperty hitPerMax = new IntProperty("hit-per-max", 1, 1, 10, this.blockHit::getValue);
    public final IntProperty postDelayMin = new IntProperty("post-delay-min", 10, 0, 500, this.blockHit::getValue);
    public final IntProperty postDelayMax = new IntProperty("post-delay-max", 40, 0, 500, this.blockHit::getValue);
    public final FloatProperty chance = new FloatProperty("chance", 100.0F, 0.0F, 100.0F, this.blockHit::getValue);
    public final IntProperty eventType = new IntProperty("event-type", 1, 0, 1, this.blockHit::getValue); // 0: PRE, 1: POST
    // New features and improvements
    public final BooleanProperty jitter = new BooleanProperty("jitter", false); // Adds small random mouse movements for anti-ban
    public final FloatProperty maxJitter = new FloatProperty("max-jitter", 0.5F, 0.0F, 2.0F, this.jitter::getValue); // Max jitter amount
    public final BooleanProperty pauseOnLowHealth = new BooleanProperty("pause-on-low-health", false); // Pauses autoclicker if health is low
    public final FloatProperty lowHealthThreshold = new FloatProperty("low-health-threshold", 5.0F, 1.0F, 20.0F, this.pauseOnLowHealth::getValue); // Health threshold to pause
    public final BooleanProperty smartBlockHit = new BooleanProperty("smart-block-hit", false, this.blockHit::getValue); // Only blocks when enemy is attacking back
    public final FloatProperty blockHitRange = new FloatProperty("block-hit-range", 3.0F, 1.0F, 6.0F, this.blockHit::getValue); // Separate range for blockhit
    public final BooleanProperty randomizeDelays = new BooleanProperty("randomize-delays", true); // Adds slight randomization to click delays for more natural feel

    private static boolean executingAction = false;
    private static boolean hitCoolDown = false;
    private static boolean alreadyHit = false;
    private static boolean safeGuard = false;
    private static int hitTimeout = 0;
    private static int hitsWaited = 0;
    private final CoolDown actionTimer = new CoolDown(0);
    private final CoolDown postDelayTimer = new CoolDown(0);
    private boolean waitingForPostDelay = false;
    private final String[] MODES = new String[]{"PRE", "POST"};

    private long getNextClickDelay() {
        long baseDelay = 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
        if (this.randomizeDelays.getValue()) {
            // Add up to 10% randomization
            long randomOffset = (long) (baseDelay * 0.1 * (ThreadLocalRandom.current().nextDouble() - 0.5));
            return Math.max(1, baseDelay + randomOffset);
        }
        return baseDelay;
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canClick() {
        if (this.pauseOnLowHealth.getValue() && mc.thePlayer.getHealth() < this.lowHealthThreshold.getValue()) {
            return false; // Pause if health is low
        }
        if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (this.breakBlocks.getValue() && this.isBreakingBlock() && !this.hasValidTarget()) {
                GameType gameType12 = mc.playerController.getCurrentGameType();
                return gameType12 != GameType.SURVIVAL && gameType12 != GameType.CREATIVE;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else {
                float borderSize = entityPlayer.getCollisionBorderSize();
                return RotationUtil.rayTrace(entityPlayer.getEntityBoundingBox().expand(
                        borderSize + this.hitBoxHorizontal.getValue(),
                        borderSize + this.hitBoxVertical.getValue(),
                        borderSize + this.hitBoxHorizontal.getValue()
                ), mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, this.range.getValue()) != null;
            }
        } else {
            return false;
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(this::isValidTarget);
    }

    private void finishCombo() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        KeyBindUtil.setKeyBindState(key, false);
    }

    private void startCombo() {
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            KeyBindUtil.setKeyBindState(key, true);
        }
    }

    private void guiUpdate() {
        if (this.waitMsMin.getValue() > this.waitMsMax.getValue()) {
            this.waitMsMax.setValue(this.waitMsMin.getValue());
        }
        if (this.hitPerMin.getValue() > this.hitPerMax.getValue()) {
            this.hitPerMax.setValue(this.hitPerMin.getValue());
        }
        if (this.postDelayMin.getValue() > this.postDelayMax.getValue()) {
            this.postDelayMax.setValue(this.postDelayMin.getValue());
        }
    }

    private void applyJitter() {
        if (this.jitter.getValue()) {
            float jitterAmount = (float) (this.maxJitter.getValue() * (ThreadLocalRandom.current().nextDouble() - 0.5) * 2);
            mc.thePlayer.rotationYaw += jitterAmount;
            mc.thePlayer.rotationPitch += jitterAmount * 0.5F; // Less vertical jitter
        }
    }

    private boolean shouldSmartBlock(Entity target) {
        if (!this.smartBlockHit.getValue()) return true;
        // Simple check: block if target is facing player or has low health (indicating aggression)
        return target instanceof EntityPlayer && ((EntityPlayer) target).getHealth() < 10.0F; // Example: block if enemy health is low
    }

    public AutoClicker() {
        super("AutoClicker", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.clickDelay > 0L) {
                this.clickDelay -= 50L;
            }
            if (mc.currentScreen != null) {
                this.clickPending = false;
            } else {
                if (this.clickPending) {
                    this.clickPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (this.isEnabled() && this.canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (!mc.thePlayer.isUsingItem()) {
                        while (this.clickDelay <= 0L) {
                            this.clickPending = true;
                            this.clickDelay = this.clickDelay + this.getNextClickDelay();
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                            applyJitter(); // Apply jitter on click
                        }
                    }
                }
            }

            // BlockHit logic
            if (this.blockHit.getValue()) {
                if (!Utils.nullCheck()) return;

                if (this.onRightMBHold.getValue() && !Utils.tryingToCombo()) {
                    if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                        safeGuard = true;
                        finishCombo();
                    }
                    return;
                }
                if (waitingForPostDelay) {
                    if (postDelayTimer.hasFinished()) {
                        executingAction = true;
                        startCombo();
                        waitingForPostDelay = false;
                        if (safeGuard) safeGuard = false;
                        actionTimer.start();
                    }
                    return;
                }

                if (executingAction) {
                    if (actionTimer.hasFinished()) {
                        executingAction = false;
                        finishCombo();
                        return;
                    } else {
                        return;
                    }
                }

                if (this.onRightMBHold.getValue() && Utils.tryingToCombo()) {
                    if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
                        if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                            safeGuard = true;
                            finishCombo();
                        }
                        return;
                    } else {
                        Entity target = mc.objectMouseOver.entityHit;
                        if (target.isDead) {
                            if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                                safeGuard = true;
                                finishCombo();
                            }
                            return;
                        }
                    }
                }

                if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof Entity && Mouse.isButtonDown(0)) {
                    Entity target = mc.objectMouseOver.entityHit;
                    if (target.isDead) {
                        if (this.onRightMBHold.getValue() && Mouse.isButtonDown(1) && Mouse.isButtonDown(0)) {
                            if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                                safeGuard = true;
                                finishCombo();
                            }
                        }
                        return;
                    }

                    if (mc.thePlayer.getDistanceToEntity(target) <= this.blockHitRange.getValue() && shouldSmartBlock(target)) {
                        if ((target.hurtResistantTime >= 10 && MODES[this.eventType.getValue()].equals(MODES[1])) || (target.hurtResistantTime <= 10 && MODES[this.eventType.getValue()].equals(MODES[0]))) {

                            if (this.onlyPlayers.getValue()) {
                                if (!(target instanceof EntityPlayer)) {
                                    return;
                                }
                            }

                            if (hitCoolDown && !alreadyHit) {
                                hitsWaited++;
                                if (hitsWaited >= hitTimeout) {
                                    hitCoolDown = false;
                                    hitsWaited = 0;
                                } else {
                                    alreadyHit = true;
                                    return;
                                }
                            }

                            if (!(this.chance.getValue() == 100 || Math.random() <= this.chance.getValue() / 100))
                                return;

                            if (!alreadyHit) {
                                guiUpdate();
                                if (this.hitPerMin.getValue() == this.hitPerMax.getValue()) {
                                    hitTimeout = this.hitPerMin.getValue();
                                } else {
                                    hitTimeout = ThreadLocalRandom.current().nextInt(this.hitPerMin.getValue(), this.hitPerMax.getValue() + 1);
                                }
                                hitCoolDown = true;
                                hitsWaited = 0;

                                actionTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(this.waitMsMin.getValue(), this.waitMsMax.getValue() + 0.01));
                                if (this.postDelayMax.getValue() != 0) {
                                    postDelayTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(this.postDelayMin.getValue(), this.postDelayMax.getValue() + 0.01));
                                    postDelayTimer.start();
                                    waitingForPostDelay = true;
                                } else {
                                    executingAction = true;
                                    startCombo();
                                    actionTimer.start();
                                    alreadyHit = true;
                                    if (safeGuard) safeGuard = false;
                                }
                                alreadyHit = true;
                            }
                        } else {
                            if (alreadyHit) {
                                alreadyHit = false;
                            }

                            if (safeGuard) safeGuard = false;
                        }
                    }
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onCLick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (!this.clickPending) {
                this.clickDelay = this.clickDelay + this.getNextClickDelay();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.clickDelay = 0L;
    }

    @Override
    public void verifyValue(String mode) {
        if (this.minCPS.getName().equals(mode)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.maxCPS.setValue(this.minCPS.getValue());
            }
        } else if (this.maxCPS.getName().equals(mode)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.minCPS.setValue(this.maxCPS.getValue());
            }
        } else if (this.waitMsMin.getName().equals(mode)) {
            if (this.waitMsMin.getValue() > this.waitMsMax.getValue()) {
                this.waitMsMax.setValue(this.waitMsMin.getValue());
            }
        } else if (this.waitMsMax.getName().equals(mode)) {
            if (this.waitMsMin.getValue() > this.waitMsMax.getValue()) {
                this.waitMsMin.setValue(this.waitMsMax.getValue());
            }
        } else if (this.hitPerMin.getName().equals(mode)) {
            if (this.hitPerMin.getValue() > this.hitPerMax.getValue()) {
                this.hitPerMax.setValue(this.hitPerMin.getValue());
            }
        } else if (this.hitPerMax.getName().equals(mode)) {
            if (this.hitPerMin.getValue() > this.hitPerMax.getValue()) {
                this.hitPerMin.setValue(this.hitPerMax.getValue());
            }
        } else if (this.postDelayMin.getName().equals(mode)) {
            if (this.postDelayMin.getValue() > this.postDelayMax.getValue()) {
                this.postDelayMax.setValue(this.postDelayMin.getValue());
            }
        } else if (this.postDelayMax.getName().equals(mode)) {
            if (this.postDelayMin.getValue() > this.postDelayMax.getValue()) {
                this.postDelayMin.setValue(this.postDelayMax.getValue());
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minCPS.getValue(), this.maxCPS.getValue())
                ? new String[]{this.minCPS.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minCPS.getValue(), this.maxCPS.getValue())};
    }
}
