package com.katalon.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class FileUtils {

    public static void downloadAndExtract(Logger logger, String fileUrl, File targetDir)
            throws IOException {

        LogUtils.info(logger, "Downloading Katalon Studio from " + fileUrl + ". It may take a few minutes.");

        URL url = new URL(fileUrl);

        try (InputStream inputStream = url.openStream()) {
            Path temporaryFile = Files.createTempFile("Katalon-", "");
            Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            LogUtils.info(logger, "Extract " + temporaryFile + " to " + targetDir);

            try {
                if (fileUrl.endsWith(".zip")) {
                    extractZip(temporaryFile, targetDir.toPath());
                } else if (fileUrl.endsWith(".tar.gz")) {
                    extractTarGz(temporaryFile, targetDir.toPath());
                } else {
                    throw new IllegalStateException("Unsupported file type: " + fileUrl + ".");
                }
            } catch (Exception e) {
                LogUtils.info(logger, "Failed to extract " + temporaryFile + " to " + targetDir);
                throw e;
            } finally {
                var deleted = temporaryFile.toFile().delete();
                if (!deleted) {
                    LogUtils.info(logger, "Failed to delete temporary file: " + temporaryFile);
                }
            }
        }
    }

    private static void extractZip(Path archivePath, Path targetDir) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new BufferedInputStream(Files.newInputStream(archivePath)))) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = resolveEntryPath(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    var parentPath = entryPath.getParent();
                    if (parentPath != null) {
                        Files.createDirectories(parentPath);
                    }

                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void extractTarGz(Path archivePath, Path targetDir) throws IOException {
        try (GzipCompressorInputStream gzis = new GzipCompressorInputStream(
                new BufferedInputStream(Files.newInputStream(archivePath)));
             TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                Path entryPath = resolveEntryPath(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    var parentPath = entryPath.getParent();
                    if (parentPath != null) {
                        Files.createDirectories(parentPath);
                    }

                    Files.copy(tis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // Guard against zip slip: entry path must stay within targetDir
    private static Path resolveEntryPath(Path targetDir, String entryName) throws IOException {
        Path resolved = targetDir.resolve(entryName).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new IOException("Blocked path traversal attempt: " + entryName);
        }
        return resolved;
    }
}
