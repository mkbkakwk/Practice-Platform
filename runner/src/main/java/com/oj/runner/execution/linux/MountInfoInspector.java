package com.oj.runner.execution.linux;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

final class MountInfoInspector {

    private MountInfoInspector() {
    }

    static ReadOnlyStatus inspectReadOnly(Path target, String mountInfo) {
        if (target == null || !target.isAbsolute()
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target)) {
            return ReadOnlyStatus.TARGET_UNAVAILABLE;
        }
        if (mountInfo == null || mountInfo.isBlank()) {
            return ReadOnlyStatus.INVALID;
        }

        try {
            Path normalizedTarget = target.normalize();
            MountEntry best = null;
            for (String line : mountInfo.lines().toList()) {
                MountEntry entry = parse(line);
                if (normalizedTarget.startsWith(entry.mountPoint())
                        && (best == null || entry.mountPoint().getNameCount() > best.mountPoint().getNameCount())) {
                    best = entry;
                }
            }
            if (best == null) {
                return ReadOnlyStatus.INVALID;
            }
            return best.readOnly() ? ReadOnlyStatus.READ_ONLY : ReadOnlyStatus.READ_WRITE;
        } catch (IllegalArgumentException exception) {
            return ReadOnlyStatus.INVALID;
        }
    }

    private static MountEntry parse(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Blank mountinfo line");
        }
        String[] sections = line.split(" - ", -1);
        if (sections.length != 2) {
            throw new IllegalArgumentException("Malformed mountinfo separator");
        }
        String[] left = sections[0].split(" ");
        String[] right = sections[1].split(" ");
        if (left.length < 6 || right.length < 3) {
            throw new IllegalArgumentException("Malformed mountinfo fields");
        }

        Path mountPoint = Path.of(unescapePath(left[4])).normalize();
        if (!mountPoint.isAbsolute()) {
            throw new IllegalArgumentException("Mount point is not absolute");
        }
        Set<String> options = Set.of(left[5].split(","));
        boolean readOnly = options.contains("ro");
        boolean readWrite = options.contains("rw");
        if (readOnly == readWrite) {
            throw new IllegalArgumentException("Mount options lack an unambiguous ro/rw state");
        }
        return new MountEntry(mountPoint, readOnly);
    }

    private static String unescapePath(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                decoded.append(current);
                continue;
            }
            if (index + 3 >= value.length()) {
                throw new IllegalArgumentException("Truncated mountinfo escape");
            }
            String escape = value.substring(index + 1, index + 4);
            decoded.append(switch (escape) {
                case "040" -> ' ';
                case "011" -> '\t';
                case "012" -> '\n';
                case "134" -> '\\';
                default -> throw new IllegalArgumentException("Unknown mountinfo escape");
            });
            index += 3;
        }
        return decoded.toString();
    }

    enum ReadOnlyStatus {
        READ_ONLY,
        READ_WRITE,
        INVALID,
        TARGET_UNAVAILABLE
    }

    private record MountEntry(Path mountPoint, boolean readOnly) {
    }
}
