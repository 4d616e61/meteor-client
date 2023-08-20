/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;


public class AutoLogin extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<String> loginPw = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("Password used for login")
        .defaultValue("")
        .build()
    );
    private final Setting<Boolean> autoRegister = sgGeneral.add(new BoolSetting.Builder()
        .name("register")
        .description("Executes the register command as well")
        .defaultValue(false)
        .build()
    );
    public AutoLogin() {
        super(Categories.Misc, "auto-login", "Automatically executes /login when connected to a server.");
    }

//    @EventHandler
//    private void onGameJoined(ConnectToServerEvent event) {
//        if(autoRegister.get())
//        {
//            ChatUtils.sendPlayerMsg("/register " + loginPw);
//
//        }
//        ChatUtils.sendPlayerMsg("/login " + loginPw);
//    }
//

}
