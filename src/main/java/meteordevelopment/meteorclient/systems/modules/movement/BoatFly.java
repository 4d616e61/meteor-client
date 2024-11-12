/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import io.netty.buffer.Unpooled;
import meteordevelopment.meteorclient.events.entity.BoatMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.Offhand;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.network.PacketUtils.packetToString;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.stream.IntStream;

public class BoatFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal speed in blocks per second.")
        .defaultValue(10)
        .min(0)
        .sliderMax(50)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed in blocks per second.")
        .defaultValue(6)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-speed")
        .description("How fast you fall in blocks per second.")
        .defaultValue(0.1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> cancelServerPackets = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel-server-packets")
        .description("Cancels incoming boat move packets.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> usePacketAntikick = sgGeneral.add(new BoolSetting.Builder()
        .name("use-packet-antikick")
        .description("Attempts to bypass flykick by sending down move packets.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> remountBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("remount-bypass")
        .description("Attempts to bypass anticheat kick by remounting.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> resetTime = sgGeneral.add(new IntSetting.Builder()
        .name("bypass-reset-time")
        .description("The time in milliseconds to reset(The first condition reached out of the 3 is used for reset)")
        .defaultValue(200)
        .visible(remountBypass::get)
        .build()
    );
    private final Setting<Integer> resetPackets = sgGeneral.add(new IntSetting.Builder()
        .name("bypass-reset-packet-count")
        .description("Number of serverside dismount packets sent before reset")
        .defaultValue(10)
        .visible(remountBypass::get)
        .build()
    );
    private final Setting<Double> resetDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("bypass-reset-distance")
        .description("Number of serverside dismount packets sent before reset")
        .defaultValue(3.0)
        .visible(remountBypass::get)
        .build()
    );
    private final Setting<Double> downMove = sgGeneral.add(new DoubleSetting.Builder()
        .name("antikick-down-move")
        .description("How far you go down for packet antikick")
        .defaultValue(1)
        .min(0)
        .build()
    );
    private final Setting<Integer> fallDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Fall Delay")
        .description("How often to fall (antikick).")
        .defaultValue(20)
        .min(0)
        .sliderMax(40)
        .build()
    );
    private int antikickTimer;
    private boolean cancelledVehicleMove;
    private VehicleMoveC2SPacket newVehicleMovePacket;
    private boolean isActive;
    private boolean resetRequired = false;
    private boolean resetSent = false;
    private boolean resetAcknowledged = false;
    private Vec3d posAtReset = new Vec3d(0,0,0);
    private long timeAtReset = 0;
    private int packetCtr = 0;


    public BoatFly() {
        super(Categories.Movement, "boat-fly", "Transforms your boat into a plane.");
    }

    @EventHandler
    private void onBoatMove(BoatMoveEvent event) {
        if (event.boat.getControllingPassenger() != mc.player) return;

        event.boat.setYaw(mc.player.getYaw());
        //Assume that it is a boat that the player is on(it most likely is)
        

        // Horizontal movement
        Vec3d vel = PlayerUtils.getHorizontalVelocity(speed.get());
        double velX = vel.getX();
        double velY = 0;
        double velZ = vel.getZ();

        // Vertical movement
        if (mc.options.jumpKey.isPressed()) velY += verticalSpeed.get() / 20;
        if (mc.options.sprintKey.isPressed()) velY -= verticalSpeed.get() / 20;
        else velY -= fallSpeed.get() / 20;

        // Apply velocity
        if(resetSent && !resetAcknowledged) {
            ((IVec3d) event.boat.getVelocity()).set(0, 0, 0);
        }
        else {
            ((IVec3d) event.boat.getVelocity()).set(velX, velY, velZ);
        }
        
    }
    private void doPacketAntikick() {
        if(usePacketAntikick.get())
        {
            if(!mc.player.hasVehicle())
                return;
            if(!(mc.player.getVehicle() instanceof BoatEntity))
                return;
            BoatEntity boatEntity = (BoatEntity) mc.player.getVehicle();
            antikickTimer++;

            //man i sure hate this
            Vec3d pos = new Vec3d(boatEntity.getX(), boatEntity.getY(), boatEntity.getZ());
            //boolean canSeeFeet = mc.world.raycast(new RaycastContext(vec1, vec2, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType();
            //I'd raytrace if I could

            double fallDist = downMove.get();
            //boatEntity.setPos(pos.x, pos.y - fallDist, pos.z);
            //mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y - fallDist, pos.z, mc.player.isOnGround()));
            //mc.player.networkHandler.sendPacket(createPacket(pos.x, pos.y - fallDist, pos.z, boatEntity.getYaw(), boatEntity.getPitch()));
            //VehicleMoveC2SPacketAccessor

            //Set back to original
            //boatEntity.setPos(pos.x, pos.y, pos.z);
        }

    }
    private Vec3d getCurPos() {
        Entity riding = mc.player.getVehicle();
        if(riding != null) {
            return riding.getPos();
        }
        if(mc.player == null)
            return new Vec3d(0,0,0);

        return mc.player.getPos();
        
    }
    private void resetBypassCheckStates() {
        posAtReset = getCurPos();
        timeAtReset = System.currentTimeMillis();
        packetCtr = 0;
    }
    private void resetAllBypassStates() {
        resetBypassCheckStates();
        resetSent = false;
        resetAcknowledged = false;
    }

    private boolean checkAndResetBypass() {
        if(!isActive){
            resetBypassCheckStates();
            
            return true;
        }
            
        boolean needsReset = false;

        needsReset = needsReset || (getCurPos().distanceTo(posAtReset) >= resetDistance.get());
        needsReset = needsReset || (packetCtr >= resetPackets.get());
        needsReset = needsReset || (System.currentTimeMillis() - timeAtReset >= resetTime.get());
        if(needsReset){
            if(!resetSent){
                info("RESETTING!");
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(0, 0, false, true));
                resetAcknowledged = false;
                resetSent = true;
            }
            if(resetAcknowledged) {
                resetAllBypassStates();
            }
                
        }

            
        return needsReset;
        
    }
    private boolean handleEntityPositionS2C(EntityPositionS2CPacket packet) {
        Entity riding = mc.player.getVehicle();
        if(riding == null)
            return false;
        if(!isActive)
            return false;
        checkAndResetBypass();
        
        return false;
    }
    private boolean handleEntityPassengersSetS2C(EntityPassengersSetS2CPacket packet) {
        if(!isActive)
            return false;
        Entity riding = mc.player.getVehicle();
        if(riding == null)
            return false;
        if(packet.getEntityId() != riding.getId())
            return false; 
        if(IntStream.of(packet.getPassengerIds()).anyMatch(x -> x == mc.player.getId()))
            return false;
        packetCtr++;
        
        checkAndResetBypass();
        //This is not necessarily true, but it should be a good enough heuristic
        resetAcknowledged = true;
        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(mc.world.getEntityById(packet.getEntityId()), mc.player.isSneaking(), Hand.OFF_HAND));
        return true;

    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        isActive = false;
        doPacketAntikick();
        if(!remountBypass.get() || !mc.player.isRiding()){
            resetAllBypassStates();
            return;
        }
        


        isActive = true;

        checkAndResetBypass();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        
        if (event.packet instanceof VehicleMoveS2CPacket && cancelServerPackets.get()) {
            event.cancel();
        }
        if( event.packet instanceof EntityPositionS2CPacket) {
            event.setCancelled(handleEntityPositionS2C((EntityPositionS2CPacket)event.packet));
            
        }
        if (event.packet instanceof EntityPassengersSetS2CPacket) {
            event.setCancelled(handleEntityPassengersSetS2C((EntityPassengersSetS2CPacket)event.packet));
            
            
        }

    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event)
    {
        if(antikickTimer < fallDelay.get()){
            cancelledVehicleMove = false;
            return;
        }


        if(event.packet instanceof VehicleMoveC2SPacket && usePacketAntikick.get() && !cancelledVehicleMove)
        {
            antikickTimer = 0;
            VehicleMoveC2SPacket currentPacket = (VehicleMoveC2SPacket) event.packet;
            //clone a packet
            //No Mixins?
            newVehicleMovePacket = createPacket(currentPacket.getX(), currentPacket.getY() - downMove.get(), currentPacket.getZ(), currentPacket.getYaw(), currentPacket.getPitch());
            mc.player.networkHandler.sendPacket(newVehicleMovePacket);
            cancelledVehicleMove = true;

            event.cancel();
            return;
        }

    }

    private VehicleMoveC2SPacket createPacket(double x, double y, double z, float yaw, float pitch) {
        try{
            Constructor<VehicleMoveC2SPacket> constructor = VehicleMoveC2SPacket.class.getDeclaredConstructor(PacketByteBuf.class);
            constructor.setAccessible(true);
            return constructor.newInstance(createPacketData(x, y, z, yaw, pitch));
        }
        catch(Exception e) {
            //should not happen
            return new VehicleMoveC2SPacket(null);
        }
        
    }

    private PacketByteBuf createPacketData(double x, double y, double z, float yaw, float pitch) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);

        return buffer;
    }
}
