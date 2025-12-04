package com.lmt.crypto.filter;

import com.lmt.crypto.holder.CryptoContextHolder;
import com.lmt.crypto.util.AesUtil;
import com.lmt.crypto.util.RsaUtil;
import com.lmt.crypto.wrapper.CachedBodyHttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 请求解密过滤器
 * 在Filter链的早期拦截需要解密的请求，完成以下工作：
 * 1. 检查X-Encrypt请求头，判断是否需要解密
 * 2. 使用RSA私钥解密客户端传输的AES密钥
 * 3. 使用AES-GCM解密请求体
 * 4. 将解密后的请求体和AES密钥传递给后续处理流程
 *
 * @author mingtao_liao
 */
public class DecryptRequestFilter extends OncePerRequestFilter {

    /**
     * 加密标识头 - 标识请求是否需要解密
     */
    private static final String HEADER_ENCRYPT = "X-Encrypt";

    /**
     * 请求ID头 - 用于关联请求和响应
     */
    private static final String HEADER_REQ_ID = "X-Req-Id";

    /**
     * 加密的AES密钥头 - 使用RSA公钥加密的AES密钥（Base64）
     */
    private static final String HEADER_KEY = "X-Key";

    /**
     * 初始化向量头 - AES-GCM加密使用的IV（Base64）
     */
    private static final String HEADER_IV = "X-IV";

    /**
     * GCM认证标签头 - GCM模式的认证标签（Base64）
     */
    private static final String HEADER_TAG = "X-Tag";

    /**
     * RSA密钥对
     */
    private final KeyPair rsaKeyPair;

    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;

    public DecryptRequestFilter(KeyPair rsaKeyPair, ObjectMapper objectMapper) {
        this.rsaKeyPair = rsaKeyPair;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断是否应该跳过过滤
     * 只有X-Encrypt头为true的请求才需要解密
     *
     * @param request 当前请求
     * @return true表示跳过此过滤器，false表示需要执行过滤
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String encryptHeader = request.getHeader(HEADER_ENCRYPT);
        return !"true".equalsIgnoreCase(encryptHeader);
    }

    /**
     * 执行解密逻辑
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 提取并验证请求头
            String encryptedKeyBase64 = request.getHeader(HEADER_KEY);
            String ivBase64 = request.getHeader(HEADER_IV);
            String tagBase64 = request.getHeader(HEADER_TAG);
            String requestId = request.getHeader(HEADER_REQ_ID);

            if (!validateHeaders(encryptedKeyBase64, ivBase64, tagBase64, requestId)) {
                writeErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_HEADERS", "缺少必需的加密请求头");
                return;
            }

            // 2. 读取加密的请求体
            byte[] encryptedBodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
            if (encryptedBodyBytes.length == 0) {
                writeErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST,
                        "EMPTY_BODY", "加密请求体不能为空");
                return;
            }

            // 3. RSA解密AES密钥
            System.out.println("[DecryptFilter] ========== 密钥解密流程开始 ==========");
            System.out.println("[DecryptFilter] [1] 接收到的加密AES密钥Base64长度: " + encryptedKeyBase64.length() + " 字符");
            System.out.println("[DecryptFilter] [1] 接收到的加密AES密钥Base64前100字符: " +
                    encryptedKeyBase64.substring(0, Math.min(100, encryptedKeyBase64.length())) + "...");
            System.out.println("[DecryptFilter] [1] 接收到的加密AES密钥Base64是否包含空白字符: " +
                    encryptedKeyBase64.matches(".*\\s+.*"));

            byte[] encryptedKeyBytes;
            try {
                encryptedKeyBytes = AesUtil.fromBase64(encryptedKeyBase64);
            } catch (IllegalArgumentException e) {
                System.err.println("[DecryptFilter] [1.1] Base64解码失败: " + e.getMessage());
                throw e;
            }
            System.out.println("[DecryptFilter] [2] Base64解码后的加密密钥字节长度: " + encryptedKeyBytes.length + " 字节");
            System.out.println("[DecryptFilter] [2] 加密密钥前10字节(Hex): " + bytesToHex(encryptedKeyBytes, 10));

//            // 验证密钥对是否匹配：尝试用公钥加密一个测试数据，然后用私钥解密
//            System.out.println("[DecryptFilter] [3] 开始密钥对自验证...");
//            try {
//                byte[] testData = "test".getBytes(StandardCharsets.UTF_8);
//                System.out.println("[DecryptFilter] [3.1] 测试数据: " + new String(testData, StandardCharsets.UTF_8));
//                byte[] testEncrypted = RsaUtil.encrypt(rsaKeyPair.getPublic(), testData);
//                System.out.println("[DecryptFilter] [3.2] 测试数据加密后长度: " + testEncrypted.length + " 字节");
//                byte[] testDecrypted = RsaUtil.decrypt(rsaKeyPair, testEncrypted);
//                System.out.println("[DecryptFilter] [3.3] 测试数据解密后: " + new String(testDecrypted, StandardCharsets.UTF_8));
//                if (java.util.Arrays.equals(testData, testDecrypted)) {
//                    System.out.println("[DecryptFilter] [3.4] ✓ 密钥对自验证成功！");
//                } else {
//                    System.err.println("[DecryptFilter] [3.4] ✗ 警告：密钥对自验证失败！");
//                }
//            } catch (Exception e) {
//                System.err.println("[DecryptFilter] [3.4] ✗ 密钥对自验证异常: " + e.getMessage());
//                e.printStackTrace();
//            }
//
//            System.out.println("[DecryptFilter] [4] 开始RSA解密接收到的AES密钥...");
//
//            // 尝试用当前密钥对的公钥加密相同的数据，看看是否能匹配
//            try {
//                // 获取当前密钥对的公钥Base64
//                String currentPublicKeyBase64 = RsaUtil.publicKeyToBase64(rsaKeyPair);
//                System.out.println("[DecryptFilter] [4.1] 当前后端公钥Base64前50字符: " +
//                        currentPublicKeyBase64.substring(0, Math.min(50, currentPublicKeyBase64.length())) + "...");
//
//                // 尝试用当前公钥加密一个测试数据，然后解密
//                byte[] testEncrypt = RsaUtil.encrypt(rsaKeyPair.getPublic(), "test123".getBytes(StandardCharsets.UTF_8));
//                byte[] testDecrypt = RsaUtil.decrypt(rsaKeyPair, testEncrypt);
//                System.out.println("[DecryptFilter] [4.2] 当前密钥对加密/解密测试: " +
//                        new String(testDecrypt, StandardCharsets.UTF_8));
//
//                // 尝试解密前端传来的数据
//                System.out.println("[DecryptFilter] [4.3] 尝试解密前端传来的加密AES密钥...");
//            } catch (Exception e) {
//                System.err.println("[DecryptFilter] [4.1] 密钥对测试异常: " + e.getMessage());
//                e.printStackTrace();
//            }

            byte[] decryptedBytes = RsaUtil.decrypt(rsaKeyPair, encryptedKeyBytes);
            System.out.println("[DecryptFilter] [4] ✓ RSA解密成功！");
            System.out.println("[DecryptFilter] [4] RSA解密后的原始字节长度: " + decryptedBytes.length + " 字节");
            System.out.println("[DecryptFilter] [4] RSA解密后的原始内容(UTF-8): " +
                    new String(decryptedBytes, StandardCharsets.UTF_8));

            // 将解密后的内容转换为AES密钥字节数组
            // 前端发送的是十六进制字符串，需要转换回字节数组
            String decryptedHex = new String(decryptedBytes, StandardCharsets.UTF_8);
            System.out.println("[DecryptFilter] [4.1] 解密后的十六进制字符串长度: " + decryptedHex.length() + " 字符");
            System.out.println("[DecryptFilter] [4.1] 解密后的十六进制字符串前32字符: " +
                    decryptedHex.substring(0, Math.min(32, decryptedHex.length())) + "...");

            // 将十六进制字符串转换为字节数组
            byte[] aesKeyBytes = hexStringToBytes(decryptedHex);
            System.out.println("[DecryptFilter] [4.2] 转换后的AES密钥字节长度: " + aesKeyBytes.length + " 字节");
            System.out.println("[DecryptFilter] [4.2] AES密钥前10字节(Hex): " + bytesToHex(aesKeyBytes, 10));

            // 验证密钥长度（AES-256应该是32字节）
            if (aesKeyBytes.length != 32) {
                throw new IllegalStateException(
                        String.format("AES密钥长度不正确: 期望32字节，实际%d字节", aesKeyBytes.length));
            }

            SecretKey aesKey = AesUtil.bytesToKey(aesKeyBytes);

            // 4. AES-GCM解密请求体
            System.out.println("[DecryptFilter] [5] 开始AES-GCM解密请求体...");
            byte[] iv = AesUtil.fromBase64(ivBase64);
            System.out.println("[DecryptFilter] [5.1] IV Base64: " + ivBase64);
            System.out.println("[DecryptFilter] [5.1] IV字节长度: " + iv.length + " 字节");

            byte[] tag = AesUtil.fromBase64(tagBase64);
            System.out.println("[DecryptFilter] [5.2] Tag Base64: " + tagBase64);
            System.out.println("[DecryptFilter] [5.2] Tag字节长度: " + tag.length + " 字节");

            String encryptedBodyBase64 = new String(encryptedBodyBytes, StandardCharsets.UTF_8);
            System.out.println("[DecryptFilter] [5.3] 请求体原始字符串长度: " + encryptedBodyBase64.length() + " 字符");
            System.out.println("[DecryptFilter] [5.3] 请求体原始字符串前50字符: " +
                    encryptedBodyBase64.substring(0, Math.min(50, encryptedBodyBase64.length())) + "...");

            // 如果请求体被JSON序列化了（前后有引号），去除引号
            encryptedBodyBase64 = encryptedBodyBase64.trim();
            if (encryptedBodyBase64.startsWith("\"") && encryptedBodyBase64.endsWith("\"")) {
                System.out.println("[DecryptFilter] [5.3.1] 检测到JSON序列化的字符串，去除引号");
                encryptedBodyBase64 = encryptedBodyBase64.substring(1, encryptedBodyBase64.length() - 1);
                // 处理转义的引号
                encryptedBodyBase64 = encryptedBodyBase64.replace("\\\"", "\"");
            }

            System.out.println("[DecryptFilter] [5.3.2] 清理后的请求体Base64长度: " + encryptedBodyBase64.length() + " 字符");
            System.out.println("[DecryptFilter] [5.3.2] 清理后的请求体Base64前50字符: " +
                    encryptedBodyBase64.substring(0, Math.min(50, encryptedBodyBase64.length())) + "...");

            byte[] ciphertext = AesUtil.fromBase64(encryptedBodyBase64);
            System.out.println("[DecryptFilter] [5.4] 密文字节长度: " + ciphertext.length + " 字节");

            byte[] decryptedBody = AesUtil.decrypt(aesKey, iv, ciphertext, tag);
            System.out.println("[DecryptFilter] [5.5] ✓ AES-GCM解密成功！");
            System.out.println("[DecryptFilter] [5.5] 解密后的请求体长度: " + decryptedBody.length + " 字节");
            System.out.println("[DecryptFilter] [5.5] 解密后的请求体内容: " +
                    new String(decryptedBody, StandardCharsets.UTF_8));
            System.out.println("[DecryptFilter] ========== 密钥解密流程完成 ==========");

            // 5. 将AES密钥和请求ID存入ThreadLocal，供响应加密使用
            CryptoContextHolder.set(requestId, aesKey);

            // 6. 包装请求，使用解密后的请求体
            // 注意：解密后的内容是JSON格式，需要修改Content-Type为application/json
            CachedBodyHttpServletRequest wrappedRequest =
                    new CachedBodyHttpServletRequest(request, decryptedBody);

            // 创建一个包装器来修改Content-Type头
            HttpServletRequest finalRequest = new HttpServletRequestWrapper(wrappedRequest) {
                @Override
                public String getContentType() {
                    return MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";
                }

                @Override
                public String getHeader(String name) {
                    if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                        return MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";
                    }
                    return super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                        return Collections.enumeration(
                                Collections.singletonList(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"));
                    }
                    return super.getHeaders(name);
                }
            };

            // 7. 继续过滤链
            filterChain.doFilter(finalRequest, response);

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            // Base64解码失败或参数验证失败
            writeErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST,
                    "DECODE_ERROR", "解码失败：" + e.getMessage());
        } catch (IllegalStateException e) {
            e.printStackTrace();
            // RSA/AES解密失败或GCM标签验证失败
            writeErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST,
                    "DECRYPT_ERROR", "解密失败：" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            // 其他未预期的异常
            writeErrorResponse(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "服务器内部错误");
        } finally {
            // 清理ThreadLocal，防止内存泄漏
            // 注意：只在异常情况下清理，正常情况下由ResponseBodyAdvice清理
            if (response.isCommitted()) {
                CryptoContextHolder.clear();
            }
        }
    }

    /**
     * 验证必需的请求头是否存在
     */
    private boolean validateHeaders(String encryptedKey, String iv, String tag, String requestId) {
        return encryptedKey != null && !encryptedKey.trim().isEmpty()
                && iv != null && !iv.trim().isEmpty()
                && tag != null && !tag.trim().isEmpty()
                && requestId != null && !requestId.trim().isEmpty();
    }

    /**
     * 写入错误响应，并附带必要的 CORS 响应头，防止浏览器拦截
     */
    private void writeErrorResponse(HttpServletRequest request,
                                    HttpServletResponse response,
                                    int status,
                                    String code, String message) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // CORS 处理：确保前端能拿到错误信息，而不是被浏览器拦截
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
        } else {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        ErrorResponse errorResponse = new ErrorResponse(code, message);
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * 将字节数组转换为十六进制字符串（用于调试）
     */
    private String bytesToHex(byte[] bytes, int maxLength) {
        if (bytes == null || bytes.length == 0) {
            return "empty";
        }
        int length = Math.min(bytes.length, maxLength);
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hex.append(String.format("%02X", bytes[i]));
            if (i < length - 1) {
                hex.append(" ");
            }
        }
        if (bytes.length > maxLength) {
            hex.append("...");
        }
        return hex.toString();
    }

    /**
     * 将十六进制字符串转换为字节数组
     *
     * @param hex 十六进制字符串（例如："1a2b3c"）
     * @return 字节数组
     * @throws IllegalArgumentException 如果十六进制字符串格式不正确
     */
    private byte[] hexStringToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("十六进制字符串不能为空");
        }

        // 去除可能的空白字符
        hex = hex.trim().replaceAll("\\s+", "");

        // 验证长度（必须是偶数）
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    String.format("十六进制字符串长度必须是偶数，实际长度: %d", hex.length()));
        }

        // 验证字符（只能是0-9, a-f, A-F）
        if (!hex.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("十六进制字符串包含非法字符");
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }

        return bytes;
    }

    /**
     * 错误响应对象
     */
    @Getter
    private static class ErrorResponse {
        /**
         * 错误码
         */
        private final String code;
        /**
         * 错误信息
         */
        private final String message;
        /**
         * 错误时间戳
         */
        private final long timestamp;

        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

    }
}
