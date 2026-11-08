package pulsefx;

import net.fabricmc.api.ModInitializer;

/** Registers the custom particle types (client-only mod, so this never runs on a server). */
public class PulseFxMain implements ModInitializer {
    @Override
    public void onInitialize() { Fxp.registerTypes(); }
}
