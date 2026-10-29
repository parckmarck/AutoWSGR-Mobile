package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ZipAssetIndexTest {
    @Test public void indexesTheStreamOnceAndClosesIt() throws Exception {
        TrackingInputStream input = new TrackingInputStream(sampleZip());

        ZipAssetIndex index = ZipAssetIndex.load(input);
        int readsAfterLoad = input.readCalls;

        assertArrayEquals(bytes(1, 2, 3), index.read("data/first.bin"));
        assertArrayEquals(bytes(4, 5), index.read("data/second.bin"));
        assertEquals(Arrays.asList("data/first.bin", "data/second.bin"),
                index.listFirst(2));
        assertEquals("reads and listings must use only the in-memory index",
                readsAfterLoad, input.readCalls);
        assertEquals(1, input.closeCalls);
    }

    @Test public void normalizesSlashAndOptionalAutowsgrPrefixConsistently()
            throws Exception {
        ZipAssetIndex index = ZipAssetIndex.load(
                new ByteArrayInputStream(sampleZip())
        );

        assertArrayEquals(bytes(1, 2, 3), index.read("data/first.bin"));
        assertArrayEquals(bytes(1, 2, 3), index.read("autowsgr/data/first.bin"));
        assertArrayEquals(bytes(4, 5), index.read("autowsgr\\data\\second.bin"));
        assertArrayEquals(bytes(6), index.read("data/windows.bin"));
    }

    @Test public void skipsDirectoriesButRetainsEmptyFilesInStableOrder()
            throws Exception {
        ZipAssetIndex index = ZipAssetIndex.load(
                new ByteArrayInputStream(sampleZip())
        );

        assertEquals(Arrays.asList(
                        "data/first.bin",
                        "data/second.bin",
                        "data/empty.bin",
                        "data/windows.bin"
                ),
                index.listFirst(20));
        assertArrayEquals(new byte[0], index.read("data/empty.bin"));
    }

    @Test public void missingPathThrowsIOException() throws Exception {
        ZipAssetIndex index = ZipAssetIndex.load(
                new ByteArrayInputStream(sampleZip())
        );

        assertThrows(IOException.class, () -> index.read("data/missing.bin"));
    }

    @Test public void everyReadReturnsADefensiveCopy() throws Exception {
        ZipAssetIndex index = ZipAssetIndex.load(
                new ByteArrayInputStream(sampleZip())
        );

        byte[] first = index.read("data/first.bin");
        first[0] = 99;

        assertArrayEquals(bytes(1, 2, 3), index.read("data/first.bin"));
    }

    @Test public void listFirstIsDeterministicAndValidatesMaxItems() throws Exception {
        ZipAssetIndex index = ZipAssetIndex.load(
                new ByteArrayInputStream(sampleZip())
        );

        assertEquals(Collections.emptyList(), index.listFirst(0));
        assertEquals(Arrays.asList("data/first.bin", "data/second.bin"),
                index.listFirst(2));
        assertEquals(Arrays.asList("data/first.bin", "data/second.bin"),
                index.listFirst(2));
        assertThrows(IllegalArgumentException.class, () -> index.listFirst(-1));
    }

    private static byte[] sampleZip() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            putDirectory(zip, "data/folder/");
            putDirectory(zip, "data\\windows-folder\\");
            put(zip, "data/first.bin", bytes(1, 2, 3));
            put(zip, "autowsgr/data/first.bin", bytes(99));
            put(zip, "autowsgr/data/second.bin", bytes(4, 5));
            put(zip, "data/empty.bin", new byte[0]);
            put(zip, "data\\windows.bin", bytes(6));
        }
        return output.toByteArray();
    }

    private static void putDirectory(ZipOutputStream zip, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.closeEntry();
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        int readCalls;
        int closeCalls;

        TrackingInputStream(byte[] data) {
            super(data);
        }

        @Override public int read() {
            readCalls++;
            return super.read();
        }

        @Override public int read(byte[] buffer, int offset, int length) {
            readCalls++;
            return super.read(buffer, offset, length);
        }

        @Override public void close() throws IOException {
            closeCalls++;
            super.close();
        }
    }
}
