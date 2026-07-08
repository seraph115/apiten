package cn.apiten.common.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CryptoSignaturesTest {

    @Test
    void sha256Hex_knownVector() {
        // echo -n "abc" | sha256sum
        assertThat(CryptoSignatures.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_nullEqualsEmpty() {
        assertThat(CryptoSignatures.sha256Hex(null)).isEqualTo(CryptoSignatures.sha256Hex(""));
    }

    @Test
    void hmacSha256Hex_knownVector() {
        // HMAC-SHA256(key="key", msg="The quick brown fox jumps over the lazy dog")
        assertThat(CryptoSignatures.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void buildSignPayload_isNewlineJoined() {
        assertThat(CryptoSignatures.buildSignPayload("AK1", "1700000000000", "n1", "dig"))
                .isEqualTo("AK1\n1700000000000\nn1\ndig");
    }
}
