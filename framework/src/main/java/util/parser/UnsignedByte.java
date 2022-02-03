package util.parser;

public class UnsignedByte {

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public static short parse( byte datum ) {
        return (short)(datum & 0xff);
    }

    public static short parse( byte[] data, int offset ) {
        return (short)(data[offset] & 0xff);
    }

    public static short parse( byte[] data ) {
        return parse( data, 0 );
    }

    public static byte[] parse( short number ) {
        return new byte[] { (byte)(number & 0xff) };
    }
    ////////////////////////////////////////////////////////////

}
