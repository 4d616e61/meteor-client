/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.Map;


public class AutoHighwayElytrafly extends Module {
    public enum HighwayAxis{
        XPlus,
        XMinus,
        ZPlus,
        ZMinus,
        XPlusZPlus,
        XPlusZMinus,
        XMinusZPlus,
        XMinusZMinus
    }
    public enum RotationLockMode{
        Position,
        PlayerRotation,
        Manual,
        None,
    }
    //region UISettings
    public AutoHighwayElytrafly() {
        super(Categories.Movement, "auto-highway-elytrafly", "Automatically unstucks yourself when travelling on highways");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> detectHighway = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-highway")
        .description("Whether to automatically detect highways.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> headToOrigin = sgGeneral.add(new BoolSetting.Builder()
        .name("to-origin")
        .description("Whether to head to origin or not.")
        .defaultValue(false)
        .build()
    );

    public final Setting<HighwayAxis> highwayAxisSetting = sgGeneral.add(new EnumSetting.Builder<HighwayAxis>()
        .name("highway-axis")
        .description("The direction of the highway(Note that these are directions, NOT actual highways)")
        .defaultValue(HighwayAxis.XPlus)
        .visible(() -> !detectHighway.get())
        .build()
    );

    private final Setting<Integer> xOriginSetting = sgGeneral.add(new IntSetting.Builder()
        .name("x-origin")
        .description("X coordinates of highway origin.")
        .defaultValue(0)
        .sliderRange(-100000, 100000)
        .build()
    );
    private final Setting<Integer> zOriginSetting = sgGeneral.add(new IntSetting.Builder()
        .name("z-origin")
        .description("Z coordinates of highway origin.")
        .defaultValue(0)
        .sliderRange(-100000, 100000)
        .build()
    );
    private final Setting<Integer> yTarget = sgGeneral.add(new IntSetting.Builder()
        .name("y-target")
        .description("Y target value.")
        .defaultValue(120)
        .sliderRange(0, 256)
        .build()
    );
    private final Setting<Double> yMaxPosDelta = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-max-positive-delta")
        .description("Maximum accepted y positive delta.")
        .defaultValue(1)
        .sliderRange(0, 3)
        .build()
    );
    private final Setting<Double> yMaxNegDelta = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-max-negative-delta")
        .description("Maximum accepted y negative delta.")
        .defaultValue(0.0)
        .sliderRange(0, 3)
        .build()
    );
    private final Setting<Double> horizontalMaxDelta = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-max-delta")
        .description("Maximum accepted horizontal delta.")
        .defaultValue(3)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Double> stuckDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("stuck-detection-distance")
        .description("Distance used to detect whether the player is stuck.")
        .defaultValue(3)
        .sliderRange(0, 8)
        .build()
    );
    private final Setting<Double> stuckVelocity = sgGeneral.add(new DoubleSetting.Builder()
        .name("stuck-detection-speed")
        .description("Speed(length of player velocity) used to detect whether the player is stuck.")
        .defaultValue(3)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> detectionSpan = sgGeneral.add(new IntSetting.Builder()
        .name("detection-span")
        .description("Stuck detection span, in ticks")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 400)
        .build()
    );
    private final Setting<Double> unstuckMoveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("unstuck-move-distance")
        .description("Distance to move to unstuck the player.")
        .defaultValue(3)
        .sliderRange(0, 8)
        .build()
    );
    public final Setting<RotationLockMode> rotationLockModeSetting = sgGeneral.add(new EnumSetting.Builder<RotationLockMode>()
        .name("rotation-lock-mode")
        .description("Mode of smart player rotation lock.")
        .defaultValue(RotationLockMode.None)
        .build()
    );
    private final Setting<Double> manualRotationYaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-max-negative-delta")
        .description("Maximum accepted y negative delta.")
        .defaultValue(0.0)
        .sliderRange(0, 360)
        .visible(() -> rotationLockModeSetting.get() == RotationLockMode.Manual)
        .build()
    );
    //endregion

    //region Vars
    private LinkedList<Vec3d> posHistory = new LinkedList<>();
    private final ElytraFly eflyModule = Modules.get().get(ElytraFly.class);
    private boolean playerIsStuck = false;
    private boolean startedUnstuck = false;
    private int velocityBelowThresholdTicks = 0;

    private int xOrigin = 0;
    private int zOrigin = 0;

    private Vec3d axisVec = new Vec3d(1,0,0);
    private boolean executedCommand = false;

    private final Vec3d[] nondiags = {
        //x+
        new Vec3d(1,0, 0),
        //x-
        new Vec3d(-1,0, 0),
        //z+
        new Vec3d(0,0, 1),
        //z-
        new Vec3d(0,0, -1)
    };
    private final Vec3d[] diags = {
        //++
        new Vec3d(1,0, 1),
        //+-
        new Vec3d(1,0, -1),
        //-+
        new Vec3d(-1,0, 1),
        //--
        new Vec3d(-1,0, -1)
    };

    public final Map<HighwayAxis, Vec3d> directionVecs = new EnumMap<>(Map.ofEntries(
        Map.entry(HighwayAxis.XPlus,            new Vec3d(1,0, 0)),
        Map.entry(HighwayAxis.XMinus,           new Vec3d(-1,0, 0)),
        Map.entry(HighwayAxis.ZPlus,            new Vec3d(0,0, 1)),
        Map.entry(HighwayAxis.ZMinus,           new Vec3d(0,0, -1)),
        Map.entry(HighwayAxis.XPlusZPlus,       new Vec3d(1,0, 1)),
        Map.entry(HighwayAxis.XPlusZMinus,      new Vec3d(1,0, -1)),
        Map.entry(HighwayAxis.XMinusZPlus,      new Vec3d(-1,0, 1)),
        Map.entry(HighwayAxis.XMinusZMinus,     new Vec3d(-1,0, -1))

    ));


    //endregion

    //region Misc
    private void lockRotation(){
        switch (rotationLockModeSetting.get()){
            case Manual -> mc.player.setYaw(manualRotationYaw.get().floatValue());
            case Position -> mc.player.setYaw((float) Math.toDegrees(Math.atan2(axisVec.z, axisVec.x)) - 90 + (headToOrigin.get() ? 180 : 0));
            case PlayerRotation -> mc.player.setYaw(Math.round((mc.player.getYaw() + 1f) / 45f) * 45f);
            case None -> {
                return;
            }
        }

    }

    //endregion



    //region Utils
    private Vec3d zeroYComponent(Vec3d v){
        return new Vec3d(v.x, 0, v.z);
    }
    private double getAbsDelta(double x, double y){
        return Math.abs(Math.abs(x) - Math.abs(y));
    }


    private void toggleEfly(boolean state) {
        if (eflyModule.isActive() == state)
            return;
        eflyModule.toggle();
    }

    private void baritoneGoto(Vec3d pos) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(String.format("goto %d %d %d", (int) pos.x, (int) pos.y, (int) pos.z));
    }

    private boolean baritoneIsPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    private void baritoneStop(){
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
    }

    private double distFromHighway(Vec3d highwayVecOrig){
        //prob should use vec2d but whatevs
        Vec3d highwayVec = zeroYComponent(highwayVecOrig).normalize();
        Vec3d playerPos = zeroYComponent(mc.player.getPos().subtract(xOrigin, 0, zOrigin));
        //take dot product between the 2
        double dot = highwayVec.dotProduct(playerPos);
        //see diagram here: https://cdn.discordapp.com/attachments/1147380012752842773/1147381068366880809/image.png
        //if dp is negative then the closest would just be to origin

        if(dot < 0)
            return playerPos.length();
        //info("dist from " + highwayVecOrig.toString() + " is " + String.valueOf(dist));

        return Math.sqrt(playerPos.lengthSquared() - Math.pow(dot, 2));

    }
    private boolean isDiagonal(HighwayAxis axis){
        return isDiagonal(directionVecs.get(axis));
    }
    private boolean isDiagonal(Vec3d vec){
        return (vec.x != 0 && vec.z != 0);
    }
    //endregion

    //region StuckChecks
    private boolean checkStuckVelocity() {
        if (Utils.getPlayerSpeed().length() < stuckVelocity.get()) {
            velocityBelowThresholdTicks++;
        } else {
            velocityBelowThresholdTicks = 0;
        }
        if (velocityBelowThresholdTicks >= detectionSpan.get())
            return true;

        return false;
    }

    //checks player pos to determine if player is stuck
    private boolean checkStuckPosition() {
        posHistory.add(mc.player.getPos());
        if (posHistory.size() < detectionSpan.get())
            return false;
        Vec3d firstPos = posHistory.removeFirst();
        if (firstPos.subtract(mc.player.getPos()).length() < stuckDistance.get())
            return true;
        return false;
    }

    private boolean checkPlayerPosBounds() {
        if(mc.player.getY() < yTarget.get() - yMaxNegDelta.get())
            return true;
        if(mc.player.getY() > yTarget.get() + yMaxPosDelta.get())
            return true;

        return (distFromHighway(axisVec) > horizontalMaxDelta.get());
    }
    //endregion



    private void checkStuck() {
        playerIsStuck = checkStuckPosition() || checkStuckVelocity() || checkPlayerPosBounds();

    }
    private void resetStuckDetection(){
        posHistory.clear();
        velocityBelowThresholdTicks = 0;
    }

    private void resetAll(){
        resetStuckDetection();
        startedUnstuck = false;
        playerIsStuck = false;
        executedCommand = false;

    }



    //This only works with automatic highway detection
    private Vec3d getUnstuckTarget() {
        double spawnDistOnAxis = Math.max(Math.abs(mc.player.getX()), Math.abs(mc.player.getZ()));
        //^not actual geometric dist from spawn
        //scale vec with that thing
        Vec3d res = axisVec.multiply(spawnDistOnAxis + (unstuckMoveDistance.get() * (headToOrigin.get() ? -1 : 1)));
        return res.add(0, yTarget.get(), 0);

    }



    private void resolveStuck() {
        toggleEfly(false);
        if(mc.player.isFallFlying())
            return;

        if(baritoneIsPathing())
            startedUnstuck = true;

        if(!startedUnstuck){

            //wait until efly stops
            if(!executedCommand){
                info("Player is stuck. Resolving...");
                Vec3d target = getUnstuckTarget();
                baritoneGoto(target);
                executedCommand = true;
            }
            return;

        }
        //unstuck ended
        if(!baritoneIsPathing()){
            info("Player stuck resolved.");
            startedUnstuck = false;
            playerIsStuck = false;
            executedCommand = false;
            toggleEfly(true);
            resetStuckDetection();
        }

    }



    //Returns the guessed highway direction vector
    private Vec3d checkHighway() {
        double recordLength = Double.MAX_VALUE;
        //placeholder, can and should be replaced
        Vec3d highwayVec = directionVecs.get(HighwayAxis.XPlus);
        for(Vec3d curVec : directionVecs.values()){
            double curLen = distFromHighway(curVec);
            if (curLen < recordLength) {
                recordLength = curLen;
                highwayVec = curVec;
            }
        }
        return highwayVec;

    }


    private void recalcHighway(){
        Vec3d playerPos = mc.player.getPos();
//        if(zeroYComponent(mc.player.getPos()).length() < 0) {
//            info("Please move at least 100 blocks away from spawn.");
//            toggle();
//        }
        xOrigin = xOriginSetting.get();
        zOrigin = zOriginSetting.get();
        if(detectHighway.get())
            axisVec = checkHighway();
        else
            axisVec = directionVecs.get(highwayAxisSetting.get());


        info("Currently on highway dir: " + axisVec.toString());
    }


    @EventHandler
    private void onTick(TickEvent.Post event) {

        if (playerIsStuck) {
            resolveStuck();
            return;
        }
        lockRotation();
        toggleEfly(true);
        checkStuck();

    }



    @Override
    public void onActivate() {
        recalcHighway();
        resetStuckDetection();
    }

    @Override
    public void onDeactivate(){
        baritoneStop();
        toggleEfly(false);
        resetAll();
    }






}
