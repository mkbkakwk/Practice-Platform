package com.oj.runner.execution.docker;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class SandboxArtifactArchive {

    private SandboxArtifactArchive() {
    }

    static byte[] source(String filename, String sourceCode) {
        return archive(Map.of(filename, new Artifact(sourceCode.getBytes(StandardCharsets.UTF_8), 0400)));
    }

    static byte[] stdin(byte[] bytes) {
        return archive(Map.of("stdin", new Artifact(bytes, 0400)));
    }

    private static byte[] archive(Map<String, Artifact> files) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             TarArchiveOutputStream output = new TarArchiveOutputStream(bytes)) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
            for (Map.Entry<String, Artifact> file : files.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(file.getValue().bytes().length);
                entry.setMode(file.getValue().mode());
                entry.setUserId(10_001);
                entry.setGroupId(10_001);
                entry.setModTime(0);
                output.putArchiveEntry(entry);
                output.write(file.getValue().bytes());
                output.closeArchiveEntry();
            }
            output.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create sandbox archive", exception);
        }
    }

    private record Artifact(byte[] bytes, int mode) {
    }
}
