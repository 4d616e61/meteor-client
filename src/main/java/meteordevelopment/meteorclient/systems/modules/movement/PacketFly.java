/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.Vec3d;

public class PacketFly extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The mode for PacketFly")
        .defaultValue(Mode.Phase)
        .onChanged(mode -> {
            if (!isActive()) return;

        })
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("Horizontal speed")
        .description("Horizontal PacketFly Speed.")
        .defaultValue(0.5)
        .min(0.05)
        .max(2.0)
        .build()
    );
    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("Vertical speed")
        .description("Vertical PacketFly Speed.")
        .defaultValue(0.5)
        .min(0.05)
        .max(2.0)
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
    private final Setting<Boolean> packetCancel = sgGeneral.add(new BoolSetting.Builder()
        .name("Packet Cancel")
        .description("Cancel rubberband packets clientside.")
        .defaultValue(false)
        .build()
    );

    public PacketFly() {
        super(Categories.Movement, "PacketFly", "Courtesy to BleachDev");
    }

    public enum Mode{
        Phase,
        Packet
    }




    private Vec3d cachedPos;
    private int timer = 0;

    @Override
    public void onActivate() {
        //Note: MAY cause issues; test is for InWorld in original code
        if(mc.player.isAlive())
        {
            cachedPos = mc.player.getRootVehicle().getPos();

        }
    }
    //TODO: cancel player move
    /*
    @EventHandler
    public void onMovementPackets(SendMovementPacketsEvent.Pre event) {
        mc.player.setVelocity(Vec3d.ZERO);


    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        event.setCancelled(true);
    }
    */

    @EventHandler
    public void onReadPacket(PacketEvent.Receive event) {

        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            //PlayerPositionLookS2CPacket p = (PlayerPositionLookS2CPacket) event.packet;

            if (packetCancel.get()) {
                event.setCancelled(true);
            }
            return;
        }


    }

    @EventHandler
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket.LookAndOnGround) {
            event.setCancelled(true);
            return;
        }

        if (event.packet instanceof PlayerMoveC2SPacket.Full) {
            event.setCancelled(true);
            PlayerMoveC2SPacket p = (PlayerMoveC2SPacket) event.packet;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(0), p.getY(0), p.getZ(0), p.isOnGround()));

        }
        //MAY NOT BE A GOOD IDEA
        if(event.packet instanceof PlayerMoveC2SPacket)
        {
            mc.player.setVelocity(Vec3d.ZERO);
            event.setCancelled(true);
        }
    }

    //maybe pre works, maybe post, idk
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!mc.player.isAlive())
            return;

        double hspeed = horizontalSpeed.get();
        double vspeed = verticalSpeed.get();
        timer++;

        Vec3d forward = new Vec3d(0, 0, hspeed).rotateY(-(float) Math.toRadians(mc.player.getYaw()));
        Vec3d moveVec = Vec3d.ZERO;

        if (mc.player.input.pressingForward) {
            moveVec = moveVec.add(forward);
        }
        if (mc.player.input.pressingBack) {
            moveVec = moveVec.add(forward.negate());
        }
        if (mc.player.input.jumping) {
            moveVec = moveVec.add(0, vspeed, 0);
        }
        if (mc.player.input.sneaking) {
            moveVec = moveVec.add(0, -vspeed, 0);
        }
        if (mc.player.input.pressingLeft) {
            moveVec = moveVec.add(forward.rotateY((float) Math.toRadians(90)));
        }
        if (mc.player.input.pressingRight) {
            moveVec = moveVec.add(forward.rotateY((float) -Math.toRadians(90)));
        }

        Entity target = mc.player.getRootVehicle();
        if (mode.get() == Mode.Phase) {
            if (timer > fallDelay.get()) {
                moveVec = moveVec.add(0, -vspeed, 0);
                timer = 0;
            }

            cachedPos = cachedPos.add(moveVec);

            //target.noClip = true;
            target.updatePositionAndAngles(cachedPos.x, cachedPos.y, cachedPos.z, mc.player.getYaw(), mc.player.getPitch());
            if (target != mc.player) {
                mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(target));
            } else {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cachedPos.x, cachedPos.y, cachedPos.z, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cachedPos.x, cachedPos.y - 0.01, cachedPos.z, true));
            }
        } else if (mode.get() == Mode.Packet) {
            //moveVec = Vec3d.ZERO;
			/*if (mc.player.headYaw != mc.player.yaw) {
				mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookOnly(
						mc.player.headYaw, mc.player.pitch, mc.player.isOnGround()));
				return;
			}*/

			/*if (mc.options.jumpKey.isPressed())
				mouseY = 0.062;
			if (mc.options.sneakKey.isPressed())
				mouseY = -0.062;*/

            if (timer > fallDelay.get()) {
                moveVec = new Vec3d(0, -vspeed, 0);
                timer = 0;
            }

            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX() + moveVec.x, mc.player.getY() + moveVec.y, mc.player.getZ() + moveVec.z, false));

            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX() + moveVec.x, mc.player.getY() - 420.69, mc.player.getZ() + moveVec.z, true));
        }
    }


}
