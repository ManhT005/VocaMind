package com.vocamind.app.data.catalog.upgrade;

import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** One atomic recovery record, never a collection of independently updated active-file flags. */
final class CatalogGenerationStore {
    final File root;
    private final AtomicFile pointer;
    CatalogGenerationStore(File root) throws IOException {
        this.root = root;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create generation store");
        pointer = new AtomicFile(new File(root, "active.json"));
    }
    JSONObject read() throws Exception {
        try (InputStream in = pointer.openRead()) {
            JSONObject state = new JSONObject(new String(readBytes(in), StandardCharsets.UTF_8));
            if (state.getInt("format") != 1 || !Arrays.asList("PENDING", "COMMITTED").contains(state.getString("phase")))
                throw new IOException("Invalid catalog state");
            generation(state.getString("active"));
            if (!state.isNull("previous")) generation(state.getString("previous"));
            return state;
        } catch (FileNotFoundException missing) { return null; }
    }
    void write(String active, String previous, String phase) throws Exception {
        generation(active);
        JSONObject state = new JSONObject().put("format", 1).put("active", active)
                .put("previous", previous == null ? JSONObject.NULL : previous).put("phase", phase);
        FileOutputStream out = null;
        try {
            out = pointer.startWrite(); out.write(state.toString().getBytes(StandardCharsets.UTF_8));
            // AtomicFile logs some sync/rename failures instead of throwing; enforce both explicitly.
            out.getFD().sync();
            pointer.finishWrite(out);
        } catch (Exception e) { if (out != null) pointer.failWrite(out); throw e; }
        syncDirectory(root);
        JSONObject persisted = read();
        if (persisted == null || !active.equals(persisted.getString("active"))
                || !phase.equals(persisted.getString("phase"))
                || !Objects.equals(previous, persisted.isNull("previous") ? null : persisted.getString("previous")))
            throw new IOException("Catalog publication record was not persisted");
    }
    CatalogGeneration generation(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid generation ID");
        return new CatalogGeneration(new File(root, id), id);
    }
    static String hash(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) { return hash(in); }
    }
    static String hash(InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = new byte[8192]; int n;
        while ((n = in.read(bytes)) != -1) digest.update(bytes, 0, n);
        StringBuilder s = new StringBuilder(); for (byte b : digest.digest()) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
    static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        return out.toByteArray();
    }
    static void copy(InputStream in, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192]; int n; while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            out.getFD().sync();
        }
    }
    static void syncDirectory(File directory) throws Exception {
        // Android's NIO file open may reject directories; use a native directory descriptor first.
        // The host runner's Os bridge may reject directory opens, so retain a real NIO fsync there.
        try {
            var descriptor = android.system.Os.open(directory.getAbsolutePath(), android.system.OsConstants.O_RDONLY, 0);
            try { android.system.Os.fsync(descriptor); }
            finally { android.system.Os.close(descriptor); }
        } catch (android.system.ErrnoException nativeFailure) {
            try (var channel = java.nio.channels.FileChannel.open(directory.toPath(), StandardOpenOption.READ)) {
                channel.force(true);
            } catch (Exception fallbackFailure) {
                fallbackFailure.addSuppressed(nativeFailure);
                throw fallbackFailure;
            }
        }
    }
}
