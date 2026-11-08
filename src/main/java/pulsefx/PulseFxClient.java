package pulsefx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Every effect uses OUR OWN animated sprites (see Fxp / GlowParticle), not vanilla Minecraft particles.
 * All effects are client-side, so they work on any server. Invisible players/mobs are never revealed.
 */
public class PulseFxClient implements ClientModInitializer {
    private static KeyBinding menuKey;
    private final Random rnd = new Random();
    private Vec3d lastPos = Vec3d.ZERO;
    private boolean wasOnGround = true, wasAttack = false, wasSprint = false;
    private double airTop = 0;
    private int tick, slashAge = -1;
    private double slashYaw;

    // colours for Glow Style: {from, to}
    private static final int[][] AURA = {
        {0xFFB03B, 0xFF3B1A}, // Flame
        {0xFFFFFF, 0x7FD6FF}, // Frost
        {0x2A0A4A, 0xB84CFF}, // Void
        {0xFFFF9A, 0x6AA8FF}, // Lightning
        {0xFFF2B0, 0xFFD35A}  // Holy
    };

    // ---- pixel wings: one wing, inner edge at column 0, outer tip at the last column ----
    // P/p purple bone, K near-black, G gold, O orange, Y bright yellow
    private static final String[] WING = {
        "......PPPPP.........",
        "....PPPppppPPP......",
        "..PPPpGGGYYGGPPP....",
        "PPPpGGYYYYGGGGGPPP..",
        "KPpGGGOYYGGGOGGGGPP.",
        ".KGGOGGOGGGOGGOGGOP.",
        "..GOGOGGOGGOGGGOGO..",
        "...O.GOG.OGGO.GO.O..",
        "...O..OG..OG...O....",
        ".......O...O........"
    };

    @Override
    public void onInitializeClient() {
        FxConfig.load();
        Fxp.registerFactories();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("pulsefx", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.pulsefx.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, cat));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addFirst(Identifier.of("pulsefx", "hudfx"), (ctx, tc) -> HudFx.render(ctx));
    }

    // ---------------- helpers ----------------
    private int cnt(double base) {
        double v = base * FxConfig.rate * FxConfig.perfMul();
        int n = (int) v;
        if (rnd.nextDouble() < v - n) n++;
        return n;
    }
    private double j(double x) { return (rnd.nextDouble() - 0.5) * 2 * x; }
    private double u(double lo, double hi) { return lo + rnd.nextDouble() * (hi - lo); }
    private int pick(int... cols) { return cols[rnd.nextInt(cols.length)]; }
    private int hsv(double h, float s, float v) { return MathHelper.hsvToRgb((float) (((h % 1.0) + 1.0) % 1.0), s, v); }
    private static GlowParticle fx(String n, double x, double y, double z, double vx, double vy, double vz) {
        return Fxp.spawn(n, x, y, z, vx, vy, vz);
    }

    private static boolean holdingWeapon(ClientPlayerEntity p) {
        String id = Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath();
        return id.endsWith("sword") || id.endsWith("_axe") || id.equals("trident") || id.equals("mace") || id.endsWith("spear");
    }

    // ---------------- main tick ----------------
    private void onTick(MinecraftClient mc) {
        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new FxScreen());
        }
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.isPaused() || !Fxp.ready()) return;
        tick++;

        FxConfig.skyDimOk = w.getRegistryKey() != World.NETHER && w.getRegistryKey() != World.END;

        boolean visible = !p.isInvisible();
        Vec3d pos = p.getEntityPos();
        float size = FxConfig.particleSize;
        double range = FxConfig.range;
        double yawR = Math.toRadians(p.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(yawR), 0, Math.cos(yawR));
        Vec3d right = new Vec3d(-Math.cos(yawR), 0, -Math.sin(yawR));
        double eyeY = p.getEyeY();

        // ================= VISUALS (ambient) =================
        if (Fx.STARS.on) {
            for (int i = cnt(1.6); i > 0; i--) {
                double a = rnd.nextDouble() * Math.PI * 2, r = (2 + rnd.nextDouble() * 10) * range;
                fx("star", pos.x + Math.cos(a) * r, pos.y + 0.3 + rnd.nextDouble() * 6, pos.z + Math.sin(a) * r, j(0.004), u(0.006, 0.02), j(0.004))
                    .life(40 + rnd.nextInt(40)).size((float) u(0.25, 0.5) * size).pulse().alpha(1f, 1f)
                    .color(pick(0xFFFFFF, 0xFFE9A0, 0xFFC6F0, 0xBFE6FF)).phys(0, 0.99f).spin((float) j(0.05));
            }
        }
        if (Fx.SAKURA.on) {
            for (int i = cnt(0.9); i > 0; i--) {
                fx("petal", pos.x + j(8 * range), pos.y + 4 + rnd.nextDouble() * 3, pos.z + j(8 * range), j(0.01), -u(0.015, 0.035), j(0.01))
                    .life(110 + rnd.nextInt(50)).size((float) u(0.10, 0.17)).alpha(1f, 0.9f)
                    .color(pick(0xFFFFFF, 0xFFE0EA, 0xFFC4D8)).wobble(0.012f).spin((float) j(0.12)).phys(0, 1f);
            }
        }
        if (Fx.BUBBLES.on) {
            for (int i = cnt(1.2); i > 0; i--) {
                fx("bubble", pos.x + j(5 * range), pos.y + rnd.nextDouble() * 2.5, pos.z + j(5 * range), 0, u(0.012, 0.03), 0)
                    .life(50 + rnd.nextInt(40)).size((float) u(0.08, 0.2) * size, (float) u(0.1, 0.25) * size)
                    .alpha(0.95f, 0.95f).wobble(0.006f).phys(0, 1f);
            }
        }
        if (Fx.MAGIC.on) {
            for (int i = cnt(1.4); i > 0; i--) {
                double a = rnd.nextDouble() * Math.PI * 2, r = u(1.2, 2.0);
                double sx = pos.x + Math.cos(a) * r, sy = pos.y + u(0.2, 2.2), sz = pos.z + Math.sin(a) * r;
                fx("rune", sx, sy, sz, (pos.x - sx) * 0.035, (pos.y + 1.2 - sy) * 0.03, (pos.z - sz) * 0.035)
                    .life(28 + rnd.nextInt(10)).size((float) u(0.16, 0.26) * size, 0.05f)
                    .color(pick(0xB48CFF, 0x7FE8FF, 0xFFA8F0), 0xFFFFFF).phys(0, 1f).spin((float) j(0.08));
            }
        }
        if (Fx.ORBS.on && visible) {
            for (int k = 0; k < 3; k++) {
                double a = tick * 0.12 + k * Math.PI * 2 / 3;
                double ox = pos.x + Math.cos(a) * 1.1, oz = pos.z + Math.sin(a) * 1.1, oy = pos.y + 1.0 + Math.sin(tick * 0.08 + k) * 0.35;
                int col = hsv(tick * 0.01 + k / 3.0, 0.55f, 1f);
                fx("glow", ox, oy, oz, 0, 0, 0).life(0).size(0.55f * size).alpha(0.9f, 0.9f).color(col);
                fx("star", ox, oy, oz, 0, 0, 0).life(0).size(0.22f * size).alpha(1f, 1f).color(0xFFFFFF);
                if (rnd.nextDouble() < 0.5) fx("glow", ox, oy, oz, j(0.01), j(0.01), j(0.01)).life(12).size(0.2f * size, 0f).color(col).alpha(0.8f, 0f);
            }
        }
        if (Fx.WINGS.on && visible) wings(p, pos);
        if (Fx.HALO.on && visible) {
            for (int k = 0; k < 14; k++) {
                double a = tick * 0.12 + k * Math.PI * 2 / 14;
                fx("glow", pos.x + Math.cos(a) * 0.4, pos.y + 2.35 + Math.sin(a * 2 + tick * 0.1) * 0.02, pos.z + Math.sin(a) * 0.4, 0, 0, 0)
                    .life(0).size(0.12f * size).alpha(0.95f, 0.95f).color(k % 3 == 0 ? 0xFFFFFF : 0xFFD84A);
            }
            fx("star", pos.x + Math.cos(tick * 0.12) * 0.4, pos.y + 2.35, pos.z + Math.sin(tick * 0.12) * 0.4, 0, 0, 0)
                .life(0).size(0.28f * size).color(0xFFF2B0);
        }
        if (Fx.FIREFLIES.on) {
            for (int i = cnt(1.0); i > 0; i--) {
                fx("glow", pos.x + j(7 * range), pos.y + rnd.nextDouble() * 3, pos.z + j(7 * range), j(0.012), j(0.008), j(0.012))
                    .life(60 + rnd.nextInt(50)).size((float) u(0.22, 0.34) * size).pulse().alpha(1f, 1f)
                    .color(pick(0xD8FF5A, 0xB8FF7A, 0xFFF08A)).wobble(0.01f).phys(0, 1f);
            }
        }
        if (Fx.SNOWFALL.on) {
            for (int i = cnt(2); i > 0; i--) {
                fx("flake", pos.x + j(10 * range), pos.y + 5 + rnd.nextDouble() * 3, pos.z + j(10 * range), j(0.008), -u(0.03, 0.06), j(0.008))
                    .life(100).size((float) u(0.07, 0.14)).alpha(1f, 0.9f).color(pick(0xFFFFFF, 0xDDF3FF)).wobble(0.008f).spin((float) j(0.1)).phys(0, 1f);
            }
        }
        if (Fx.EMBERS.on) {
            for (int i = cnt(1.2); i > 0; i--) {
                fx("flame", pos.x + j(6 * range), pos.y + rnd.nextDouble() * 2, pos.z + j(6 * range), j(0.004), u(0.03, 0.06), j(0.004))
                    .life(24 + rnd.nextInt(14)).size((float) u(0.12, 0.2), 0.02f).alpha(1f, 0f).wobble(0.006f).phys(0, 1f);
                if (rnd.nextDouble() < 0.6)
                    fx("glow", pos.x + j(6 * range), pos.y + 1 + rnd.nextDouble() * 3, pos.z + j(6 * range), j(0.006), u(0.02, 0.045), j(0.006))
                        .life(40).size(0.1f, 0.02f).color(0xFFB03B, 0xFF3B1A).alpha(1f, 0f).phys(0, 1f);
            }
        }
        if (Fx.WISPS.on) {
            for (int i = cnt(0.8); i > 0; i--) {
                fx("wisp", pos.x + j(5 * range), pos.y + rnd.nextDouble() * 2, pos.z + j(5 * range), j(0.006), u(0.02, 0.04), j(0.006))
                    .life(40 + rnd.nextInt(20)).size((float) u(0.22, 0.34), 0.08f).color(pick(0x6FD8FF, 0x9AB8FF, 0x8CFFF0)).alpha(0.95f, 0f).wobble(0.01f).phys(0, 1f);
            }
        }
        if (Fx.HEARTS.on && visible) {
            if (cnt(0.2) > 0)
                fx("heart", pos.x + j(0.5), pos.y + 2.1, pos.z + j(0.5), j(0.004), 0.03, j(0.004))
                    .life(36).size(0.08f, 0.26f * size).alpha(1f, 0f).color(pick(0xFF4F7A, 0xFF7FB0, 0xFF3355)).wobble(0.01f).phys(0, 1f);
        }
        if (Fx.SPARKLES.on && visible) {
            for (int i = cnt(1.0); i > 0; i--) {
                fx("star", pos.x + j(0.45), pos.y + rnd.nextDouble() * 1.9, pos.z + j(0.45), 0, 0.004, 0)
                    .life(14 + rnd.nextInt(8)).size((float) u(0.14, 0.26) * size).pulse().alpha(1f, 1f).color(pick(0xFFFFFF, 0xFFE9A0, 0xBFE6FF));
            }
        }

        // ================= TRAIL =================
        Vec3d d = pos.subtract(lastPos);
        double dist = d.length();
        if (Fx.TRAIL.on && visible && dist > 0.04 && dist < 6) {
            int steps = (int) Math.min(10, Math.max(1, dist / 0.16));
            for (int s = 0; s < steps; s++)
                spawnTrail(lastPos.add(d.multiply((s + 1.0) / steps)), tick + s * 0.35, size);
        }

        // ================= MOVEMENT =================
        boolean sprint = p.isSprinting();
        if (Fx.FLAMES.on && visible && sprint && p.isOnGround()) {
            for (int i = cnt(2); i > 0; i--)
                fx("flame", pos.x + j(0.2), pos.y + 0.12, pos.z + j(0.2), j(0.01), u(0.02, 0.05), j(0.01))
                    .life(10 + rnd.nextInt(6)).size((float) u(0.22, 0.36) * size, 0.05f).alpha(1f, 0f).phys(0, 1f);
        }
        if (Fx.RIPPLES.on && visible && p.isOnGround() && dist > 0.08 && tick % 5 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = i / 12.0 * Math.PI * 2;
                fx("glow", pos.x + Math.cos(a) * 0.2, pos.y + 0.05, pos.z + Math.sin(a) * 0.2, Math.cos(a) * 0.035, 0, Math.sin(a) * 0.035)
                    .life(14).size(0.1f * size, 0.03f).alpha(0.85f, 0f).color(0xFFFFFF, 0x9FE8FF).phys(0, 0.93f);
            }
        }
        if (Fx.DASH.on && visible && sprint && !wasSprint && p.isOnGround()) {
            for (int i = 0; i < 12; i++)
                fx("smoke", pos.x + j(0.3), pos.y + 0.15 + rnd.nextDouble() * 0.4, pos.z + j(0.3),
                    -fwd.x * 0.14 + j(0.03), 0.01, -fwd.z * 0.14 + j(0.03))
                    .life(18 + rnd.nextInt(8)).size(0.18f, 0.55f).alpha(0.7f, 0f).phys(0, 0.9f).spin((float) j(0.05));
        }
        wasSprint = sprint;

        if (!p.isOnGround()) {
            if (wasOnGround) airTop = pos.y;
            airTop = Math.max(airTop, pos.y);
        }
        if (Fx.JUMP_RINGS.on && visible && wasOnGround && !p.isOnGround() && p.getVelocity().y > 0.1) {
            fx("ring", pos.x, pos.y + 0.15, pos.z, 0, 0, 0).life(12).size(0.15f, 1.3f * size).alpha(1f, 0f).color(0xFFFFFF, 0x9FE8FF);
            for (int i = 0; i < 28; i++) {
                double a = i / 28.0 * Math.PI * 2;
                fx("glow", pos.x + Math.cos(a) * 0.3, pos.y + 0.1, pos.z + Math.sin(a) * 0.3, Math.cos(a) * 0.09, 0.005, Math.sin(a) * 0.09)
                    .life(16).size(0.16f * size, 0.04f).alpha(1f, 0f).color(hsv(i / 28.0, 0.5f, 1f)).phys(0, 0.9f);
                if (i % 4 == 0) fx("star", pos.x, pos.y + 0.12, pos.z, Math.cos(a) * 0.13, 0.02, Math.sin(a) * 0.13)
                    .life(16).size(0.25f * size).pulse().phys(0, 0.92f);
            }
        }
        if (Fx.SHOCKWAVE.on && visible && p.isOnGround() && !wasOnGround && airTop - pos.y > 2.5) {
            fx("ring", pos.x, pos.y + 0.12, pos.z, 0, 0, 0).life(14).size(0.3f, 2.8f * size).alpha(1f, 0f).color(0xFFE9C0, 0xCFC3A8);
            for (int i = 0; i < 30; i++) {
                double a = i / 30.0 * Math.PI * 2;
                fx("smoke", pos.x, pos.y + 0.15, pos.z, Math.cos(a) * 0.22, 0.015, Math.sin(a) * 0.22)
                    .life(22).size(0.3f, 0.7f).alpha(0.8f, 0f).color(0xE8DCC0, 0xB8AE98).phys(0, 0.9f).spin((float) j(0.05));
            }
        }
        wasOnGround = p.isOnGround();
        lastPos = pos;

        // ================= COMBAT =================
        if (Fx.AURA.on && visible && holdingWeapon(p)) weaponAura(pos, fwd, right, eyeY, size);

        boolean atk = mc.options.attackKey.isPressed();
        if (atk && !wasAttack && mc.currentScreen == null) {
            if (Fx.SLASH.on && visible) {
                slashAge = 0; slashYaw = yawR;
                int[] c = AURA[FxConfig.auraStyle];
                fx("slash", pos.x + fwd.x * 1.7, eyeY - 0.35, pos.z + fwd.z * 1.7, 0, 0, 0).life(7).size(0.5f * size, 1.0f * size).alpha(1f, 0f).color(c[0], c[1]).fade(0.05f);
            }
            if (mc.crosshairTarget instanceof EntityHitResult ehr) {
                Entity e = ehr.getEntity();
                if (e instanceof LivingEntity && !e.isInvisible()) onHit(ehr.getPos(), size);
            }
        }
        if (Fx.MINING.on && atk && mc.currentScreen == null && mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            Vec3d hp = bh.getPos();
            Direction sd = bh.getSide();
            for (int i = cnt(2); i > 0; i--)
                fx(i % 2 == 0 ? "star" : "glow",
                    hp.x + sd.getOffsetX() * 0.05 + j(0.25), hp.y + sd.getOffsetY() * 0.05 + j(0.25), hp.z + sd.getOffsetZ() * 0.05 + j(0.25),
                    sd.getOffsetX() * 0.07 + j(0.04), sd.getOffsetY() * 0.07 + u(0.0, 0.06), sd.getOffsetZ() * 0.07 + j(0.04))
                    .life(14 + rnd.nextInt(6)).size((float) u(0.1, 0.2) * size, 0.02f).alpha(1f, 0f)
                    .color(pick(0xFFFFFF, 0xFFE08A, 0xFFB03B)).phys(0.02f, 0.92f);
        }
        wasAttack = atk;

        if (slashAge >= 0) {
            drawSlash(pos.x, eyeY - 0.35, pos.z, size);
            slashAge++;
            if (slashAge > 5) slashAge = -1;
        }
    }

    // ---------------- pixel wings ----------------
    private void wings(ClientPlayerEntity p, Vec3d pos) {
        double by = Math.toRadians(p.bodyYaw);
        double fwx = -Math.sin(by), fz = Math.cos(by);          // body forward
        double rx = -Math.cos(by), rz = -Math.sin(by);         // body right
        double flap = Math.sin(tick * 0.22) * 0.32 + 0.12;     // wing angle (rad), animated
        double cs = 0.085;                                      // size of one wing "pixel"
        float half = (float) (cs * 0.62);
        double rootY = pos.y + (p.isSneaking() ? 1.05 : 1.2);
        double backOff = p.isSneaking() ? 0.42 : 0.26;
        int skip = FxConfig.performance == 0 ? 2 : 1;
        for (int row = 0; row < WING.length; row++) {
            String line = WING[row];
            for (int col = 0; col < line.length(); col += 1) {
                char ch = line.charAt(col);
                if (ch == '.') continue;
                if (skip > 1 && ((row + col) & 1) == 1) continue;
                int color = switch (ch) {
                    case 'P' -> 0x482872;
                    case 'p' -> 0x7D4BAF;
                    case 'K' -> 0x1E1230;
                    case 'Y' -> 0xFFEC78;
                    case 'O' -> 0xE87814;
                    default -> 0xFFBE32;
                };
                double lat = 0.14 + col * cs;
                double up = (4 - row) * cs;
                double cosF = Math.cos(flap), sinF = Math.sin(flap);
                double lx = lat * cosF, ly = up + lat * sinF;
                double back = backOff + lat * 0.18;
                for (int s = -1; s <= 1; s += 2) {
                    double x = pos.x + rx * lx * s - fwx * back;
                    double z = pos.z + rz * lx * s - fz * back;
                    fx("px", x, rootY + ly, z, 0, 0, 0).life(0).size(half).alpha(1f, 1f).fade(0f).color(color);
                }
            }
        }
        // golden dust falling from the feather tips
        if (rnd.nextDouble() < 0.5 * FxConfig.perfMul()) {
            double lat = 0.5 + rnd.nextDouble() * 1.0;
            int s = rnd.nextBoolean() ? 1 : -1;
            fx("glow", pos.x + rx * lat * s - fwx * 0.4, rootY - 0.35, pos.z + rz * lat * s - fz * 0.4, 0, -0.012, 0)
                .life(22).size(0.09f, 0.02f).alpha(0.9f, 0f).color(0xFFE08A, 0xFFB03B).phys(0, 1f);
        }
    }

    // ---------------- weapon glow ----------------
    private void weaponAura(Vec3d pos, Vec3d fwd, Vec3d right, double eyeY, float size) {
        int[] c = AURA[FxConfig.auraStyle];
        double bx = pos.x + right.x * 0.45 + fwd.x * 0.5, by = eyeY - 0.55, bz = pos.z + right.z * 0.45 + fwd.z * 0.5;
        for (int i = cnt(3); i > 0; i--) {
            double t = 0.1 + rnd.nextDouble() * 1.0;
            double ax = bx + fwd.x * 0.6 * t + j(0.05), ay = by + 0.8 * t, az = bz + fwd.z * 0.6 * t + j(0.05);
            fx("glow", ax, ay, az, 0, 0, 0).life(8).size(0.2f * size, 0.04f).alpha(0.8f, 0f).color(c[0], c[1]);
            if (rnd.nextDouble() < 0.5) {
                switch (FxConfig.auraStyle) {
                    case 0 -> fx("flame", ax, ay, az, 0, 0.025, 0).life(12).size(0.24f, 0.04f).alpha(1f, 0f).phys(0, 1f);
                    case 1 -> fx("flake", ax, ay, az, j(0.01), -0.012, j(0.01)).life(20).size(0.1f).alpha(1f, 0f).spin((float) j(0.15)).color(0xFFFFFF, 0x9FE0FF).phys(0, 1f);
                    case 2 -> {
                        fx("wisp", ax, ay, az, j(0.01), 0.02, j(0.01)).life(18).size(0.22f, 0.06f).alpha(0.9f, 0f).color(0xB84CFF, 0x4A1A8A).phys(0, 1f);
                        if (rnd.nextDouble() < 0.15) fx("rune", ax, ay, az, j(0.01), 0.01, j(0.01)).life(18).size(0.14f, 0.05f).color(0xD79CFF).alpha(1f, 0f).spin((float) j(0.1)).phys(0, 1f);
                    }
                    case 3 -> {
                        fx("bolt", ax, ay, az, 0, 0, 0).life(3).size(0.24f).alpha(1f, 0.4f).color(0xFFFFB0, 0xA8D0FF);
                        if (rnd.nextDouble() < 0.4) fx("star", ax, ay, az, j(0.02), j(0.02), j(0.02)).life(8).size(0.18f).pulse().color(0xFFFFFF);
                    }
                    default -> fx("star", ax, ay, az, 0, 0.012, 0).life(14).size(0.2f).pulse().color(0xFFF2B0).phys(0, 1f);
                }
            }
        }
    }

    // ---------------- trail styles ----------------
    private void spawnTrail(Vec3d q, double t, float sz) {
        switch (FxConfig.trailStyle) {
            case 0 -> { // Rainbow Ribbon
                for (int k = 0; k < 2; k++) {
                    int c = hsv(((t * 6 + k * 40) % 360) / 360.0, 0.6f, 1f);
                    fx("glow", q.x + j(0.06), q.y + 0.25 + k * 0.8, q.z + j(0.06), 0, 0, 0)
                        .life(16).size(0.34f * sz, 0.04f).alpha(0.95f, 0f).color(c);
                }
                if (rnd.nextDouble() < 0.2) fx("star", q.x + j(0.2), q.y + 0.2 + rnd.nextDouble() * 1.4, q.z + j(0.2), 0, 0.01, 0).life(14).size(0.2f * sz).pulse();
            }
            case 1 -> { // Aurora Helix
                for (int k = 0; k < 2; k++) {
                    double a = t * 0.6 + k * Math.PI;
                    fx("glow", q.x + Math.cos(a) * 0.45, q.y + 0.9 + Math.sin(t * 0.3) * 0.5, q.z + Math.sin(a) * 0.45, 0, 0, 0)
                        .life(18).size(0.3f * sz, 0.04f).alpha(0.95f, 0f).color(0x4DFFC3, 0x8A5CFF);
                }
                if (rnd.nextDouble() < 0.2) fx("wisp", q.x + j(0.3), q.y + 0.9, q.z + j(0.3), 0, 0.02, 0).life(20).size(0.2f * sz, 0.05f).color(0x7DFFD8, 0xB090FF).alpha(0.9f, 0f);
            }
            case 2 -> { // Cosmic Dust
                fx("star", q.x + j(0.22), q.y + 0.2 + rnd.nextDouble() * 1.4, q.z + j(0.22), j(0.003), j(0.005), j(0.003))
                    .life(24).size((float) u(0.18, 0.32) * sz).pulse().color(pick(0xFF4FD8, 0x7A6BFF, 0x4FD8FF), 0xFFFFFF);
                if (rnd.nextDouble() < 0.4) fx("px", q.x + j(0.3), q.y + 0.2 + rnd.nextDouble() * 1.6, q.z + j(0.3), 0, 0.004, 0)
                    .life(26).size(0.025f).alpha(1f, 0f).color(0xFFFFFF);
            }
            default -> { // Ice Crystal
                fx("glow", q.x + j(0.2), q.y + 0.15 + rnd.nextDouble() * 1.2, q.z + j(0.2), 0, 0, 0)
                    .life(14).size(0.22f * sz, 0.03f).alpha(0.8f, 0f).color(0xFFFFFF, 0x7FD6FF);
                if (rnd.nextDouble() < 0.4) fx("flake", q.x + j(0.25), q.y + 0.4 + rnd.nextDouble(), q.z + j(0.25), 0, -0.015, 0)
                    .life(28).size((float) u(0.08, 0.15) * sz).alpha(1f, 0f).spin((float) j(0.12)).phys(0, 1f);
            }
        }
    }

    // ---------------- slash arc ----------------
    private void drawSlash(double cx, double cy, double cz, float size) {
        int[] c = AURA[FxConfig.auraStyle];
        for (int k = 0; k < 6; k++) {
            double ang = -75 + slashAge * 28 + k * 5.6;
            double a = slashYaw + Math.toRadians(ang);
            double dx = -Math.sin(a), dz = Math.cos(a), dy = (ang / 75.0) * 0.35 - 0.1;
            for (double R = 1.6; R <= 2.3; R += 0.35)
                fx("glow", cx + dx * R, cy + dy, cz + dz * R, 0, 0, 0).life(6).size(0.34f * size, 0.05f).alpha(0.95f, 0f).color(c[0], c[1]);
            if (k % 2 == 0) fx("star", cx + dx * 2.0, cy + dy, cz + dz * 2.0, dx * 0.03, 0, dz * 0.03).life(8).size(0.3f * size).pulse();
        }
    }

    // ---------------- hit effects ----------------
    private void onHit(Vec3d at, float size) {
        HudFx.hitAtMs = System.currentTimeMillis();
        if (Fx.SPARKS.on) {
            for (int i = cnt(22); i > 0; i--) {
                double a = rnd.nextDouble() * Math.PI * 2, sp = 0.08 + rnd.nextDouble() * 0.2, up = rnd.nextDouble() * 0.2;
                fx(i % 3 == 0 ? "star" : "glow", at.x, at.y, at.z, Math.cos(a) * sp, up, Math.sin(a) * sp)
                    .life(12 + rnd.nextInt(10)).size((float) u(0.12, 0.26) * size, 0.02f).alpha(1f, 0f)
                    .color(pick(0xFFE08A, 0xFFFFFF, 0xFFB03B), 0xFF5A3C).phys(0.025f, 0.9f);
            }
        }
        if (Fx.CRIT.on) {
            for (int i = cnt(16); i > 0; i--) {
                double a = rnd.nextDouble() * Math.PI * 2, sp = 0.1 + rnd.nextDouble() * 0.2;
                fx("star", at.x, at.y, at.z, Math.cos(a) * sp, rnd.nextDouble() * 0.2, Math.sin(a) * sp)
                    .life(16 + rnd.nextInt(8)).size((float) u(0.25, 0.5) * size).pulse().alpha(1f, 1f)
                    .color(pick(0xFFF2A0, 0xFFFFFF, 0xFFC84A)).phys(0.01f, 0.93f).spin((float) j(0.1));
            }
        }
        if (Fx.HIT_RINGS.on) {
            int[] c = AURA[FxConfig.auraStyle];
            fx("ring", at.x, at.y, at.z, 0, 0, 0).life(10).size(0.1f, 1.0f * size).alpha(1f, 0f).color(c[0], c[1]);
            for (int i = 0; i < 24; i++) {
                double a = i / 24.0 * Math.PI * 2;
                fx(i % 2 == 0 ? "glow" : "star", at.x, at.y, at.z, Math.cos(a) * 0.11, 0, Math.sin(a) * 0.11)
                    .life(12).size(0.18f * size, 0.03f).alpha(1f, 0f).color(c[0], c[1]).phys(0, 0.9f);
            }
        }
    }
}
