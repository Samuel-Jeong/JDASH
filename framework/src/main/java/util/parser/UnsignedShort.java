package util.parser;

public class UnsignedShort {

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public static int parse( byte[] data ) {
        return parse( data, 0 );
    }

    public static int parse( byte[] data, int offset ) {
        return ((data[offset] & 0xff) << 8) | (data[offset+1] & 0xff);
    }

    public static byte[] parse( int number ) {
        byte[] data = new byte[2];

        data[0] = (byte)((number >> 8) & 0xff);
        data[1] = (byte)(number & 0xff);

        return data;
    }
    ////////////////////////////////////////////////////////////

}
