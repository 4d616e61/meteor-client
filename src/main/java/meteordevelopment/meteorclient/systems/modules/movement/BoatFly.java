/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import io.netty.buffer.Unpooled;
import meteordevelopment.meteorclient.events.entity.BoatMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.lang.reflect.Constructor;

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


    public BoatFly() {
        super(Categories.Movement, "boat-fly", "Transforms your boat into a plane.");
    }

    @EventHandler
    private void onBoatMove(BoatMoveEvent event) {
        if (event.boat.getControllingPassenger() != mc.player) return;

        event.boat.setYaw(mc.player.getYaw());
        //Assume that it is a boat that the player is on(it most likely is)
        BoatEntity boatEntity = (BoatEntity) mc.player.getVehicle();

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
        ((IVec3d) event.boat.getVelocity()).set(velX, velY, velZ);


        if(usePacketAntikick.get())
        {
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

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof VehicleMoveS2CPacket && cancelServerPackets.get()) {
            event.cancel();
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
