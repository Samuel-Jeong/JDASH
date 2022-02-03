package util.module;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * @class public class NonceGenerator
 * @brief NonceGenerator Class
 */
public class NonceGenerator {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private static final SecureRandom secureRandom;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    private NonceGenerator() {
        // Nothing
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    static {
        try {
            secureRandom = SecureRandom.getInstance("SHA1PRNG", "SUN");

            final byte[] ar = new byte[64];
            secureRandom.nextBytes(ar);

            Arrays.fill(ar, (byte) 0);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String createRandomNonce() {
        final byte[] ar = new byte[48];

        secureRandom.nextBytes(ar);

        final String nonce = new String(
                java.util.Base64.getUrlEncoder().withoutPadding().encode(ar),
                StandardCharsets.UTF_8
        );

        Arrays.fill(ar, (byte) 0);
        return nonce;
    }
    ////////////////////////////////////////////////////////////////////////////////

}
