package util.type;

import util.module.ByteUtil;
import util.parser.UnsignedInt;

public class UINT32_T {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    public long value;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public UINT32_T() {}

    public UINT32_T(long value) {
        this.value = value;
    }

    public UINT32_T(int value, boolean isBigEndian) {
        byte[] intBytes = ByteUtil.intToBytes(value, isBigEndian);
        this.value = UnsignedInt.parse(intBytes);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public boolean equal(UINT32_T datum) {
        if (datum == null) { return false; }
        return value == datum.value;
    }
    ////////////////////////////////////////////////////////////

}
