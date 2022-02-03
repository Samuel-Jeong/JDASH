package util.module;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteUtil {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    /** The maximum number of bytes in a UDP packet. */
    public static final int MAX_UDP_PACKET_SIZE = 65537;

    /** Number of bytes in a Java short. */
    public static final int NUM_BYTES_IN_SHORT = 2;

    /** Number of bytes in a Java int. */
    public static final int NUM_BYTES_IN_INT = 4;

    /** Number of bytes in a Java long. */
    public static final int NUM_BYTES_IN_LONG = 8;

    private static final long[] maxValueCache = new long[64];

    static {
        for (int i = 1; i < 64; i++) {
            maxValueCache[i] = ((long) 1 << i) - 1;
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public static byte[] shortToBytes(short s, boolean isBigEndian) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(NUM_BYTES_IN_SHORT);

        if (isBigEndian) {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        byteBuffer.putShort(s);
        return byteBuffer.array();
    }

    public static short bytesToShort(byte[] bytes, boolean isBigEndian) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        if (isBigEndian) {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        return byteBuffer.getShort();
    }

    public static String shortToHex(short s) {
        return Integer.toHexString(s);
    }

    public static short hexToShort(String s) {
        return Short.parseShort(s, 16);
    }

    public static byte[] intToBytes(int i, boolean isBigEndian) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(NUM_BYTES_IN_INT);

        if (isBigEndian) {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        byteBuffer.putInt(i);
        return byteBuffer.array();
    }

    public static int bytesToInt(byte[] bytes, boolean isBigEndian) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        if (isBigEndian) {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        return byteBuffer.getInt();
    }

    public static String intToHex(int i) {
        return Integer.toHexString(i);
    }

    public static int hexToInt(String s) {
        return Integer.parseInt(s, 16);
    }

    public static byte[] longToBytes(long l, boolean isBigEndian) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(NUM_BYTES_IN_LONG);

        if (isBigEndian) {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        byteBuffer.putLong(l);
        return byteBuffer.array();
    }

    public static long bytesToLong(byte[] bytes, boolean isBigEndian) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        if (isBigEndian) {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        return byteBuffer.getLong();
    }

    public static String longToHex(long l) {
        return Long.toHexString(l);
    }

    public static long hexToLong(String s) {
        return Long.parseLong(s, 16);
    }

    public static String writeBytes(byte[] bytes) {
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            // New line every 4 bytes
            if (i % 4 == 0) {
                stringBuffer.append("\n");
            }
            stringBuffer.append(writeBits(bytes[i])).append(" ");
        }
        return stringBuffer.toString();

    }

    public static String writeBytes(byte[] bytes, int packetLength) {
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0; i < packetLength; i++) {
            // New line every 4 bytes
            if (i % 4 == 0) {
                stringBuffer.append("\n");
            }
            stringBuffer.append(writeBits(bytes[i])).append(" ");
        }
        return stringBuffer.toString();
    }

    public static String writeBits(byte b) {
        StringBuilder stringBuffer = new StringBuilder();
        int bit;
        for (int i = 7; i >= 0; i--) {
            bit = (b >>> i) & 0x01;
            stringBuffer.append(bit);
        }
        return stringBuffer.toString();
    }

    public static int getMaxIntValueForNumBits(int i) {
        if (i >= 32) {
            throw new RuntimeException("Number of bits exceeds Java int.");
        } else {
            return (int) maxValueCache[i];
        }
    }

    public static long getMaxLongValueForNumBits(int i) {
        if (i >= 64) {
            throw new RuntimeException("Number of bits exceeds Java long.");
        } else {
            return maxValueCache[i];
        }
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for(final byte b: a) {
            sb.append(String.format("%02x ", b & 0xff));
        }
        return sb.toString();
    }
    ////////////////////////////////////////////////////////////

}
