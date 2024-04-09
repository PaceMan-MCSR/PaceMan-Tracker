package gg.paceman.tracker.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LockUtil {
    private LockUtil() {
    }

    private static RandomAccessFile toRAF(Path path) throws FileNotFoundException {
        return new RandomAccessFile(path.toAbsolutePath().toFile(), "rw");
    }

    public static boolean isLocked(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try {
            RandomAccessFile file = LockUtil.toRAF(path);
            FileLock fileLock = file.getChannel().tryLock();
            file.write('a');
            fileLock.release();
            file.close();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public static LockStuff lock(Path path) {
        try {
            RandomAccessFile file = LockUtil.toRAF(path);
            FileLock fileLock = file.getChannel().lock();
            file.write('a');
            return new LockStuff(fileLock, file);
        } catch (Exception e) {
            return null;
        }
    }

    public static void releaseLock(LockStuff stuff) {
        try {
            stuff.fileLock.release();
            stuff.file.close();
        } catch (IOException ignored) {
        }
    }

    public static class LockStuff {
        public final FileLock fileLock;
        public final RandomAccessFile file;

        public LockStuff(FileLock fileLock, RandomAccessFile file) {
            this.fileLock = fileLock;
            this.file = file;
        }
    }
}
