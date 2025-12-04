package com.lmt.crypto.controller;

import com.lmt.crypto.config.KeyPairLoader;
import com.lmt.crypto.util.RsaUtil;
import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

/**
 * 加密相关接口控制器
 * 提供公钥获取接口和测试接口
 *
 * @author mingtao_liao
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CryptoController {


//    public CryptoController(KeyPair rsaKeyPair) {
//        this.rsaKeyPair = rsaKeyPair;
//    }

    /**
     * RSA密钥对
     */
    private final KeyPair rsaKeyPair;

    /**
     * 构造函数，注入RSA密钥对
     *
     * @param keyPairLoader RSA密钥对
     */
    public CryptoController(KeyPairLoader keyPairLoader) {
        this.rsaKeyPair = keyPairLoader.getKeyPair();
    }

    /**
     * 获取RSA公钥
     * 前端启动时调用此接口获取公钥，用于加密AES密钥
     * 返回PEM格式的公钥，兼容JSEncrypt库
     *
     * @return PEM格式的RSA公钥
     */
    @GetMapping(value = "/crypto/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getPublicKey() {
        System.out.println("[CryptoController] ========== 获取RSA公钥 ==========");
        String pem = RsaUtil.publicKeyToPem(rsaKeyPair);
        System.out.println("[CryptoController] 返回公钥PEM格式，总长度: " + pem.length() + " 字符");
        System.out.println("[CryptoController] 公钥前100字符: " + pem.substring(0, Math.min(100, pem.length())) + "...");
        System.out.println("[CryptoController] 公钥后50字符: ..." + pem.substring(Math.max(0, pem.length() - 50)));
        System.out.println("[CryptoController] 是否包含BEGIN标记: " + pem.contains("-----BEGIN PUBLIC KEY-----"));
        System.out.println("[CryptoController] 是否包含END标记: " + pem.contains("-----END PUBLIC KEY-----"));

        // 打印原始公钥的Base64（不含PEM标记）
        String base64Only = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        System.out.println("[CryptoController] 公钥Base64(不含PEM标记)长度: " + base64Only.length() + " 字符");
        System.out.println("[CryptoController] 公钥Base64前50字符: " + base64Only.substring(0, Math.min(50, base64Only.length())) + "...");
        System.out.println("[CryptoController] ========== 公钥返回完成 ==========");
        return pem;
    }

    /**
     * 测试接口 - 验证RSA密钥对
     * 用于测试密钥对是否正常工作
     *
     * @return 测试结果
     */
    @GetMapping("/crypto/test-keypair")
    public Map<String, Object> testKeyPair() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 测试数据
            String testData = "Hello, RSA Test!";
            byte[] testBytes = testData.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // 使用公钥加密
            byte[] encrypted = RsaUtil.encrypt(rsaKeyPair.getPublic(), testBytes);
            result.put("encryptedLength", encrypted.length);

            // 使用私钥解密
            byte[] decrypted = RsaUtil.decrypt(rsaKeyPair, encrypted);
            String decryptedData = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);

            // 验证结果
            boolean match = testData.equals(decryptedData);
            result.put("testPassed", match);
            result.put("originalData", testData);
            result.put("decryptedData", decryptedData);
            result.put("message", match ? "密钥对工作正常" : "密钥对验证失败");

        } catch (Exception e) {
            result.put("testPassed", false);
            result.put("error", e.getMessage());
            result.put("message", "密钥对测试失败: " + e.getClass().getSimpleName());
        }

        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 测试接口 - 回显请求内容
     * 支持加密和非加密两种模式
     * 根据请求头X-Encrypt判断是否需要解密请求和加密响应
     *
     * @param userInfo 请求负载（JSON对象）
     * @return 包含回显内容和状态的响应
     */
    @PostMapping("/test/echo")
    public Map<String, Object> echo(@RequestBody UserInfo userInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("echo", userInfo);
        result.put("status", "success");
        result.put("message", "请求处理成功");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 测试接口 - 用户信息
     * 用于测试加密功能的另一个示例接口
     *
     * @param userInfo 用户信息（JSON对象）
     * @return 处理结果
     */
    @PostMapping("/test/user-info")
    public Map<String, Object> userInfo(@RequestBody UserInfo userInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("received", userInfo);
        result.put("processed", true);
        result.put("userId", userInfo.getUserId());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    @Data
    public static class UserInfo {
        /**
         * 用户ID
         */
        private Integer userId;
        /**
         * 消息内容
         */
        private String message;
        /**
         * 时间戳
         */
        private String timestamp;
    }

    /**
     * 测试接口 - 获取服务器信息（不需要加密）
     * 用于测试非加密接口是否正常工作
     *
     * @return 服务器信息
     */
    @GetMapping("/test/server-info")
    public Map<String, Object> serverInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("serverName", "Crypto Demo Server");
        info.put("version", "1.0.0");
        info.put("timestamp", System.currentTimeMillis());
        info.put("cryptoEnabled", true);
        return info;
    }


}
