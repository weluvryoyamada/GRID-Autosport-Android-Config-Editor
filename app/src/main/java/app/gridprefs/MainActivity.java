package app.gridprefs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.gridprefs.core.GfxSpec;
import app.gridprefs.core.GfxSpec.Profile;
import app.gridprefs.core.GfxSpec.Setting;
import app.gridprefs.core.PrefFile;

public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE_AS = 2;

    private static final int BG = Color.rgb(18, 20, 24);
    private static final int CARD = Color.rgb(30, 34, 41);
    private static final int TEXT = Color.rgb(235, 238, 242);
    private static final int MUTED = Color.rgb(150, 158, 170);
    private static final int ACCENT = Color.rgb(255, 122, 24);

    private PrefFile pref;
    private Uri currentUri;
    private boolean dirty;

    private Profile profile = Profile.MAIN;
    private boolean mirrorToHigh = true;
    private int tab = 0;

    private TextView status;
    private FrameLayout content;
    private Button[] tabButtons;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildShell();
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        String last = sp.getString("lastUri", null);
        if (last != null) {
            try {
                load(Uri.parse(last), false);
            } catch (Exception e) {
                // permission gone or file moved: just start empty
            }
        }
        render();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ shell

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(28), 0, 0);

        TextView title = text("GRID Autosport settings editor", 18, TEXT, true);
        title.setPadding(dp(16), dp(8), dp(16), 0);
        root.addView(title);

        status = text("No file open", 12, MUTED, false);
        status.setPadding(dp(16), dp(2), dp(16), dp(8));
        root.addView(status);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        bar.addView(button("Open", new View.OnClickListener() {
            public void onClick(View v) { openFile(); }
        }), weight());
        bar.addView(button("Save", new View.OnClickListener() {
            public void onClick(View v) { save(); }
        }), weight());
        bar.addView(button("Save as…", new View.OnClickListener() {
            public void onClick(View v) { saveAs(); }
        }), weight());
        root.addView(bar);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(4), dp(8), dp(4));
        String[] names = {"Graphics", "Resolution", "Extras", "All values"};
        tabButtons = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button t = button(names[i], new View.OnClickListener() {
                public void onClick(View v) { tab = idx; render(); }
            });
            t.setTextSize(12);
            tabButtons[i] = t;
            tabs.addView(t, weight());
        }
        root.addView(tabs);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void setStatus() {
        if (pref == null) {
            status.setText("No file open — tap Open and pick your 'preferences' file");
        } else {
            String n = currentUri == null ? "file" : lastSegment(currentUri);
            status.setText(n + (dirty ? "  • unsaved changes" : "  • saved"));
        }
    }

    private static String lastSegment(Uri u) {
        String s = u.getLastPathSegment();
        if (s == null) return u.toString();
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    // ------------------------------------------------------------------ file IO

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    private void saveAs() {
        if (pref == null) { toast("Open a file first"); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, "preferences");
        startActivityForResult(i, REQ_SAVE_AS);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_OPEN) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
                // some providers do not offer persistable grants; still fine for this session
            }
            try {
                load(uri, true);
            } catch (Exception e) {
                toast("Could not open file: " + e.getMessage());
            }
            render();
        } else if (req == REQ_SAVE_AS) {
            if (writeTo(uri)) {
                currentUri = uri;
                getSharedPreferences("app", MODE_PRIVATE).edit().putString("lastUri", uri.toString()).apply();
                dirty = false;
                setStatus();
            }
        }
    }

    private void load(Uri uri, boolean announce) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("cannot read");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        } finally {
            in.close();
        }
        byte[] bytes = bos.toByteArray();
        String text = new String(bytes, "UTF-8");
        PrefFile p = new PrefFile(text);
        if (!p.has(PrefFile.SETUP, "ScreenW") && p.getString(PrefFile.SETUP, "gfxconfig_textures", null) == null) {
            throw new Exception("this does not look like a GRID Autosport preferences file");
        }
        backup(bytes);
        pref = p;
        currentUri = uri;
        dirty = false;
        getSharedPreferences("app", MODE_PRIVATE).edit().putString("lastUri", uri.toString()).apply();
        setStatus();
        if (announce) toast("Opened. A backup copy was saved inside the app.");
    }

    /** Keeps an untouched copy of every file we open, in the app's private storage. */
    private void backup(byte[] bytes) {
        try {
            File dir = new File(getFilesDir(), "backups");
            if (!dir.exists() && !dir.mkdirs()) return;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            FileOutputStream out = new FileOutputStream(new File(dir, stamp + "_preferences"));
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            // keep only the 10 newest
            File[] all = dir.listFiles();
            if (all != null && all.length > 10) {
                Arrays.sort(all);
                for (int i = 0; i < all.length - 10; i++) all[i].delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        if (pref == null) { toast("Open a file first"); return; }
        if (currentUri == null) { saveAs(); return; }
        if (writeTo(currentUri)) {
            dirty = false;
            setStatus();
        }
    }

    private boolean writeTo(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri, "wt");
            if (os == null) throw new Exception("cannot write");
            try {
                os.write(pref.toString().getBytes("UTF-8"));
                os.flush();
            } finally {
                os.close();
            }
            toast("Saved");
            return true;
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage() + " — try 'Save as…'");
            return false;
        }
    }

    private void changed() {
        dirty = true;
        setStatus();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setAlpha(i == tab ? 1f : 0.55f);
        }
        setStatus();
        content.removeAllViews();
        if (pref == null) {
            TextView t = text("Open your GRID Autosport 'preferences' file to start editing.\n\n"
                    + "An untouched backup is kept inside this app every time you open a file.", 14, MUTED, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(24), dp(24), dp(24), dp(24));
            content.addView(t);
            return;
        }
        switch (tab) {
            case 0: content.addView(scroll(graphicsView())); break;
            case 1: content.addView(scroll(resolutionView())); break;
            case 2: content.addView(scroll(extrasView())); break;
            default: content.addView(rawView()); break;
        }
    }

    private ScrollView scroll(View child) {
        ScrollView sv = new ScrollView(this);
        sv.addView(child);
        return sv;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(4), dp(12), dp(24));
        return c;
    }

    private LinearLayout row(String label, String hint, View control) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundColor(CARD);
        r.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        r.setLayoutParams(lp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(label, 14, TEXT, false));
        if (hint != null) labels.addView(text(hint, 10, MUTED, false));
        r.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(control);
        return r;
    }

    private TextView header(String s) {
        TextView t = text(s, 12, ACCENT, true);
        t.setPadding(dp(4), dp(14), 0, dp(4));
        return t;
    }

    // ------------------------------------------------------------------ Graphics tab

    private View graphicsView() {
        final LinearLayout c = column();

        // profile picker
        final Spinner prof = new Spinner(this);
        List<String> pn = new ArrayList<>();
        for (Profile p : Profile.values()) pn.add(p.label);
        prof.setAdapter(adapter(pn));
        prof.setSelection(profile.ordinal(), false);
        prof.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                Profile np = Profile.values()[pos];
                if (np != profile) { profile = np; render(); }
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        c.addView(row("Profile being edited",
                "Main = what the game uses now. Power saver = used when battery saver is on.", prof));

        if (profile == Profile.MAIN) {
            Switch mirror = new Switch(this);
            mirror.setChecked(mirrorToHigh);
            mirror.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) { mirrorToHigh = on; }
            });
            c.addView(row("Also write to High profile", "Keeps gfxconfig_high_* identical to Main", mirror));
        }

        // presets
        c.addView(header("QUICK PRESETS  (sets every dropdown and switch below)"));
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        for (final String lvl : GfxSpec.PRESETS) {
            Button b = button(GfxSpec.pretty(lvl), new View.OnClickListener() {
                public void onClick(View v) {
                    GfxSpec.applyPreset(pref, profile, lvl, mirrorToHigh);
                    changed();
                    render();
                }
            });
            b.setTextSize(11);
            b.setPadding(0, 0, 0, 0);
            presets.addView(b, weight());
        }
        c.addView(presets);

        c.addView(header("QUALITY LEVELS"));
        for (final Setting s : GfxSpec.LEVELS) {
            c.addView(levelRow(s));
        }

        c.addView(header("SWITCHES"));
        for (final Setting s : GfxSpec.SWITCHES) {
            c.addView(levelRow(s));
        }

        c.addView(header("ANTI-ALIASING"));
        c.addView(msaaRow());
        return c;
    }

    private View levelRow(final Setting s) {
        String cur = GfxSpec.readString(pref, profile, s);
        final List<String> raw = new ArrayList<>(Arrays.asList(s.options));
        int sel;
        if (cur == null) {
            raw.add(0, "");
            sel = 0;
        } else {
            if (!raw.contains(cur)) raw.add(0, cur);
            sel = raw.indexOf(cur);
        }
        List<String> labels = new ArrayList<>();
        for (String r : raw) labels.add(r.length() == 0 ? "(not set)" : GfxSpec.pretty(r));

        Spinner sp = new Spinner(this);
        sp.setAdapter(adapter(labels));
        sp.setSelection(sel, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                String val = raw.get(pos);
                if (val.length() == 0) return;
                String now = GfxSpec.readString(pref, profile, s);
                if (val.equals(now)) return;
                GfxSpec.writeString(pref, profile, s, val, mirrorToHigh);
                changed();
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        return row(s.label, GfxSpec.key(profile, s.id), sp);
    }

    private View msaaRow() {
        return levelRow(GfxSpec.MULTISAMPLING);
    }

    // ------------------------------------------------------------------ Resolution tab

    private View resolutionView() {
        LinearLayout c = column();

        // profile picker (resolution slider exists per profile)
        final Spinner prof = new Spinner(this);
        List<String> pn = new ArrayList<>();
        for (Profile p : Profile.values()) pn.add(p.label);
        prof.setAdapter(adapter(pn));
        prof.setSelection(profile.ordinal(), false);
        prof.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                Profile np = Profile.values()[pos];
                if (np != profile) { profile = np; render(); }
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        c.addView(row("Profile being edited", null, prof));

        c.addView(header("RESOLUTION SLIDER  (" + GfxSpec.key(profile, "window_scale") + ")"));
        final TextView desc = text("", 13, TEXT, false);
        desc.setPadding(dp(4), dp(4), dp(4), dp(4));
        final SeekBar sb = new SeekBar(this);
        sb.setMax((GfxSpec.SCALE_MAX - GfxSpec.SCALE_MIN) / 5);
        int cur = GfxSpec.readScale(pref, profile);
        sb.setProgress(Math.max(0, Math.min(sb.getMax(), (cur - GfxSpec.SCALE_MIN) / 5)));
        desc.setText(pref.has(PrefFile.SETUP, GfxSpec.key(profile, "window_scale"))
                ? GfxSpec.describeScale(pref, cur)
                : "Not set in file (game default, 100%). Move the slider to add it.");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int pct = GfxSpec.SCALE_MIN + p * 5;
                desc.setText(GfxSpec.describeScale(pref, pct));
                if (fromUser) {
                    GfxSpec.writeInt(pref, profile, "window_scale", pct, mirrorToHigh);
                    changed();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(CARD);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(desc);
        box.addView(sb);
        c.addView(box);

        c.addView(header("FRAME RATE"));
        c.addView(fpsRow("Max FPS (" + GfxSpec.key(profile, "max_fps") + ")"));
        c.addView(capRow("FeralFPSCap", "FPS cap (-1 = uncapped)"));
        c.addView(capRow("FeralNightFPSCap", "Night FPS cap (-1 = uncapped)"));

        c.addView(header("DYNAMIC RESOLUTION  (drops resolution to hold the target FPS)"));
        c.addView(drsView());
        return c;
    }

    private View fpsRow(String label) {
        final String id = "max_fps";
        int cur = pref.getInt(PrefFile.SETUP, GfxSpec.key(profile, id), -1);
        final List<Integer> vals = new ArrayList<>();
        for (int f : GfxSpec.FPS) vals.add(f);
        if (cur < 0) vals.add(0, -1);
        else if (!vals.contains(cur)) { vals.add(cur); java.util.Collections.sort(vals); }
        List<String> labels = new ArrayList<>();
        for (int v : vals) labels.add(v < 0 ? "(not set)" : v + " FPS");
        Spinner sp = new Spinner(this);
        sp.setAdapter(adapter(labels));
        sp.setSelection(vals.indexOf(cur), false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long rid) {
                int val = vals.get(pos);
                if (val < 0) return;
                if (val == pref.getInt(PrefFile.SETUP, GfxSpec.key(profile, id), -1)) return;
                GfxSpec.writeInt(pref, profile, id, val, mirrorToHigh);
                changed();
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        return row(label, null, sp);
    }

    private View capRow(final String key, String label) {
        double cur = pref.getDouble(PrefFile.SETUP, key, Double.NaN);
        final List<Double> vals = new ArrayList<>();
        vals.add(-1.0);
        for (int f : GfxSpec.FPS) vals.add((double) f);
        boolean missing = Double.isNaN(cur);
        if (!missing && !vals.contains(cur)) vals.add(cur);
        if (missing) vals.add(0, Double.NaN);
        List<String> labels = new ArrayList<>();
        for (double v : vals) {
            labels.add(Double.isNaN(v) ? "(not set)" : (v < 0 ? "Uncapped" : trim(v) + " FPS"));
        }
        Spinner sp = new Spinner(this);
        sp.setAdapter(adapter(labels));
        sp.setSelection(missing ? 0 : vals.indexOf(cur), false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long rid) {
                double val = vals.get(pos);
                if (Double.isNaN(val)) return;
                double now = pref.getDouble(PrefFile.SETUP, key, Double.NaN);
                if (!Double.isNaN(now) && now == val) return;
                pref.setDouble(PrefFile.SETUP, key, val);
                changed();
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        return row(label, key, sp);
    }

    private View drsView() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        if (!pref.has(PrefFile.DRS, "Enable")) {
            c.addView(text("No DynamicResolutionScaling block in this file.", 12, MUTED, false));
            return c;
        }
        Switch en = new Switch(this);
        en.setChecked(pref.getInt(PrefFile.DRS, "Enable", 0) != 0);
        en.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                pref.setInt(PrefFile.DRS, "Enable", on ? 1 : 0);
                changed();
            }
        });
        c.addView(row("Dynamic resolution", "DynamicResolutionScaling/Enable", en));

        // target FPS
        double cur = pref.getDouble(PrefFile.DRS, "FPSTarget", 30);
        final List<Double> vals = new ArrayList<>();
        for (int f : GfxSpec.FPS) vals.add((double) f);
        if (!vals.contains(cur)) vals.add(cur);
        List<String> labels = new ArrayList<>();
        for (double v : vals) labels.add(trim(v) + " FPS");
        Spinner sp = new Spinner(this);
        sp.setAdapter(adapter(labels));
        sp.setSelection(vals.indexOf(cur), false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long rid) {
                double val = vals.get(pos);
                if (val == pref.getDouble(PrefFile.DRS, "FPSTarget", -1)) return;
                pref.setDouble(PrefFile.DRS, "FPSTarget", val);
                changed();
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        c.addView(row("Target FPS", "FPSTarget", sp));

        // minimum scaling 0.50 .. 1.00
        final TextView d = text("", 13, TEXT, false);
        SeekBar sb = new SeekBar(this);
        sb.setMax(10); // 0.50 + 0.05*n
        double min = pref.getDouble(PrefFile.DRS, "MinScaling", 0.8);
        int prog = (int) Math.round((min - 0.5) / 0.05);
        sb.setProgress(Math.max(0, Math.min(10, prog)));
        d.setText("Lowest allowed resolution: " + Math.round(min * 100) + "%");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                double v = Math.round((0.5 + 0.05 * p) * 100) / 100.0;
                d.setText("Lowest allowed resolution: " + Math.round(v * 100) + "%");
                if (fromUser) {
                    pref.setDouble(PrefFile.DRS, "MinScaling", v);
                    changed();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(CARD);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(d);
        box.addView(sb);
        box.addView(text("MinScaling", 10, MUTED, false));
        c.addView(box);
        return c;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    // ------------------------------------------------------------------ Extras tab

    private View extrasView() {
        LinearLayout c = column();
        c.addView(header("ON / OFF TWEAKS  (raw key shown under each name)"));
        for (final GfxSpec.Toggle t : GfxSpec.EXTRA_TOGGLES) {
            Switch sw = new Switch(this);
            sw.setChecked(pref.getInt(PrefFile.SETUP, t.key, 0) != 0);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    pref.setInt(PrefFile.SETUP, t.key, on ? 1 : 0);
                    changed();
                }
            });
            c.addView(row(t.label, t.key, sw));
        }
        return c;
    }

    // ------------------------------------------------------------------ All values tab

    private final List<PrefFile.Entry> shown = new ArrayList<>();

    private View rawView() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(4), dp(12), dp(4));

        final EditText filter = new EditText(this);
        filter.setHint("Filter by name, e.g. shadow, fps, gfxconfig");
        filter.setHintTextColor(MUTED);
        filter.setTextColor(TEXT);
        filter.setSingleLine(true);
        c.addView(filter);

        final ListView lv = new ListView(this);
        final ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<String>());
        lv.setAdapter(ad);
        c.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable refill = new Runnable() {
            public void run() {
                String q = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                ad.clear();
                for (PrefFile.Entry e : pref.entries()) {
                    if (!e.path.startsWith(PrefFile.SETUP)) continue;
                    if (q.length() > 0 && !e.name.toLowerCase(Locale.ROOT).contains(q)) continue;
                    shown.add(e);
                    ad.add(e.name + "  =  " + display(e));
                }
                ad.notifyDataSetChanged();
            }
        };
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int d) {}
            public void afterTextChanged(Editable e) { refill.run(); }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                editEntry(shown.get(pos), refill);
            }
        });
        refill.run();
        return c;
    }

    private static String display(PrefFile.Entry e) {
        if (e.isDouble()) return trim(PrefFile.hexToDouble(e.raw)) + "  (decimal)";
        if ("binary".equals(e.type)) return "(binary, " + e.raw.length() / 2 + " bytes, read-only)";
        String t = e.text();
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    private void editEntry(final PrefFile.Entry e, final Runnable after) {
        if ("binary".equals(e.type) && !e.isDouble()) {
            toast("Binary blobs are left untouched");
            return;
        }
        final EditText et = new EditText(this);
        et.setText(e.isDouble() ? trim(PrefFile.hexToDouble(e.raw)) : e.text());
        if (e.isInteger()) et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (e.isDouble()) et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle(e.name)
                .setMessage("Type: " + (e.isDouble() ? "decimal number" : e.type))
                .setView(et)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Set", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            pref.setFromText(e, et.getText().toString());
                            changed();
                            after.run();
                        } catch (Exception ex) {
                            toast("Not a valid value");
                        }
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ helpers

    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }
}
