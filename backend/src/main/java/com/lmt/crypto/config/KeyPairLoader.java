package com.lmt.crypto.config;

import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 读取公钥私钥
 *
 * @author: 廖鸣韬
 * @version: 1.0.0
 * @company: 合合信息
 * @date: 2025/12/4 11:49
 * @since:
 */
@Getter
@Component
public class KeyPairLoader {
    /**
     * 密钥对
     */
    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            PublicKey publicKey = loadPublicKey("crypto/rsa_public.pem");
            PrivateKey privateKey = loadPrivateKey("crypto/rsa_private_pkcs8.pem");

            this.keyPair = new KeyPair(publicKey, privateKey);
            System.out.println("[KeyPairLoader] ===== RSA 密钥加载成功 =====");

        } catch (Exception e) {
            throw new RuntimeException("加载RSA密钥失败！", e);
        }
    }

    /**
     * 让 Spring 能注入 KeyPair
     */
    @Bean
    public KeyPair rsaKeyPair() {
        return this.keyPair;
    }

    /**
     * 加载公钥
     *
     * @param path 文件路径
     * @return 公钥
     * @throws Exception 加载异常
     */
    private PublicKey loadPublicKey(String path) throws Exception {
        String pem = loadPem(path)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }

    /**
     * 加载私钥
     *
     * @param path 文件路径
     * @return 私钥
     * @throws Exception 加载异常
     */
    private PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = loadPem(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    /**
     * 加载PEM文件
     *
     * @param path 文件路径
     * @return PEM内容
     * @throws Exception 加载异常
     */
    private String loadPem(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
