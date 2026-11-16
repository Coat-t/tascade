package com.coatt.tascade.hud;

import me.contaria.speedrunapi.config.api.option.EnumTextProvider;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public enum RaceHudMode implements EnumTextProvider {
    ON,
    // not designed yet: shows the full HUD for now
    SIMPLIFIED,
    OFF;

    @Override
    public Text toText() {
        return new TranslatableText("speedrunapi.config.tascade.option.raceHud.value." + name().toLowerCase());
    }
}
