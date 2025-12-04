//package com.lmt.crypto.config;
//
//import lombok.Data;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.security.KeyPair;
//import java.security.KeyPairGenerator;
//import java.security.NoSuchAlgorithmException;
//
///**
// * 加解密配置类
// * 负责初始化RSA密钥对并提供配置属性
// */
//@Configuration
//@EnableConfigurationProperties(CryptoConfig.CryptoProperties.class)
//public class CryptoConfig {
//
//    /**
//     * 创建RSA密钥对Bean
//     * 应用启动时生成，整个生命周期内保持不变
//     *
//     * @param properties 加密配置属性
//     * @return RSA密钥对
//     */
//    @Bean
//    public KeyPair rsaKeyPair(CryptoProperties properties) {
//        try {
//            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
//            generator.initialize(properties.getRsa().getKeySize());
//            KeyPair keyPair = generator.generateKeyPair();
//            System.out.println("RSA密钥对初始化成功，密钥长度: " + properties.getRsa().getKeySize());
//            return keyPair;
//        } catch (NoSuchAlgorithmException e) {
//            throw new IllegalStateException("初始化RSA密钥对失败", e);
//        }
//    }
//
//    /**
//     * 加密配置属性
//     */
//    @Data
//    @ConfigurationProperties(prefix = "crypto")
//    public static class CryptoProperties {
//
//        /**
//         * 是否启用加解密功能
//         */
//        private boolean enabled = true;
//
//        /**
//         * RSA相关配置
//         */
//        private RsaConfig rsa = new RsaConfig();
//
//        @Data
//        public static class RsaConfig {
//            /**
//             * RSA密钥长度（位）
//             * 推荐2048或4096
//             */
//            private int keySize = 2048;
//        }
//    }
//}
