package app.gridprefs.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds and reads/writes the game's preferences file directly on the device.
 *
 * Plain file access only works where Android allows it (Android 10 and older, or any
 * location the app itself may read). Everything else needs root ("su"). Both routes are
 * tried; callers get the paths that actually worked.
 */
public final class GameFiles {
    private GameFiles() {}

    public static final String PACKAGE = "com.feralinteractive.gridautosport";

    public static List<String> candidateDirs() {
        List<String> d = new ArrayList<>();
        d.add("/storage/emulated/0/Android/data/" + PACKAGE);
        d.add("/sdcard/Android/data/" + PACKAGE);
        d.add("/data/media/0/Android/data/" + PACKAGE);
        d.add("/data/data/" + PACKAGE);
        d.add("/data/user/0/" + PACKAGE);
        return d;
    }

    // ------------------------------------------------------------ validation

    public static boolean looksLikePrefs(byte[] head) {
        String s = new String(head, java.nio.charset.StandardCharsets.UTF_8);
        return s.contains("<registry>") && s.contains("<key name=");
    }

    // ------------------------------------------------------------ direct access

    /** Walks the dirs (bounded) and returns real paths of valid preference files, de-duplicated. */
    public static List<String> findDirect(List<String> dirs, int maxDepth) {
        List<String> found = new ArrayList<>();
        int[] budget = {4000};
        for (String d : dirs) {
            File root = new File(d);
            try {
                if (root.isDirectory() && root.canRead()) walk(root, 0, maxDepth, found, budget);
            } catch (SecurityException ignored) {
            }
        }
        return found;
    }

    private static void walk(File dir, int depth, int maxDepth, List<String> out, int[] budget) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (--budget[0] < 0) return;
            if (f.isDirectory()) {
                if (depth < maxDepth) walk(f, depth + 1, maxDepth, out, budget);
            } else if (f.length() > 0 && f.length() < 4_000_000 && !f.getName().contains(".")) {
                try {
                    byte[] head = readHead(f, 600);
                    if (looksLikePrefs(head)) {
                        String p = f.getCanonicalPath();
                        if (!out.contains(p)) out.add(p);
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static byte[] readHead(File f, int n) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[n];
            int got = 0, r;
            while (got < n && (r = in.read(buf, got, n - got)) > 0) got += r;
            byte[] out = new byte[got];
            System.arraycopy(buf, 0, out, 0, got);
            return out;
        } finally {
            in.close();
        }
    }

    public static byte[] readDirect(String path) throws IOException {
        InputStream in = new FileInputStream(path);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    /** Overwrites in place (keeps owner and permissions of the existing file). */
    public static void writeDirect(String path, byte[] data) throws IOException {
        OutputStream out = new FileOutputStream(path, false);
        try {
            out.write(data);
            out.flush();
        } finally {
            out.close();
        }
    }

    // ------------------------------------------------------------ root access

    public static final class RootResult {
        public final byte[] out;
        public final int code;

        RootResult(byte[] out, int code) {
            this.out = out;
            this.code = code;
        }
    }

    /** Runs a command through su. Returns null if su is not available or times out. */
    public static RootResult su(String command, byte[] stdin, final long timeoutMs) {
        try {
            final Process p = new ProcessBuilder("su", "-c", command).redirectErrorStream(false).start();
            Thread dog = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(timeoutMs);
                        p.destroy();
                    } catch (InterruptedException ignored) {
                    }
                }
            });
            dog.setDaemon(true);
            dog.start();
            try {
                OutputStream os = p.getOutputStream();
                if (stdin != null) os.write(stdin);
                os.close();
                byte[] out = readAll(p.getInputStream());
                int code = p.waitFor();
                dog.interrupt();
                return new RootResult(out, code);
            } finally {
                p.destroy();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> findViaRoot() {
        List<String> found = new ArrayList<>();
        for (String d : candidateDirs()) {
            RootResult r = su("find '" + d + "' -maxdepth 5 -type f ! -name '*.*' -size -4000k 2>/dev/null", null, 25000);
            if (r == null) return found; // no su at all: stop trying
            String[] lines = new String(r.out, java.nio.charset.StandardCharsets.UTF_8).split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.length() == 0 || found.contains(line)) continue;
                RootResult head = su("head -c 600 '" + line + "'", null, 8000);
                if (head != null && looksLikePrefs(head.out)) found.add(line);
            }
        }
        return found;
    }

    public static byte[] readViaRoot(String path) throws IOException {
        RootResult r = su("cat '" + path + "'", null, 20000);
        if (r == null || r.code != 0) throw new IOException("root read failed");
        return r.out;
    }

    public static void writeViaRoot(String path, byte[] data) throws IOException {
        RootResult r = su("cat > '" + path + "'", data, 20000);
        if (r == null || r.code != 0) throw new IOException("root write failed");
    }

    // ------------------------------------------------------------ util

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
