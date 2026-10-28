package app.gridprefs.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What can be edited, and which values make sense for each setting.
 *
 * Value vocabulary was taken from the ULTRA preferences file: levels are
 * ultralow / low / medium / high / ultrahigh (+ "off" on a few), switches are on/off.
 * "ultralow" is what is sometimes called "very low".
 */
public final class GfxSpec {
    private GfxSpec() {}

    public static final String[] LADDER = {"ultralow", "low", "medium", "high", "ultrahigh"};
    public static final String[] LADDER_OFF = {"off", "ultralow", "low", "medium", "high", "ultrahigh"};
    public static final String[] ONOFF = {"off", "on"};
    public static final String[] MSAA = {"off", "2xmsaa", "4xmsaa", "8xmsaa", "16xmsaa"};
    public static final int[] FPS = {30, 45, 60, 90, 120};

    public static String pretty(String raw) {
        switch (raw) {
            case "ultralow": return "Ultra low";
            case "ultrahigh": return "Ultra high";
            case "off": return "Off";
            case "on": return "On";
            case "low": return "Low";
            case "medium": return "Medium";
            case "high": return "High";
            case "custom": return "Custom";
            default:
                if (raw.endsWith("xmsaa")) return raw.replace("xmsaa", "x MSAA");
                return raw;
        }
    }

    // ------------------------------------------------------------ profiles

    public enum Profile {
        MAIN("Main", "gfxconfig_"),
        HIGH("High", "gfxconfig_high_"),
        POWER_SAVER("Power saver", "gfxconfigpowersaver_");

        public final String label;
        public final String prefix;

        Profile(String label, String prefix) {
            this.label = label;
            this.prefix = prefix;
        }
    }

    // ------------------------------------------------------------ settings

    public static final class Setting {
        public final String id;
        public final String label;
        public final String[] options;
        /** Older/alternate key spelling that may also exist in some files. */
        public final String alias;

        Setting(String id, String label, String[] options, String alias) {
            this.id = id;
            this.label = label;
            this.options = options;
            this.alias = alias;
        }
    }

    private static Setting lvl(String id, String label) {
        return new Setting(id, label, LADDER, null);
    }

    private static Setting lvlOff(String id, String label) {
        return new Setting(id, label, LADDER_OFF, null);
    }

    private static Setting sw(String id, String label) {
        return new Setting(id, label, ONOFF, null);
    }

    /** Dropdown settings (string values), per profile. */
    public static final List<Setting> LEVELS = Collections.unmodifiableList(Arrays.asList(
            lvl("textures", "Textures"),
            lvl("shadows", "Shadows"),
            lvl("shaders", "Shaders"),
            lvl("anisotropic_filtering", "Anisotropic filtering"),
            lvl("dynamic_ambient_occ", "Ambient occlusion"),
            lvl("car_reflection", "Car reflections"),
            lvlOff("mirrors", "Mirrors"),
            lvlOff("particles", "Particles / smoke"),
            lvl("water", "Water"),
            lvl("track", "Track detail"),
            lvl("objects", "Trackside objects"),
            lvl("trees", "Trees"),
            lvl("crowd", "Crowd"),
            lvl("vehicles", "Vehicle detail"),
            lvl("cloth", "Cloth / flags"),
            lvl("night_lighting", "Night lighting"),
            lvl("second_screen", "Second screen"),
            new Setting("ai_count", "AI car count", LADDER, "ia_count"),
            lvl("voice_count", "Sound voices")
    ));

    public static final Setting MULTISAMPLING =
            new Setting("multisampling", "Multisampling (MSAA)", MSAA, null);

    public static final List<Setting> SWITCHES = Collections.unmodifiableList(Arrays.asList(
            sw("advanced_lighting", "Advanced lighting"),
            sw("advanced_fog", "Advanced fog"),
            sw("global_illumination", "Global illumination"),
            sw("dynamic_ambient_occ_soft", "Soft ambient occlusion"),
            sw("avsm", "AVSM (volumetric shadows)"),
            sw("intel_aoit", "Order-independent transparency"),
            sw("groundCover", "Ground cover"),
            sw("skidmarks", "Skid marks")
    ));

    public static String key(Profile p, String id) {
        return p.prefix + id;
    }

    /** Switch keys in the flat Setup block (plain integers, 0 / 1). */
    public static final class Toggle {
        public final String key;
        public final String label;

        Toggle(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static final List<Toggle> EXTRA_TOGGLES = Collections.unmodifiableList(Arrays.asList(
            new Toggle("FeralUseFXAA", "FXAA anti-aliasing"),
            new Toggle("FeralFXAASmoothing", "FXAA smoothing"),
            new Toggle("EnableImprovedShadowsSampling", "Improved shadow sampling"),
            new Toggle("MobileGraphicBoost", "Mobile graphics boost"),
            new Toggle("MobileShaderBoost", "Mobile shader boost"),
            new Toggle("UltraLowInterior", "Ultra-low interior"),
            new Toggle("UseUltraLowBloomPrePass", "Ultra-low bloom pre-pass"),
            new Toggle("UseUltraLowParticlesBlending", "Ultra-low particle blending"),
            new Toggle("ScaleOrnamentLOD", "Scale ornament LOD"),
            new Toggle("CullDistantCarCharacters", "Cull distant car characters"),
            new Toggle("DisableDynamicEnvMapMSAA", "Disable dynamic env-map MSAA"),
            new Toggle("DisableMainMenuReflections", "Disable main-menu reflections"),
            new Toggle("DisablePlayerWindshieldRendering", "Disable player windshield rendering"),
            new Toggle("MultisampleResolveOnStoreEnabled", "MSAA resolve on store"),
            new Toggle("OptimizeLoadStoreActions", "Optimise load/store actions"),
            new Toggle("FeralEnableRuntimeASTCCompression", "Runtime ASTC compression"),
            new Toggle("ThreadedASTCEncoding", "Threaded ASTC encoding"),
            new Toggle("FeralFlattenTonemapTexture", "Flatten tonemap texture"),
            new Toggle("30FPSMenu3DRender", "30 FPS menu 3D render"),
            new Toggle("RenderUnderNotch", "Render under notch")
    ));

    // ------------------------------------------------------------ presets

    public static final String[] PRESETS = LADDER;

    /** Applies a quality level to every dropdown and switch of one profile. */
    public static void applyPreset(PrefFile f, Profile p, String level, boolean alsoWriteHigh) {
        int rank = Arrays.asList(LADDER).indexOf(level);
        if (rank < 0) throw new IllegalArgumentException(level);
        for (Setting s : LEVELS) {
            String v = level;
            if (s.options == LADDER_OFF && rank == 0) v = "off";
            writeString(f, p, s, v, alsoWriteHigh);
        }
        for (Setting s : SWITCHES) {
            // skid marks stay on at every level
            String v = (rank >= 2 || s.id.equals("skidmarks")) ? "on" : "off";
            writeString(f, p, s, v, alsoWriteHigh);
        }
    }

    /** Writes one string setting; if the profile is MAIN and alsoWriteHigh, mirrors it into HIGH. */
    public static void writeString(PrefFile f, Profile p, Setting s, String value, boolean alsoWriteHigh) {
        writeOne(f, p, s, value);
        if (p == Profile.MAIN && alsoWriteHigh) writeOne(f, Profile.HIGH, s, value);
    }

    public static void writeInt(PrefFile f, Profile p, String id, int value, boolean alsoWriteHigh) {
        f.setInt(PrefFile.SETUP, key(p, id), value);
        if (p == Profile.MAIN && alsoWriteHigh) f.setInt(PrefFile.SETUP, key(Profile.HIGH, id), value);
    }

    private static void writeOne(PrefFile f, Profile p, Setting s, String value) {
        f.setString(PrefFile.SETUP, key(p, s.id), value);
        // Some files spell the main AI-count key "ia_count"; keep that spelling in sync if present.
        if (s.alias != null && f.has(PrefFile.SETUP, key(p, s.alias))) {
            f.setString(PrefFile.SETUP, key(p, s.alias), value);
        }
    }

    /** Current value of a string setting, honouring the alias spelling. Null if not in the file. */
    public static String readString(PrefFile f, Profile p, Setting s) {
        String v = f.getString(PrefFile.SETUP, key(p, s.id), null);
        if (v == null && s.alias != null) v = f.getString(PrefFile.SETUP, key(p, s.alias), null);
        return v;
    }

    // ------------------------------------------------------------ resolution

    public static final int SCALE_MIN = 25;
    public static final int SCALE_MAX = 100;

    /** "window_scale" is the in-game resolution slider (percent of native). Absent = game default. */
    public static int readScale(PrefFile f, Profile p) {
        return f.getInt(PrefFile.SETUP, key(p, "window_scale"), 100);
    }

    public static String describeScale(PrefFile f, int percent) {
        int w = f.getInt(PrefFile.SETUP, "ScreenW", 0);
        int h = f.getInt(PrefFile.SETUP, "ScreenH", 0);
        if (w <= 0 || h <= 0) return percent + "% of native resolution";
        return percent + "%  →  about " + Math.round(w * percent / 100.0) + " × "
                + Math.round(h * percent / 100.0) + "  (native " + w + " × " + h + ")";
    }
}
