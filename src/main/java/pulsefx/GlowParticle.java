package pulsefx;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

/**
 * One custom (non-vanilla) animated particle. Uses our own PNG sprites from
 * assets/pulsefx/textures/particle and is fully configurable with the chain setters below.
 */
public class GlowParticle extends BillboardParticle {
    private final SpriteProvider sprites;
    private float size0 = 0.2f, size1 = 0.2f, a0 = 1f, a1 = 0f, fadeIn = 0.12f;
    private int c0 = 0xFFFFFF, c1 = 0xFFFFFF;
    private float grav = 0f, drag = 0.98f, spin = 0f, wob = 0f;
    private boolean pulse = false;
    private final float phase;

    public GlowParticle(ClientWorld w, double x, double y, double z, SpriteProvider sp) {
        super(w, x, y, z, sp.getSprite(0, 20));
        this.sprites = sp;
        this.phase = (float) (Math.random() * 6.2831);
        this.collidesWithWorld = false;
        this.maxAge = 20;
        apply();
    }

    // ---------- chain setters (each re-applies so the first frame is already correct) ----------
    public GlowParticle vel(double vx, double vy, double vz) { velocityX = vx; velocityY = vy; velocityZ = vz; return this; }
    public GlowParticle life(int ticks) { maxAge = ticks; apply(); return this; }
    public GlowParticle size(float from, float to) { size0 = from; size1 = to; apply(); return this; }
    public GlowParticle size(float s) { return size(s, s); }
    public GlowParticle alpha(float from, float to) { a0 = from; a1 = to; apply(); return this; }
    public GlowParticle fade(float fi) { fadeIn = fi; apply(); return this; }
    public GlowParticle color(int from, int to) { c0 = from; c1 = to; apply(); return this; }
    public GlowParticle color(int c) { return color(c, c); }
    public GlowParticle phys(float gravity, float drag) { this.grav = gravity; this.drag = drag; return this; }
    public GlowParticle spin(float s) { this.spin = s; return this; }
    public GlowParticle wobble(float w) { this.wob = w; return this; }
    public GlowParticle pulse() { this.pulse = true; apply(); return this; }
    public GlowParticle age0(float k) { this.age = (int) (maxAge * k); apply(); return this; }

    @Override
    protected BillboardParticle.RenderType getRenderType() { return BillboardParticle.RenderType.PARTICLE_ATLAS_TRANSLUCENT; }

    /** Effects glow: always full brightness, even in caves / at night. */
    @Override
    public int getBrightness(float tint) { return 0xF000F0; }

    @Override
    public void tick() {
        lastX = x; lastY = y; lastZ = z;
        if (age >= maxAge) { markDead(); return; }
        age++;
        velocityY -= grav;
        velocityX *= drag; velocityY *= drag; velocityZ *= drag;
        double wx = 0, wz = 0;
        if (wob > 0) {
            wx = Math.cos((age + phase * 3) * 0.22) * wob;
            wz = Math.sin((age + phase * 3) * 0.27) * wob;
        }
        move(velocityX + wx, velocityY, velocityZ + wz);
        apply();
    }

    private void apply() {
        updateSprite(sprites);
        float k = maxAge <= 0 ? 0f : Math.min(1f, age / (float) maxAge);
        float s = size0 + (size1 - size0) * k;
        if (pulse) s *= (float) Math.sin(Math.PI * k);
        scale = Math.max(0.001f, s);
        float al = a0 + (a1 - a0) * k;
        if (fadeIn > 0 && k < fadeIn) al *= k / fadeIn;
        setAlpha(Math.max(0f, Math.min(1f, al)));
        float r = (((c0 >> 16) & 255) + (((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * k) / 255f;
        float g = (((c0 >> 8) & 255) + (((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * k) / 255f;
        float b = ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * k) / 255f;
        setColor(r, g, b);
        lastZRotation = zRotation;
        zRotation += spin;
    }
}
