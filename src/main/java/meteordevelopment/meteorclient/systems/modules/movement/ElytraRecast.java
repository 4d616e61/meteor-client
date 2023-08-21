/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;


public class ElytraRecast extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> recastDelay = sgGeneral.add(new IntSetting.Builder()
        .name("recast-delay")
        .description("Elytra recasting delay in milliseconds")
        .defaultValue(4)
        .min(0)
        .sliderMax(100)
        .build()
    );
    private final Setting<Boolean> stopWhenRubberbanded = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-rubberbanded")
        .description("Toggles off when rubberbanding")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> packetThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("packet-threshold")
        .description("Stops when the specified amount of SetPos packets are sent to client, in a specific timeframe(see below)")
        .defaultValue(4)
        .min(1)
        .sliderMax(10)
        .visible(() -> stopWhenRubberbanded.get())
        .build()
    );
    private final Setting<Integer> checkTimeframe = sgGeneral.add(new IntSetting.Builder()
        .name("check-timeframe")
        .description("The check timeframe for above setting, in milliseconds")
        .defaultValue(1000)
        .min(0)

        .sliderMax(10000)
        .visible(() -> stopWhenRubberbanded.get())
        .build()
    );
    private final Setting<Boolean> toggleAutojump = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-autojump-on-rubberband")
        .description("Turns off autojump when rubberbanding")
        .defaultValue(false)
        .build()
    );

    private boolean lastElytraState = false;

    private long recastStartTime = System.currentTimeMillis();
    private long antiRubberbandStartTime = System.currentTimeMillis();
    private int packetCtr = 0;
    public ElytraRecast() {
        super(Categories.Movement, "elytra-recast", "Recasts your elytra automatically");
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onReceivePacket(PacketEvent.Receive event) {
        if(!stopWhenRubberbanded.get())
            return;
        if(!(event.packet instanceof PlayerPositionLookS2CPacket))
            return;

        long curtime = System.currentTimeMillis();
        if(curtime - antiRubberbandStartTime > checkTimeframe.get())
        {
            //reset packet counter
            packetCtr = 0;
            antiRubberbandStartTime = System.currentTimeMillis();
        }

        packetCtr++;


        if(packetCtr > packetThreshold.get())
        {
            info("Rubberbanding! Disabling Elytra Recast.");
            if(toggleAutojump.get())
                if(Modules.get().get(AutoJump.class).isActive())
                    Modules.get().get(AutoJump.class).toggle();

            toggle();
        }
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {



    }
    //Default cd is 4ms so no point
    public boolean doUpdateFallFlying()
    {
        return isActive();
    }


    private boolean checkElytra()
    {
        if(mc.player.hasVehicle() || mc.player.getAbilities().flying || mc.player.isClimbing())
            return false;

        ItemStack chestArmor = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if(chestArmor.isOf(Items.ELYTRA) && ElytraItem.isUsable(chestArmor))
            return true;
        return false;
    }

    private boolean checkFallFlyingIgnoreGround()
    {
        if (mc.player.isTouchingWater() || mc.player.hasStatusEffect(StatusEffects.LEVITATION))
            return false;
        ItemStack chestArmor = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if(chestArmor.isOf(Items.ELYTRA) && ElytraItem.isUsable(chestArmor))
        {
            mc.player.input.jumping = true;

            mc.player.startFallFlying();
            return true;
        }
        return false;


    }


    private boolean castElytra()
    {
        if(checkElytra() && checkFallFlyingIgnoreGround())
        {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        return false;


    }
    private boolean canRecast()
    {
        long curtime = System.currentTimeMillis();
        if(curtime - recastStartTime >= recastDelay.get())
        {
            recastStartTime = curtime;
            return true;
        }
        return false;

    }

    public boolean recastIfLanded(boolean currentElytraState)
    {

        if(!canRecast() || !mc.player.isAlive())
        {
            this.lastElytraState = currentElytraState;
            return currentElytraState;
        }


        //canRecast = false;
        //if deployed last tick and not this tick
        if(!currentElytraState && lastElytraState)
        {
            this.lastElytraState = currentElytraState;
            return castElytra();
        }
        this.lastElytraState = currentElytraState;
        return currentElytraState;

    }



}
