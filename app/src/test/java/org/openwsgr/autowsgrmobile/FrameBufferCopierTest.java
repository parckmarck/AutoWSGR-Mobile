package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class FrameBufferCopierTest {
    @Test public void copiesTightlyPackedTwoByTwoRgba() {
        byte[] source = bytes(
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16
        );

        byte[] copied = FrameBufferCopier.copyRgba(
                ByteBuffer.wrap(source), 2, 2, 4, 8
        );

        assertArrayEquals(source, copied);
    }

    @Test public void skipsPaddedBytesAtTheEndOfEachRow() {
        ByteBuffer source = ByteBuffer.wrap(bytes(
                1, 2, 3, 4, 5, 6, 7, 8, 90, 91, 92, 93,
                9, 10, 11, 12, 13, 14, 15, 16
        ));

        assertArrayEquals(
                bytes(
                        1, 2, 3, 4, 5, 6, 7, 8,
                        9, 10, 11, 12, 13, 14, 15, 16
                ),
                FrameBufferCopier.copyRgba(source, 2, 2, 4, 12)
        );
    }

    @Test public void skipsExtraBytesInsidePixelsWhenPixelStrideExceedsFour() {
        ByteBuffer source = ByteBuffer.wrap(bytes(
                1, 2, 3, 4, 80, 81, 5, 6, 7, 8, 82, 83, 90, 91,
                9, 10, 11, 12, 84, 85, 13, 14, 15, 16
        ));

        assertArrayEquals(
                bytes(
                        1, 2, 3, 4, 5, 6, 7, 8,
                        9, 10, 11, 12, 13, 14, 15, 16
                ),
                FrameBufferCopier.copyRgba(source, 2, 2, 6, 14)
        );
    }

    @Test public void leavesOriginalPositionAndLimitUnchanged() {
        ByteBuffer source = ByteBuffer.wrap(bytes(1, 2, 3, 4, 90, 91, 92, 93));
        source.position(2);
        source.limit(7);

        assertArrayEquals(bytes(1, 2, 3, 4),
                FrameBufferCopier.copyRgba(source, 1, 1, 4, 4));
        assertEquals(2, source.position());
        assertEquals(7, source.limit());
    }

    @Test public void rejectsInvalidDimensionsAndStrides() {
        ByteBuffer source = ByteBuffer.allocate(64);

        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(source, 0, 1, 4, 4));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(source, 1, -1, 4, 4));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(source, 1, 1, 3, 4));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(source, 2, 1, 4, 7));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(null, 1, 1, 4, 4));
    }

    @Test public void rejectsUnreadableBoundsAndArithmeticOverflow() {
        ByteBuffer shortSource = ByteBuffer.allocate(11);
        ByteBuffer limitedSource = ByteBuffer.allocate(16);
        limitedSource.limit(11);

        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(shortSource, 2, 2, 4, 8));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(limitedSource, 2, 2, 4, 8));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(
                        ByteBuffer.allocate(8), Integer.MAX_VALUE, 1, 4,
                        Integer.MAX_VALUE
                ));
        assertThrows(IllegalArgumentException.class,
                () -> FrameBufferCopier.copyRgba(
                        ByteBuffer.allocate(8), 1, Integer.MAX_VALUE, 4, 4
                ));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
