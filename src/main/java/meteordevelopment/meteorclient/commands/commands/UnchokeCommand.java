/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerArgumentType;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.exploits.PacketChoker;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class UnchokeCommand extends Command {

    private boolean shouldExecute(){
        if(!Modules.get().get(PacketChoker.class).isActive())
        {
            info("Enable PacketChoker before executing!");
            return false;
        }
        return true;


    }
    public UnchokeCommand() {
        super("bind", "Unchokes all choked packets in PacketChoker");
    }
    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("s2c").executes(context -> {
            if(!shouldExecute())
                return SINGLE_SUCCESS;
            Modules.get().get(PacketChoker.class).UnchokeS2C();
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("c2s").executes(context -> {
            if(!shouldExecute())
                return SINGLE_SUCCESS;
            Modules.get().get(PacketChoker.class).UnchokeC2S();
            return SINGLE_SUCCESS;
        }));
    }
}
