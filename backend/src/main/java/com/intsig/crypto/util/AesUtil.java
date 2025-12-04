package com.intsig.crypto.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM加解密工具类
 * 使用AES-256-GCM模式，提供认证加密（AEAD）
 * GCM模式自带完整性校验，无需额外的HMAC
 *
 * @author mingtao_liao
 */
public final class AesUtil {

    /**
     * AES-GCM转换算法
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * 密钥算法
     */
    private static final String ALGORITHM = "AES";

    /**
     * GCM认证标签长度（位）
     * 128位=16字节，提供足够的安全强度
     */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * GCM认证标签长度（字节）
     */
    private static final int GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8;

    /**
     * GCM模式推荐的IV长度（字节）
     * 12字节的IV可以直接作为counter，无需额外处理
     */
    private static final int IV_LENGTH_BYTES = 12;

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 生成随机IV（初始化向量）
     * 每次加密必须使用新的IV，切勿重复使用
     *
     * @return 12字节的随机IV
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 将字节数组转换为AES密钥对象
     *
     * @param keyBytes 密钥字节数组（通常为32字节，即256位）
     * @return SecretKey对象
     */
    public static SecretKey bytesToKey(byte[] keyBytes) {
        if (keyBytes == null) {
            throw new IllegalArgumentException("密钥字节数组不能为空");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * AES-GCM加密
     * 返回的结果包含分离的密文和认证标签
     *
     * @param key       AES密钥
     * @param iv        初始化向量（12字节）
     * @param plaintext 明文数据
     * @return 包含密文和标签的结果对象
     * @throws IllegalStateException 加密失败时抛出
     */
    public static GcmEncryptionResult encrypt(SecretKey key, byte[] iv, byte[] plaintext) {
        validateEncryptionParams(key, iv, plaintext);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            // GCM模式下，doFinal返回的是：密文 + 认证标签（tag）
            byte[] ciphertextWithTag = cipher.doFinal(plaintext);

            // 分离密文和标签
            // 密文长度 = 总长度 - 标签长度
            int ciphertextLength = ciphertextWithTag.length - GCM_TAG_LENGTH_BYTES;

            byte[] ciphertext = new byte[ciphertextLength];
            byte[] tag = new byte[GCM_TAG_LENGTH_BYTES];

            // 使用ByteBuffer进行高效的数组分割
            ByteBuffer buffer = ByteBuffer.wrap(ciphertextWithTag);
            buffer.get(ciphertext);
            buffer.get(tag);

            return new GcmEncryptionResult(ciphertext, tag);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * AES-GCM解密
     * 需要提供密文和认证标签
     *
     * @param key        AES密钥
     * @param iv         初始化向量（12字节）
     * @param ciphertext 密文数据
     * @param tag        认证标签（16字节）
     * @return 解密后的明文数据
     * @throws IllegalStateException 解密失败或完整性校验失败时抛出
     */
    public static byte[] decrypt(SecretKey key, byte[] iv, byte[] ciphertext, byte[] tag) {
        validateDecryptionParams(key, iv, ciphertext, tag);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            // 组合密文和标签
            // GCM解密需要：密文 + 标签
            ByteBuffer buffer = ByteBuffer.allocate(ciphertext.length + tag.length);
            buffer.put(ciphertext);
            buffer.put(tag);

            // doFinal会同时解密并验证完整性
            // 如果标签不匹配，会抛出AEADBadTagException
            return cipher.doFinal(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM解密失败或完整性校验失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将字节数组转换为Base64字符串
     *
     * @param data 字节数组
     * @return Base64字符串
     */
    public static String toBase64(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("数据不能为空");
        }
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * 将Base64字符串转换为字节数组
     * 兼容标准Base64和URL-safe Base64格式
     * 自动去除空白字符（空格、换行符、制表符等）
     *
     * @param base64 Base64字符串
     * @return 字节数组
     * @throws IllegalArgumentException Base64解码失败时抛出
     */
    public static byte[] fromBase64(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            throw new IllegalArgumentException("Base64字符串不能为空");
        }

        // 清理Base64字符串：去除所有空白字符（包括空格、换行符、制表符等）
        String cleaned = base64.replaceAll("\\s+", "");

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Base64字符串清理后为空");
        }

        try {
            // 首先尝试标准Base64解码
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            // 如果标准Base64失败，尝试URL-safe Base64
            try {
                return Base64.getUrlDecoder().decode(cleaned);
            } catch (IllegalArgumentException e2) {
                // 如果都失败，提供详细的错误信息
                throw new IllegalArgumentException(
                        String.format("Base64解码失败，数据格式不正确。原始长度: %d, 清理后长度: %d, 前50字符: %s, 错误: %s",
                                base64.length(), cleaned.length(),
                                cleaned.length() > 50 ? cleaned.substring(0, 50) + "..." : cleaned,
                                e.getMessage()), e);
            }
        }
    }

    /**
     * 验证加密参数
     */
    private static void validateEncryptionParams(SecretKey key, byte[] iv, byte[] plaintext) {
        if (key == null) {
            throw new IllegalArgumentException("AES密钥不能为空");
        }
        if (iv == null || iv.length != IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("IV长度必须为" + IV_LENGTH_BYTES + "字节");
        }
        if (plaintext == null) {
            throw new IllegalArgumentException("明文数据不能为空");
        }
    }

    /**
     * 验证解密参数
     */
    private static void validateDecryptionParams(SecretKey key, byte[] iv, byte[] ciphertext, byte[] tag) {
        if (key == null) {
            throw new IllegalArgumentException("AES密钥不能为空");
        }
        if (iv == null || iv.length != IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("IV长度必须为" + IV_LENGTH_BYTES + "字节");
        }
        if (ciphertext == null) {
            throw new IllegalArgumentException("密文数据不能为空");
        }
        if (tag == null || tag.length != GCM_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("GCM标签长度必须为" + GCM_TAG_LENGTH_BYTES + "字节");
        }
    }

    /**
     * GCM加密结果
     * 包含分离的密文和认证标签
     */
    @Getter
    @AllArgsConstructor
    public static class GcmEncryptionResult {
        /**
         * 密文数据
         */
        private final byte[] ciphertext;
        /**
         * 认证标签
         */
        private final byte[] tag;
    }
}
