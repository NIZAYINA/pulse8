package pulsefx;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers our own particle sprites (glow, star, bubble, petal, heart, flake, flame, wisp, ring,
 * slash, smoke, bolt, rune, feather, px) and spawns GlowParticles from them.
 */
public final class Fxp {
    public static final String[] NAMES = {
        "glow", "star", "bubble", "petal", "heart", "flake", "flame", "wisp",
        "ring", "slash", "smoke", "bolt", "rune", "feather", "px"
    };
    private static final Map<String, SimpleParticleType> TYPES = new HashMap<>();
    private static final Map<String, SpriteProvider> SPRITES = new HashMap<>();

    private Fxp() {}

    /** Called from the main entrypoint (registries are still open there). */
    public static void registerTypes() {
        for (String n : NAMES) {
            SimpleParticleType t = FabricParticleTypes.simple();
            Registry.register(Registries.PARTICLE_TYPE, Identifier.of("pulsefx", n), t);
            TYPES.put(n, t);
        }
    }

    /** Called from the client entrypoint. */
    public static void registerFactories() {
        for (String n : NAMES) {
            ParticleFactoryRegistry.getInstance().register(TYPES.get(n), provider -> {
                SPRITES.put(n, provider);
                return (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz, random) -> null;
            });
        }
    }

    /** True once the game has loaded all our sprite definitions (they load before any world opens). */
    public static boolean ready() { return SPRITES.size() >= NAMES.length; }

    public static GlowParticle spawn(String name, double x, double y, double z, double vx, double vy, double vz) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SpriteProvider sp = SPRITES.get(name);
        if (sp == null) sp = SPRITES.values().iterator().next(); // only reached if ready() was not checked
        GlowParticle p = new GlowParticle(mc.world, x, y, z, sp);
        p.vel(vx, vy, vz);
        mc.particleManager.addParticle(p);
        return p;
    }
}
