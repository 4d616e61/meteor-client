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
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedList;


public class AutoHighwayElytrafly extends Module {
    public enum LockedAxis {
        X,
        Z
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

    public final Setting<LockedAxis> lockedAxisSetting = sgGeneral.add(new EnumSetting.Builder<LockedAxis>()
        .name("axis")
        .description("The axis to lock to.")
        .defaultValue(LockedAxis.X)
        .visible(() -> !detectHighway.get())
        .build()
    );

    private final Setting<Integer> lockXSetting = sgGeneral.add(new IntSetting.Builder()
        .name("locked-x")
        .description("X coordinates to lock to.")
        .defaultValue(0)
        .sliderRange(-100000, 100000)
        .visible(() -> (!detectHighway.get()) && lockedAxisSetting.get() == LockedAxis.X)
        .build()
    );
    private final Setting<Integer> lockZSetting = sgGeneral.add(new IntSetting.Builder()
        .name("locked-z")
        .description("Z coordinates to lock to.")
        .defaultValue(0)
        .sliderRange(-100000, 100000)
        .visible(() -> (!detectHighway.get()) && lockedAxisSetting.get() == LockedAxis.Z)
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
    private ElytraFly eflyModule = Modules.get().get(ElytraFly.class);
    private boolean playerIsStuck = false;
    private boolean startedUnstuck = false;
    private int velocityBelowThresholdTicks = 0;
    private LockedAxis lockedAxis = LockedAxis.X;
    private int lockedX = lockXSetting.get();
    private int lockedZ = lockZSetting.get();
    private Vec3d axisVec = new Vec3d(1,0,0);
    private boolean isDiagonal = false;

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
        //-+
        new Vec3d(-1,0, 1),
        //+-
        new Vec3d(1,0, -1),
        //--
        new Vec3d(-1,0, -1)
    };
    //endregion

    //region Misc
    private void lockRotation(){
        switch (rotationLockModeSetting.get()){
            case Manual -> mc.player.setYaw(manualRotationYaw.get().floatValue());
            case Position -> mc.player.setYaw((float) Math.toDegrees(Math.atan2(axisVec.z, axisVec.x)) - 90);
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
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal() != null;
    }

    private void baritoneStop(){
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
    }

    private double distFromHighway(Vec3d highwayVecOrig){
        //prob should use vec2d but whatevs
        Vec3d highwayVec = zeroYComponent(highwayVecOrig).normalize();
        Vec3d playerPos = zeroYComponent(mc.player.getPos());
        //take dot product between the 2]]
        double dot = highwayVec.dotProduct(playerPos);
        //see diagram here: https://cdn.discordapp.com/attachments/1147380012752842773/1147381068366880809/image.png
        //if dp is negative then the closest would just be to origin

        if(dot < 0)
            return playerPos.length();
        //info("dist from " + highwayVecOrig.toString() + " is " + String.valueOf(dist));

        return Math.sqrt(playerPos.lengthSquared() - Math.pow(dot, 2));

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
        double delta;

        //horizontal bounds
        if(detectHighway.get()){
            delta = distFromHighway(axisVec);
        }
        else{
            if(lockedAxis == LockedAxis.X){
                delta = getAbsDelta(lockedX, mc.player.getX());
            }else{
                delta = getAbsDelta(lockedZ, mc.player.getZ());
            }
        }
        if(delta > horizontalMaxDelta.get())
            return true;
        return false;
    }
    //endregion



    private void checkStuck() {
        playerIsStuck = checkStuckPosition() || checkStuckVelocity() || checkPlayerPosBounds();

    }
    private void resetStuckDetection(){
        posHistory.clear();
        velocityBelowThresholdTicks = 0;
    }


    //TODO: fix thing below
    //region pleasefixthisthing
    private Vec3d getUnstuckTarget_needsfixing(){
        //detect mode
        Vec3d target = new Vec3d(mc.player.getX(), yTarget.get(), mc.player.getZ());
        Vec3d dirVec = new Vec3d(0,0,0);

        double moveScalar = unstuckMoveDistance.get();
        if(isDiagonal){
            moveScalar = mc.player.getX() > 0 ? moveScalar : -moveScalar;

            //pick an arbitrary horizontal pos
            double xTarget = mc.player.getX() + moveScalar;
            //in this case highway vec should also be properly initialized
            //See if z is opposite
            double zTarget = xTarget * axisVec.x * axisVec.z;
            target = new Vec3d(xTarget, yTarget.get(), zTarget);

        }
        else {
            //TODO: handle case of going TO spawn
            if(lockedAxis == LockedAxis.X){
                moveScalar = mc.player.getZ() > 0 ? moveScalar : -moveScalar;
                target = new Vec3d(lockedX, yTarget.get(), mc.player.getZ() + moveScalar);
            }
            else{
                moveScalar = mc.player.getX() > 0 ? moveScalar : -moveScalar;
                target = new Vec3d(mc.player.getX() + moveScalar, yTarget.get(), lockedZ);
            }

        }
        return target;
    }
    //endregion


    //This only works with automatic highway detection
    private Vec3d getUnstuckTarget(){
        double spawnDistOnAxis = Math.max(Math.abs(mc.player.getX()), Math.abs(mc.player.getZ()));
        //^not actual geometric dist from spawn
        //scale vec with that thing
        Vec3d res = axisVec.multiply(spawnDistOnAxis + unstuckMoveDistance.get());
        return res.add(0, yTarget.get(), 0);

    }

    private void resolveStuck() {
        toggleEfly(false);
        if(baritoneIsPathing())
            startedUnstuck = true;

        if(!startedUnstuck){
            info("Player is stuck. Resolving...");
            //wait until efly stops
            if(mc.player.isFallFlying())
                return;

            Vec3d target = getUnstuckTarget();
            baritoneGoto(target);


        }
        //unstuck ended
        if(!baritoneIsPathing()){
            info("Player stuck resolved.");
            startedUnstuck = false;
            playerIsStuck = false;
            toggleEfly(true);
            resetStuckDetection();
        }

    }


    //for HORIZONTAL HIGHWAYS ONLY
    private LockedAxis getLockedAxis(Vec3d dir){
        return dir.x == 0 ? LockedAxis.X : LockedAxis.Z;
    }
    private void checkHighway() {
        double recordLength = Double.MAX_VALUE;

        //check nondiags
        for (Vec3d curVec : nondiags) {
            double curLen = distFromHighway(curVec);
            if (curLen < recordLength) {
                recordLength = curLen;
                axisVec = curVec;
                lockedAxis = getLockedAxis(curVec);
            }

        }
        for(Vec3d curVec : diags) {
            double curLen = distFromHighway(curVec);
            if (curLen < recordLength) {
                recordLength = curLen;
                axisVec = curVec;
                isDiagonal = true;
            }
        }

    }


    private void recalcHighway(){
        Vec3d playerPos = mc.player.getPos();
        if(zeroYComponent(mc.player.getPos()).length() < 0) {
            info("Please move at least 100 blocks away from spawn.");
            toggle();
        }
        if(detectHighway.get()){
            lockedX = 0;
            lockedZ = 0;
            //auto set highway
            checkHighway();

        }
        else {
            //manual set highway
            lockedX = lockXSetting.get();
            lockedZ = lockZSetting.get();
            lockedAxis = lockedAxisSetting.get();
            isDiagonal = false;
            //NOTE: this is intentionally broken
            axisVec = new Vec3d(1,0,0);

        }
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
    }

    @Override
    public void onDeactivate(){
        baritoneStop();
        toggleEfly(false);
    }






}
