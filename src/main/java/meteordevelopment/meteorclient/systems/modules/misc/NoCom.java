/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class NoCom extends Module {



    private final SettingGroup sgGeneral = settings.createGroup("General Settings");
    public static Queue<Vec3i> blocksToScan;
    public static HashMap<Vec3i, TestState> blocksTested;

    private static enum TestState
    {
        STATE_INITIAL,
        STATE_CYCLED_0,
        STATE_CYCLED_1,
        STATE_MARKED_DELETION,
        STATE_FOUND,

    }




    private static final int CHECK_INTERVAL = 20;
    private static int currentTick = 0;
    private final Setting<Integer> frequency = sgGeneral.add(new IntSetting.Builder()
        .name("frequency")
        .description("Frequency of packets sent(per second)")
        .defaultValue(200)
        .build()
    );



    public NoCom() {
        super(Categories.Misc, "nocom", "Simple implementation of the nocom exploit");
    }

    private void TestBlock(Vec3i pos)
    {
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, new BlockPos(pos), Direction.UP));
    }

    private void CheckResult()
    {
        for(Map.Entry<Vec3i, TestState> entry : blocksTested.entrySet())
        {
            if(entry.getValue() == TestState.STATE_INITIAL)
            {
                blocksTested.replace(entry.getKey(), TestState.STATE_CYCLED_0);
                continue;
            }
            if(entry.getValue() == TestState.STATE_CYCLED_0)
            {
                blocksTested.replace(entry.getKey(), TestState.STATE_CYCLED_1);
                continue;
            }

            if(entry.getValue() == TestState.STATE_CYCLED_1)
            {
                blocksTested.replace(entry.getKey(), TestState.STATE_MARKED_DELETION);
                continue;
            }
            if(entry.getValue() == TestState.STATE_MARKED_DELETION)
            {
                blocksTested.remove(entry.getKey());
                continue;
            }




        }
    }



    @EventHandler
    private void onTick(TickEvent.Post event) {

        if(currentTick >= CHECK_INTERVAL)
        {
            currentTick = 0;
            CheckResult();
        }
        currentTick++;
        for(int i = 0; i < frequency.get(); i++)
        {
            Vec3i target = blocksToScan.poll();
            if(target == null)
                return;
            TestBlock(target);

            blocksTested.put(target, TestState.STATE_INITIAL);
        }

    };
    @EventHandler
    private void onRecievePacket(PacketEvent.Receive event)
    {
        if(event.packet instanceof BlockUpdateS2CPacket)
        {
            BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) event.packet;
            Vec3i pos = packet.getPos();
            if(blocksTested.containsKey(pos))
            {
                blocksTested.replace(pos, TestState.STATE_FOUND);
                ChatUtils.sendMsg(mc.player.getId(), Formatting.GRAY, "(highlight)%s(default) -- FOUND LOADED", pos.toString());
            }
        }
    }
}
