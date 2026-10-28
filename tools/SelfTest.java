import app.gridprefs.core.*;
import java.nio.file.*;

public class SelfTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        String orig = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        PrefFile f = new PrefFile(orig);
        check(f.toString().equals(orig), "untouched round trip is byte-identical");
        check(f.entries().size() == 674, "entry count = " + f.entries().size() + " (python XML parser says 674)");

        // typed reads
        check(f.getString(PrefFile.SETUP, "gfxconfig_textures", "").equals("ultrahigh"), "read string");
        check(f.getInt(PrefFile.SETUP, "gfxconfig_max_fps", -1) == 60, "read int");
        check(f.getDouble(PrefFile.SETUP, "FeralFPSCap", 0) == -1.0, "read double -1.0");
        check(f.getDouble(PrefFile.DRS, "MinScaling", 0) == 0.8, "read double 0.8 from DRS key");
        check(GfxSpec.readScale(f, GfxSpec.Profile.POWER_SAVER) == 80, "power saver window_scale = 80");
        check(GfxSpec.readScale(f, GfxSpec.Profile.MAIN) == 100, "main window_scale absent -> 100");

        // every double we know round-trips through hex
        for (PrefFile.Entry e : f.entries())
            if (e.isDouble()) check(PrefFile.doubleToHex(PrefFile.hexToDouble(e.raw)).equals(e.raw), "hex round trip " + e.name);

        // edit one value: exactly one line differs
        f.setString(PrefFile.SETUP, "gfxconfig_textures", "medium");
        String[] o = orig.split("\n", -1), n = f.toString().split("\n", -1);
        int diff = 0; for (int i = 0; i < o.length; i++) if (!o[i].equals(n[i])) diff++;
        check(o.length == n.length && diff == 1, "single edit changes exactly 1 line (" + diff + ")");

        // add a missing key: exactly one line added, inside Setup, entry count +1
        f.setInt(PrefFile.SETUP, "gfxconfig_window_scale", 70);
        check(f.entries().size() == 675, "added key -> 675 entries");
        check(GfxSpec.readScale(f, GfxSpec.Profile.MAIN) == 70, "added window_scale reads back");
        check(f.find(PrefFile.SETUP, "gfxconfig_window_scale").path.equals(PrefFile.SETUP), "added key lives in Setup");

        // double write keeps type + length
        f.setDouble(PrefFile.DRS, "MinScaling", 0.65);
        check(f.getDouble(PrefFile.DRS, "MinScaling", 0) == 0.65 && f.find(PrefFile.DRS, "MinScaling").raw.length() == 16, "double write");

        // preset on all three profiles, then re-read through the spec
        for (GfxSpec.Profile p : GfxSpec.Profile.values()) GfxSpec.applyPreset(f, p, "low", false);
        boolean ok = true;
        for (GfxSpec.Profile p : GfxSpec.Profile.values())
            for (GfxSpec.Setting s : GfxSpec.LEVELS) {
                String v = GfxSpec.readString(f, p, s);
                if (!(s.id.equals("ai_count") && p == GfxSpec.Profile.MAIN && v == null) && !"low".equals(v)) { ok = false; System.out.println("  bad " + p + " " + s.id + "=" + v); }
            }
        check(ok, "preset 'low' applied to every dropdown in every profile");
        check("on".equals(f.getString(PrefFile.SETUP, "gfxconfig_skidmarks", "")) && "off".equals(f.getString(PrefFile.SETUP, "gfxconfig_advanced_lighting", "")), "preset low: skidmarks on, lighting off");
        GfxSpec.applyPreset(f, GfxSpec.Profile.MAIN, "ultralow", false);
        check("off".equals(f.getString(PrefFile.SETUP, "gfxconfig_particles", "")), "ultralow turns off-able items off");

        // ia_count alias is kept in sync
        check(f.has(PrefFile.SETUP, "gfxconfig_ia_count"), "sample has main 'ia_count' spelling");
        check("ultralow".equals(f.getString(PrefFile.SETUP, "gfxconfig_ia_count", "")), "ia_count alias kept in sync");

        // re-parse the output: still well-formed with same entries
        PrefFile g = new PrefFile(f.toString());
        check(g.entries().size() == f.entries().size(), "re-parse of edited output keeps all entries");

        // string escaping
        f.setString(PrefFile.SETUP, "CommandLineText", "a<b&c");
        check(f.getString(PrefFile.SETUP, "CommandLineText", "").equals("a<b&c") && f.toString().contains("a&lt;b&amp;c"), "xml escaping");

        // every key the UI references exists in the sample (or is a known-addable one)
        PrefFile fresh = new PrefFile(orig);
        for (GfxSpec.Toggle t : GfxSpec.EXTRA_TOGGLES)
            check(fresh.has(PrefFile.SETUP, t.key) && fresh.find(PrefFile.SETUP, t.key).isInteger(), "toggle key exists as integer: " + t.key);
        for (GfxSpec.Profile p : GfxSpec.Profile.values())
            for (GfxSpec.Setting s : GfxSpec.LEVELS) if (GfxSpec.readString(fresh, p, s) == null) System.out.println("NOTE  not in sample: " + p + " " + s.id);
        for (String k : new String[]{"FeralFPSCap", "FeralNightFPSCap"}) check(fresh.getDouble(PrefFile.SETUP, k, 99) != 99, "fps cap double exists " + k);
        for (String k : new String[]{"Enable", "FPSTarget", "MinScaling"}) check(fresh.has(PrefFile.DRS, k), "DRS key exists " + k);

        // ---- GameFiles: fake game dir with the real file + decoys
        java.nio.file.Path tmp = Files.createTempDirectory("gamefiles");
        java.nio.file.Path files = Files.createDirectories(tmp.resolve("Android/data/x/files/sub"));
        Files.write(files.resolve("preferences"), orig.getBytes("UTF-8"));
        Files.write(files.resolve("notes"), "hello".getBytes());
        Files.write(files.resolve("data.bin"), new byte[100]);
        Files.write(tmp.resolve("Android/data/x/other"), "<registry><key name=\"a\">".getBytes());
        java.util.List<String> hits = GameFiles.findDirect(java.util.Arrays.asList(tmp.toString()), 6);
        check(hits.size() == 2, "finder returns valid pref-like files only (" + hits.size() + ")");
        String real = files.resolve("preferences").toRealPath().toString();
        check(hits.contains(real), "finder located the real preferences file");
        check(GameFiles.findDirect(java.util.Arrays.asList("/nonexistent/dir"), 5).isEmpty(), "missing dir -> no results, no crash");
        byte[] got = GameFiles.readDirect(real);
        check(new String(got, "UTF-8").equals(orig), "readDirect returns identical bytes");
        PrefFile h = new PrefFile(new String(got, "UTF-8"));
        h.setString(PrefFile.SETUP, "gfxconfig_water", "low");
        GameFiles.writeDirect(real, h.toString().getBytes("UTF-8"));
        check(new PrefFile(new String(GameFiles.readDirect(real), "UTF-8")).getString(PrefFile.SETUP, "gfxconfig_water", "").equals("low"), "writeDirect persists edit");
        check(GameFiles.su("true", null, 3000) == null || true, "su probe does not throw");

        // ---- Advisor
        check(Advisor.recommend(11500, 120f).level.equals("ultrahigh") && Advisor.recommend(11500, 120f).fps == 120, "12GB/120Hz -> ultrahigh @120");
        check(Advisor.recommend(7600, 60f).level.equals("high") && Advisor.recommend(7600, 60f).fps == 60, "8GB/60Hz -> high @60 (capped by display)");
        check(Advisor.recommend(2800, 60f).level.equals("ultralow") && Advisor.recommend(2800, 60f).fps == 30, "3GB -> ultralow @30");
        PrefFile ap = new PrefFile(orig);
        Advisor.apply(ap, GfxSpec.Profile.MAIN, Advisor.recommend(5600, 60f), true);
        check(ap.getString(PrefFile.SETUP, "gfxconfig_textures", "").equals("medium") && ap.getString(PrefFile.SETUP, "gfxconfig_high_textures", "").equals("medium"), "advisor applies preset to Main and High");
        check(ap.getInt(PrefFile.SETUP, "gfxconfig_max_fps", 0) == 60 && GfxSpec.readScale(ap, GfxSpec.Profile.MAIN) == 80 && ap.getDouble(PrefFile.DRS, "FPSTarget", 0) == 60.0, "advisor sets fps, scale, DRS target");
        check(new PrefFile(ap.toString()).entries().size() == ap.entries().size(), "advised file re-parses");

        System.out.println(fails == 0 ? "\nALL PASSED" : "\n" + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
