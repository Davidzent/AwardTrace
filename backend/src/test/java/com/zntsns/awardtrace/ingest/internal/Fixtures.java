package com.zntsns.awardtrace.ingest.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Contract fixtures: rows cut from the Department of Agriculture's 2026-09 full and delta files. */
final class Fixtures {

    static final String FULL_FILE = "FY2026_012_Contracts_Full_20260909_1.csv";
    static final String DELTA_FILE = "FY(All)_012_Contracts_Delta_20260908_1.csv";

    private Fixtures() {
    }

    static String csv(String fileName) {
        try (var in = Fixtures.class.getResourceAsStream("/fixtures/contracts/" + fileName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A zip holding one entry, shaped like a USAspending archive file. */
    static byte[] zip(String entryName, String content) {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
