package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarLong {
    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public static int getByteSize(final long value) {
        // Gale start - Velocity - pre-compute VarInt and VarLong sizes
        return VARLONG_EXACT_BYTE_LENGTHS[Long.numberOfLeadingZeros(value)];
    }
    private static final int[] VARLONG_EXACT_BYTE_LENGTHS = new int[65];
    static {
        for (int i = 0; i < 64; i++) {
            VARLONG_EXACT_BYTE_LENGTHS[i] = (int) Math.ceil((63d - (i - 1)) / 7d);
        }
        VARLONG_EXACT_BYTE_LENGTHS[64] = 1; // Special case for the number 0
    }
    static int getByteSizeOld(final long value) { // public -> package-private
        // Gale end - Velocity - pre-compute VarInt and VarLong sizes
        for (int i = 1; i < MAX_VARLONG_SIZE; i++) {
            if ((value & -1L << i * DATA_BITS_PER_BYTE) == 0L) {
                return i;
            }
        }

        return MAX_VARLONG_SIZE;
    }

    public static boolean hasContinuationBit(final byte in) {
        return (in & CONTINUATION_BIT_MASK) == CONTINUATION_BIT_MASK;
    }

    public static long read(final ByteBuf input) {
        long out = 0L;
        int bytes = 0;

        byte in;
        do {
            in = input.readByte();
            out |= (long)(in & (byte)DATA_BITS_MASK) << bytes++ * DATA_BITS_PER_BYTE;
            if (bytes > MAX_VARLONG_SIZE) {
                throw new RuntimeException("VarLong too big");
            }
        } while (hasContinuationBit(in));

        return out;
    }

    public static ByteBuf write(final ByteBuf output, long value) {
        // Gale start - Velocity - optimized VarLong#write
        if ((value & 0xFFFFFFFFFFFFFF80L) == 0) {
            output.writeByte((int) value);
        } else if (value < 0) {
            // The case of writing arbitrary longs is common
            // Here, the number is negative, which has probability 1/2 for arbitrary numbers
            int least7bits = (int) (value & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            long nonLeast7Bits = value >>> 28;
            int secondLeast7bits = (int) (nonLeast7Bits & 0xFFFFFFFL);
            int w2 = (secondLeast7bits & 0x7F) << 24
                | ((secondLeast7bits & 0x3F80) << 9)
                | (secondLeast7bits & 0x1FC000) >> 6
                | (secondLeast7bits >>> 21)
                | 0x80808080;
            int thirdLeast7Bits = (int) (nonLeast7Bits >>> 28);
            int w3 = (thirdLeast7Bits & 0x7F) << 8
                | (thirdLeast7Bits >>> 7)
                | 0x00008000;
            output.writeInt(w);
            output.writeInt(w2);
            output.writeShort(w3);
        } else if ((value & 0xFFFFFFFFFFFFC000L) == 0) {
            int least7bits = (int) value;
            int w = (least7bits & 0x7F) << 8
                | (least7bits >>> 7)
                | 0x00008000;
            output.writeShort(w);
        } else if ((value & 0xFFFFFFFFFFE00000L) == 0) {
            int least7bits = (int) value;
            int w = (least7bits & 0x7F) << 16
                | (least7bits & 0x3F80) << 1
                | (least7bits >>> 14)
                | 0x00808000;
            output.writeMedium(w);
        } else if ((value & 0xFFFFFFFFF0000000L) == 0) {
            int least7bits = (int) value;
            int w = (least7bits & 0x7F) << 24
                | ((least7bits & 0x3F80) << 9)
                | (least7bits & 0x1FC000) >> 6
                | (least7bits >>> 21)
                | 0x80808000;
            output.writeInt(w);
        } else if ((value & 0xFFFFFFF800000000L) == 0) {
            int least7bits = (int) (value & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            output.writeInt(w);
            output.writeByte((int) (value >>> 28));
        } else if ((value & 0xFFFFFC0000000000L) == 0) {
            int least7bits = (int) (value & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            int secondLeast7bits = (int) (value >>> 28);
            int w2 = (secondLeast7bits & 0x7F) << 8
                | (secondLeast7bits >>> 7)
                | 0x00008000;
            output.writeInt(w);
            output.writeShort(w2);
        } else if ((value & 0xFFFE000000000000L) == 0) {
            int least7bits = (int) (value & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            int secondLeast7bits = (int) (value >>> 28);
            int w2 = (secondLeast7bits & 0x7F) << 16
                | (secondLeast7bits & 0x3F80) << 1
                | (secondLeast7bits >>> 14)
                | 0x00808000;
            output.writeInt(w);
            output.writeMedium(w2);
        } else if ((value & 0xFF00000000000000L) == 0) {
            int least7bits = (int) (value & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            int secondLeast7bits = (int) (value >>> 28);
            int w2 = (secondLeast7bits & 0x7F) << 24
                | ((secondLeast7bits & 0x3F80) << 9)
                | (secondLeast7bits & 0x1FC000) >> 6
                | (secondLeast7bits >>> 21)
                | 0x80808000;
            output.writeInt(w);
            output.writeInt(w2);
        } else {
            int least7bits = (int) (value & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            long nonLeast7Bits = value >>> 28;
            int secondLeast7bits = (int) (nonLeast7Bits & 0xFFFFFFFL);
            int w2 = (secondLeast7bits & 0x7F) << 24
                | ((secondLeast7bits & 0x3F80) << 9)
                | (secondLeast7bits & 0x1FC000) >> 6
                | (secondLeast7bits >>> 21)
                | 0x80808080;
            output.writeInt(w);
            output.writeInt(w2);
            output.writeByte((int) (nonLeast7Bits >>> 28));
        }
        return output;
    }
    static ByteBuf writeOld(ByteBuf output, long value) { // public -> package-private
        // Gale end - Velocity - optimized VarLong#write
        while ((value & -128L) != 0L) {
            output.writeByte((int)(value & DATA_BITS_MASK) | CONTINUATION_BIT_MASK);
            value >>>= DATA_BITS_PER_BYTE;
        }

        output.writeByte((int)value);
        return output;
    }
}
