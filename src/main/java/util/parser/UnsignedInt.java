package util.parser;

public class UnsignedInt {

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public static long parse( byte[] data ) {
        return parse( data, 0 );
    }

    public static long parse( byte[] data, int offset ) {
        return (((long) data[offset] & 0xffL) << 24)
                | (((long) data[offset+1] & 0xffL) << 16)
                | (((long) data[offset+2] & 0xffL) << 8)
                |  ((long) data[offset+3] & 0xffL);
    }

    public static byte[] parse( long number ) {
        byte[] data = new byte[4];

        data[0] = (byte) ((number >> 24) & 0xff);
        data[1] = (byte) ((number >> 16) & 0xff);
        data[2] = (byte) ((number >> 8) & 0xff);
        data[3] = (byte) (number & 0xff);

        return data;
    }
    ////////////////////////////////////////////////////////////

}
