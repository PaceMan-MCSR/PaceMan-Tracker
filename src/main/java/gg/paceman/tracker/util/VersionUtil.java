package gg.paceman.tracker.util;

public final class VersionUtil {
    private VersionUtil() {
    }

    public static int tryCompare(String versionA, String versionB, int onFailure) {
        try {
            return Version.of(versionA).compareTo(Version.of(versionB));
        } catch (Exception e) {
            return onFailure;
        }
    }

    public static int compare(String versionA, String versionB) {
        return Version.of(versionA).compareTo(Version.of(versionB));
    }

    /*
     * Version class copied from Julti to allow standalone to use.
     */
    public static class Version implements Comparable<Version> {

        private final String version;

        private Version(String version) {
            if (version == null) {
                throw new IllegalArgumentException("Version can not be null");
            }
            if (!version.matches("[0-9]+(\\.[0-9]+)*")) {
                throw new IllegalArgumentException("Invalid version format");
            }
            this.version = version;
        }

        public static Version of(String versionString) {
            return new Version(versionString);
        }

        public final String get() {
            return this.version;
        }

        @Override
        public int compareTo(Version that) {
            if (that == null) {
                return 1;
            }
            String[] thisParts = this.get().split("\\.");
            String[] thatParts = that.get().split("\\.");
            int length = Math.max(thisParts.length, thatParts.length);
            for (int i = 0; i < length; i++) {
                int thisPart = i < thisParts.length ?
                        Integer.parseInt(thisParts[i]) : 0;
                int thatPart = i < thatParts.length ?
                        Integer.parseInt(thatParts[i]) : 0;
                if (thisPart < thatPart) {
                    return -1;
                }
                if (thisPart > thatPart) {
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null) {
                return false;
            }
            if (this.getClass() != that.getClass()) {
                return false;
            }
            return this.compareTo((Version) that) == 0;
        }

        @Override
        public String toString() {
            return this.version;
        }
    }
}