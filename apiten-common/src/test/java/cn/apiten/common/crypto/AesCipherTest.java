package cn.apiten.common.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AesCipherTest {

    @Test
    void roundTrip() {
        String key = "apiten-test-key";
        String plain = "sk_9f8e7d6c5b4a3210";
        String cipher = AesCipher.encrypt(plain, key);
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(AesCipher.decrypt(cipher, key)).isEqualTo(plain);
    }

    @Test
    void randomIv_differentCiphertextsSamePlaintext() {
        String key = "k";
        assertThat(AesCipher.encrypt("same", key)).isNotEqualTo(AesCipher.encrypt("same", key));
    }

    @Test
    void wrongKey_fails() {
        String cipher = AesCipher.encrypt("secret", "key-A");
        assertThatThrownBy(() -> AesCipher.decrypt(cipher, "key-B")).isInstanceOf(RuntimeException.class);
    }
}
