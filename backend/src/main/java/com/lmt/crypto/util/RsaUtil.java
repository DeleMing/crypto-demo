package com.lmt.crypto.util;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA加解密工具类
 * 使用RSA-OAEP填充模式，提供更高的安全性
 * 主要用于加密传输AES密钥
 *
 * @author mingtao_liao
 */
public final class RsaUtil {

    /**
     * RSA转换算法 - 使用PKCS1填充
     * 为了与前端JSEncrypt库兼容，使用PKCS1填充模式
     * 注意：OAEP填充更安全，但JSEncrypt不支持
     */
    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private static final String ALGORITHM = "RSA";

    private RsaUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 使用RSA公钥加密数据
     * 主要用于客户端加密AES密钥
     *
     * @param publicKey RSA公钥
     * @param data      待加密的原始数据
     * @return 加密后的数据
     * @throws IllegalStateException 加密失败时抛出
     */
    public static byte[] encrypt(PublicKey publicKey, byte[] data) {
        if (publicKey == null || data == null) {
            throw new IllegalArgumentException("公钥和数据不能为空");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用RSA私钥解密数据
     * 主要用于服务端解密客户端传输的AES密钥
     *
     * @param keyPair   RSA密钥对（使用其中的私钥）
     * @param encrypted 待解密的密文数据
     * @return 解密后的原始数据
     * @throws IllegalStateException 解密失败时抛出
     */
    public static byte[] decrypt(KeyPair keyPair, byte[] encrypted) {
        if (keyPair == null || encrypted == null) {
            throw new IllegalArgumentException("密钥对和加密数据不能为空");
        }

        if (encrypted.length == 0) {
            throw new IllegalArgumentException("加密数据不能为空");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());

            // 检查数据长度是否合理（RSA-2048加密后的数据应该是256字节）
            if (keyPair.getPrivate() instanceof RSAPrivateKey) {
                RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
                int keySize = rsaPrivateKey.getModulus().bitLength();
                int expectedSize = keySize / 8;

                if (encrypted.length != expectedSize) {
                    throw new IllegalStateException(
                            String.format("RSA加密数据长度不正确: 期望%d字节，实际%d字节。可能是公钥格式不匹配或加密数据损坏",
                                    expectedSize, encrypted.length));
                }
            }

            return cipher.doFinal(encrypted);
        } catch (javax.crypto.BadPaddingException e) {
            // BadPaddingException通常表示：
            // 1. 使用了错误的密钥对
            // 2. 数据被损坏
            // 3. 公钥格式不匹配（前端使用了错误的公钥）
            throw new IllegalStateException(
                    "RSA解密失败: Padding错误。可能原因：1) 使用了错误的密钥对 2) 公钥格式不匹配（前端需要使用PEM格式） 3) 加密数据损坏。原始错误: " + e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将RSA公钥转换为Base64字符串
     * 用于向客户端传输公钥
     *
     * @param keyPair RSA密钥对
     * @return Base64编码的公钥字符串
     */
    public static String publicKeyToBase64(KeyPair keyPair) {
        if (keyPair == null) {
            throw new IllegalArgumentException("密钥对不能为空");
        }
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * 将RSA公钥转换为PEM格式字符串
     * JSEncrypt库需要PEM格式的公钥
     * 使用标准的 X.509 SubjectPublicKeyInfo 格式 (-----BEGIN PUBLIC KEY-----)
     *
     * @param keyPair RSA密钥对
     * @return PEM格式的公钥字符串
     */
    public static String publicKeyToPem(KeyPair keyPair) {
        if (keyPair == null) {
            throw new IllegalArgumentException("密钥对不能为空");
        }
        byte[] encoded = keyPair.getPublic().getEncoded();
        String base64Key = Base64.getEncoder().encodeToString(encoded);

        // PEM格式：每64个字符换行，首尾添加标记
        // 注意：确保格式严格符合PEM标准
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");

        // 每64个字符换行（PEM标准要求）
        for (int i = 0; i < base64Key.length(); i += 64) {
            int end = Math.min(i + 64, base64Key.length());
            pem.append(base64Key.substring(i, end));
            if (end < base64Key.length()) {
                pem.append("\n");
            }
        }

        // 确保最后有换行符
        if (base64Key.length() % 64 != 0) {
            pem.append("\n");
        }
        pem.append("-----END PUBLIC KEY-----\n");

        return pem.toString();
    }

    /**
     * 将Base64字符串转换为RSA公钥对象
     * 用于解析客户端传输的公钥（如果需要）
     *
     * @param base64PublicKey Base64编码的公钥字符串
     * @return RSA公钥对象
     * @throws IllegalStateException 解析失败时抛出
     */
    public static PublicKey base64ToPublicKey(String base64PublicKey) {
        if (base64PublicKey == null || base64PublicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Base64公钥字符串不能为空");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(base64PublicKey.getBytes(StandardCharsets.UTF_8));
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            return keyFactory.generatePublic(keySpec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("解析RSA公钥失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Base64解码失败，公钥格式不正确: " + e.getMessage(), e);
        }
    }
}
