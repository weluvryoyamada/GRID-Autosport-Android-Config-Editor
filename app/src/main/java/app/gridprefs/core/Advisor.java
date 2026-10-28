package app.gridprefs.core;

/**
 * Turns detected device specs into a suggested setup. These are rough rules of thumb based on
 * RAM and display refresh rate (GPU name is shown for reference, not scored), so the result is a
 * starting point to tweak, not a benchmark.
 */
public final class Advisor {
    private Advisor() {}

    public static final class Plan {
        public final String level;
        public final int fps;
        public final int scalePercent;
        public final String reason;

        Plan(String level, int fps, int scalePercent, String reason) {
            this.level = level;
            this.fps = fps;
            this.scalePercent = scalePercent;
            this.reason = reason;
        }
    }

    /** @param ramMb total RAM as reported by Android (a "12 GB" phone reports roughly 11,000 MB) */
    public static Plan recommend(long ramMb, float refreshHz) {
        String level;
        if (ramMb >= 10500) level = "ultrahigh";
        else if (ramMb >= 7000) level = "high";
        else if (ramMb >= 5000) level = "medium";
        else if (ramMb >= 3500) level = "low";
        else level = "ultralow";

        int rank = java.util.Arrays.asList(GfxSpec.LADDER).indexOf(level);
        int fps;
        if (rank == 4 && refreshHz >= 115f) fps = 120;
        else if (rank >= 3 && refreshHz >= 85f) fps = 90;
        else if (rank >= 2) fps = 60;
        else if (rank == 1) fps = 45;
        else fps = 30;

        int[] scale = {60, 70, 80, 90, 100};
        String reason = String.format(java.util.Locale.US,
                "%.1f GB RAM and a %.0f Hz display suggest %s quality at %d FPS.",
                ramMb / 1024.0, refreshHz, GfxSpec.pretty(level), fps);
        return new Plan(level, fps, scale[rank], reason);
    }

    /** Applies a plan to one profile (and optionally mirrors Main into High). */
    public static void apply(PrefFile f, GfxSpec.Profile p, Plan plan, boolean alsoHigh) {
        GfxSpec.applyPreset(f, p, plan.level, alsoHigh);
        GfxSpec.writeInt(f, p, "max_fps", plan.fps, alsoHigh);
        GfxSpec.writeInt(f, p, "window_scale", plan.scalePercent, alsoHigh);
        if (f.has(PrefFile.DRS, "FPSTarget")) f.setDouble(PrefFile.DRS, "FPSTarget", Math.min(plan.fps, 60));
    }
}
