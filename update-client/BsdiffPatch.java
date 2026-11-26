package uk.playdrop.cherrytree_idletextrpg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Applies a BSDIFF40 patch and reconstructs the target file byte-for-byte. */
final class BsdiffPatch {
    private static final long MAX_OUTPUT_BYTES = 180L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 32768;

    private BsdiffPatch() { }

    static void apply(File source, File patch, File output) throws IOException {
        long sourceLength = source.length();
        long patchLength = patch.length();
        if (!source.isFile() || sourceLength <= 0L || patchLength < 32L) {
            throw new IOException("Patch input is incomplete.");
        }

        boolean complete = false;
        try (RandomAccessFile patchFile = new RandomAccessFile(patch, "r")) {
            byte[] header = new byte[32];
            patchFile.readFully(header);
            if (header[0] != 'B' || header[1] != 'S' || header[2] != 'D'
                    || header[3] != 'I' || header[4] != 'F' || header[5] != 'F'
                    || header[6] != '4' || header[7] != '0') {
                throw new IOException("Unsupported patch format.");
            }
            long controlLength = decodeOffset(header, 8);
            long diffLength = decodeOffset(header, 16);
            long newLength = decodeOffset(header, 24);
            if (controlLength < 0L || diffLength < 0L || newLength <= 0L
                    || newLength > MAX_OUTPUT_BYTES
                    || controlLength > patchLength - 32L
                    || diffLength > patchLength - 32L - controlLength) {
                throw new IOException("Patch header is invalid.");
            }

            long diffStart = 32L + controlLength;
            long extraStart = diffStart + diffLength;
            try (InputStream controlBytes = range(patch, 32L, controlLength);
                    InputStream diffBytes = range(patch, diffStart, diffLength);
                    InputStream extraBytes = range(patch, extraStart, patchLength - extraStart);
                    BZip2CompressorInputStream control = new BZip2CompressorInputStream(controlBytes);
                    BZip2CompressorInputStream diff = new BZip2CompressorInputStream(diffBytes);
                    BZip2CompressorInputStream extra = new BZip2CompressorInputStream(extraBytes);
                    RandomAccessFile oldFile = new RandomAccessFile(source, "r");
                    FileOutputStream newFile = new FileOutputStream(output, false)) {
                applyStreams(oldFile, sourceLength, control, diff, extra, newFile, newLength);
                newFile.getFD().sync();
            }
            complete = output.isFile() && output.length() == newLength;
            if (!complete) throw new IOException("Patched file size is incorrect.");
        } finally {
            if (!complete) output.delete();
        }
    }

    private static void applyStreams(RandomAccessFile oldFile, long oldLength,
            InputStream control, InputStream diff, InputStream extra,
            OutputStream output, long newLength) throws IOException {
        byte[] oldBuffer = new byte[BUFFER_SIZE];
        byte[] diffBuffer = new byte[BUFFER_SIZE];
        byte[] outBuffer = new byte[BUFFER_SIZE];
        long oldPosition = 0L;
        long newPosition = 0L;

        while (newPosition < newLength) {
            long addLength = readOffset(control);
            long copyLength = readOffset(control);
            long seekLength = readOffset(control);
            if (addLength < 0L || copyLength < 0L
                    || addLength > newLength - newPosition
                    || copyLength > newLength - newPosition - addLength) {
                throw new IOException("Patch control data is invalid.");
            }

            long added = 0L;
            while (added < addLength) {
                int count = (int) Math.min((long) BUFFER_SIZE, addLength - added);
                readFully(diff, diffBuffer, count);
                Arrays.fill(oldBuffer, 0, count, (byte) 0);
                long oldStart = safeAdd(oldPosition, added);
                long validStart = Math.max(0L, oldStart);
                long validEnd = Math.min(oldLength, safeAdd(oldStart, count));
                if (validStart < validEnd) {
                    int destinationOffset = (int) (validStart - oldStart);
                    int validCount = (int) (validEnd - validStart);
                    oldFile.seek(validStart);
                    oldFile.readFully(oldBuffer, destinationOffset, validCount);
                }
                for (int i = 0; i < count; i++) {
                    outBuffer[i] = (byte) ((oldBuffer[i] & 0xff) + (diffBuffer[i] & 0xff));
                }
                output.write(outBuffer, 0, count);
                added += count;
            }
            oldPosition = safeAdd(oldPosition, addLength);
            newPosition += addLength;

            copyExactly(extra, output, copyLength);
            oldPosition = safeAdd(oldPosition, seekLength);
            newPosition += copyLength;
        }
    }

    private static InputStream range(File file, long start, long length) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            input.getChannel().position(start);
            return new LimitedInputStream(input, length);
        } catch (IOException error) {
            input.close();
            throw error;
        }
    }

    private static long readOffset(InputStream input) throws IOException {
        byte[] bytes = new byte[8];
        readFully(input, bytes, bytes.length);
        return decodeOffset(bytes, 0);
    }

    private static long decodeOffset(byte[] bytes, int offset) {
        long value = bytes[offset + 7] & 0x7f;
        for (int i = 6; i >= 0; i--) value = (value * 256L) + (bytes[offset + i] & 0xffL);
        return (bytes[offset + 7] & 0x80) == 0 ? value : -value;
    }

    private static long safeAdd(long left, long right) throws IOException {
        if (right > 0L && left > Long.MAX_VALUE - right
                || right < 0L && left < Long.MIN_VALUE - right) {
            throw new IOException("Patch offset overflowed.");
        }
        return left + right;
    }

    private static void readFully(InputStream input, byte[] buffer, int length) throws IOException {
        int offset = 0;
        while (offset < length) {
            int count = input.read(buffer, offset, length - offset);
            if (count < 0) throw new IOException("Patch data ended unexpectedly.");
            offset += count;
        }
    }

    private static void copyExactly(InputStream input, OutputStream output, long length)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0L;
        while (copied < length) {
            int count = (int) Math.min((long) buffer.length, length - copied);
            readFully(input, buffer, count);
            output.write(buffer, 0, count);
            copied += count;
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream input;
        private long remaining;

        LimitedInputStream(InputStream input, long length) {
            this.input = input;
            this.remaining = length;
        }

        @Override public int read() throws IOException {
            if (remaining <= 0L) return -1;
            int value = input.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0L) return -1;
            int count = input.read(buffer, offset, (int) Math.min((long) length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }

        @Override public void close() throws IOException { input.close(); }
    }
}
