package util.type;

import util.module.ByteUtil;
import util.parser.UnsignedShort;

public class UINT16_T {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    public int value;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public UINT16_T() {}

    public UINT16_T(int value) {
        this.value = value;
    }

    public UINT16_T(short value, boolean isBigEndian) {
        byte[] shortBytes = ByteUtil.shortToBytes(value, isBigEndian);
        this.value = UnsignedShort.parse(shortBytes);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public boolean equal(UINT16_T datum) {
        if (datum == null) { return false; }
        return value == datum.value;
    }
    ////////////////////////////////////////////////////////////

}
