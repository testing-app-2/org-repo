import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class StrongAesGcm {

    public byte[] encrypt(byte[] key32Bytes, byte[] data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(key32Bytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }
}