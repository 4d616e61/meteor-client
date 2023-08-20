/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IHorseBaseEntity;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;


public class ElytraRecast extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> recastDelay = sgGeneral.add(new IntSetting.Builder()
        .name("recast-delay")
        .description("Elytra recasting delay in milliseconds")
        .defaultValue(4)
        .min(0)
        .max(100)
        .build()
    );
    private boolean lastElytraState = false;

    private long startTime = System.currentTimeMillis();
    public ElytraRecast() {
        super(Categories.Movement, "elytra-recast", "Recasts your elytra automatically");
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
        if(curtime - startTime >= recastDelay.get())
        {
            startTime = curtime;
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
