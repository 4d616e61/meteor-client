/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class ToggleBaritone extends Module {
    private boolean paused;
    public ToggleBaritone() {
        super(Categories.Misc, "toggle-baritone", "Toggles baritone what more do u expect");
    }
    public void pauseBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
    }
    public void unpauseBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("unpause");
    }
    @Override
    public void onActivate() {
        toggle();
        paused = !paused;
        if(paused)
            unpauseBaritone();
        else
            pauseBaritone();
    }
}
