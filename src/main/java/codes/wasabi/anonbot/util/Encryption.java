package codes.wasabi.anonbot.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

public class Encryption {

    private static SecureRandom secureRandom = new SecureRandom();

    private static KeyGenerator keyGenerator = null;

    @Contract(" -> new")
    public static @NotNull SecretKey generateKey() {
        if (keyGenerator == null) {
            try {
                keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(256);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return keyGenerator.generateKey();
    }

    private static SecretKeyFactory keyFactory = null;

    @Contract("_ -> new")
    public static @NotNull SecretKey generateKey(String seed) {
        if (keyFactory == null) {
            try {
                keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        KeySpec spec = new PBEKeySpec(seed.toCharArray(), new byte[]{ 0 }, 65536, 256);
        try {
            return new SecretKeySpec(keyFactory.generateSecret(spec).getEncoded(), "AES");
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @NotNull Cipher createCipher() {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return cipher;
    }

    public static @NotNull String encrypt(long input, @NotNull SecretKey key) throws GeneralSecurityException {
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        //
        Cipher cipher = createCipher();
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        //
        byte[] digest = cipher.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(input).array());
        String a = new String(Base64.getEncoder().encode(digest), StandardCharsets.UTF_8);
        String b = new String(Base64.getEncoder().encode(iv), StandardCharsets.UTF_8);
        //
        return a + "@" + b;
    }

    public static long decrypt(@NotNull String input, @NotNull SecretKey key) throws GeneralSecurityException, IllegalArgumentException {
        String[] parts = input.split("@");
        if (parts.length != 2) throw new IllegalArgumentException("Malformed input");
        String a = parts[0];
        String b = parts[1];
        //
        byte[] ab = Base64.getDecoder().decode(a.getBytes(StandardCharsets.UTF_8));
        byte[] bb = Base64.getDecoder().decode(b.getBytes(StandardCharsets.UTF_8));
        //
        IvParameterSpec ivSpec = new IvParameterSpec(bb);
        //
        Cipher cipher = createCipher();
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        //
        byte[] output = cipher.doFinal(ab);
        if (output.length < Long.BYTES) throw new IllegalArgumentException("Output is not large enough");
        return ByteBuffer.wrap(output).getLong();
    }

}
