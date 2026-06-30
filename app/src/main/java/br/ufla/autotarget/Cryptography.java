package br.ufla.autotarget;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utilitário de Criptografia AES (AV3).
 * Utiliza o algoritmo AES/CBC/PKCS5Padding com uma chave derivada de forma segura.
 */
public class Cryptography {
    private static final String TAG = "Cryptography";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    
    // Chave e IV fixos para simplicidade nesta etapa (Em produção usar Keystore)
    private static final String SECRET_SEED = "AutoTarget_Security_2024_UFLA";
    private static final byte[] IV = new byte[16]; // IV de 16 bytes (zeros por simplicidade)

    private static SecretKeySpec secretKey;

    static {
        prepareKey();
        Arrays.fill(IV, (byte) 0); // Preenche IV com zeros
    }

    /**
     * Prepara a chave AES de 256 bits a partir de uma seed estável.
     */
    private static void prepareKey() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = SECRET_SEED.getBytes(StandardCharsets.UTF_8);
            key = sha.digest(key);
            secretKey = new SecretKeySpec(key, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Erro ao preparar chave criptográfica", e);
        }
    }

    /**
     * Encripta um texto puro usando AES.
     * @param plainText Texto em formato JSON ou simples.
     * @return String encriptada em Base64.
     */
    public static String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao encriptar dados", e);
            return null;
        }
    }

    /**
     * Decifra um texto encriptado em Base64.
     * @param encryptedText String em Base64.
     * @return Texto original ou null em caso de falha.
     */
    public static String decrypt(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] decodedBytes = Base64.decode(encryptedText, Base64.NO_WRAP);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao decifrar dados", e);
            return null;
        }
    }

    /**
     * Realiza um teste interno de integridade (Self-Test).
     * @return true se a cifra/decifra for consistente.
     */
    public static boolean selfTest() {
        String testJson = "{\"player\":\"Teste\", \"score\": 100}";
        String encrypted = encrypt(testJson);
        if (encrypted == null) return false;
        
        String decrypted = decrypt(encrypted);
        boolean result = testJson.equals(decrypted);
        
        if (result) {
            Log.i(TAG, "Criptografia: Teste de integridade OK.");
        } else {
            Log.e(TAG, "Criptografia: Falha no teste de integridade!");
        }
        return result;
    }
}
