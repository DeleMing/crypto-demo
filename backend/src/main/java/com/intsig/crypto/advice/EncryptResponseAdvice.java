package com.intsig.crypto.advice;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.intsig.crypto.holder.CryptoContextHolder;
import com.intsig.crypto.util.AesUtil;
import lombok.Getter;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.annotation.Resource;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletResponse;

/**
 * 响应加密增强器
 * 实现ResponseBodyAdvice接口，在响应返回给客户端之前进行加密处理
 * <p>
 * 工作流程：
 * 1. 检查ThreadLocal中是否存在加密上下文（说明请求是加密的）
 * 2. 如果存在，使用请求中的AES密钥加密响应数据
 * 3. 生成新的IV（不能复用请求的IV）
 * 4. 设置响应头：X-Encrypt、X-Req-Id、X-IV-Resp、X-Tag-Resp
 * 5. 返回Base64编码的密文
 *
 * @author mingtao_liao
 */
@ControllerAdvice
public class EncryptResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 加密标识头
     */
    private static final String HEADER_ENCRYPT = "X-Encrypt";

    /**
     * 请求ID头（响应时回传）
     */
    private static final String HEADER_REQ_ID = "X-Req-Id";

    /**
     * 响应IV头
     */
    private static final String HEADER_IV_RESP = "X-IV-Resp";

    /**
     * 响应GCM标签头
     */
    private static final String HEADER_TAG_RESP = "X-Tag-Resp";

    /**
     * 本项目用fastjson进行序列化
     */
    @Resource
    private FastJsonConfig fastJsonConfig;

//    /**
//     * JSON序列化工具
//     */
//    private final ObjectMapper objectMapper;
//
//    public EncryptResponseAdvice(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//    }

    /**
     * 判断是否支持加密
     * 对所有返回类型都支持（在beforeBodyWrite中再判断是否需要加密）
     *
     * @param returnType    返回类型
     * @param converterType 消息转换器类型
     * @return true表示支持
     */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 在响应体写入之前进行加密处理
     *
     * @param body                  原始响应体
     * @param returnType            返回类型
     * @param selectedContentType   选择的内容类型
     * @param selectedConverterType 选择的转换器类型
     * @param request               请求对象
     * @param response              响应对象
     * @return 加密后的响应体（Base64字符串）或原始响应体
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        try {
            // 1. 检查是否存在加密上下文
            CryptoContextHolder.CryptoContext context = CryptoContextHolder.get();
            if (context == null) {
                // 请求未加密，响应也不需要加密
                return body;
            }

            // 2. 获取AES密钥和请求ID
            SecretKey aesKey = context.getAesKey();
            String requestId = context.getRequestId();

            // 3. 生成新的IV（重要：不能复用请求的IV）
            byte[] responseIv = AesUtil.generateIv();

            // 4. 将响应对象序列化为JSON字节数组
            // 使用 fastjson 生成字节
            SerializeConfig serializeConfig = fastJsonConfig.getSerializeConfig();
            SerializerFeature[] features = fastJsonConfig.getSerializerFeatures();

            byte[] responseBodyBytes = JSON.toJSONBytes(body, serializeConfig, features);
//            byte[] responseBodyBytes = objectMapper.writeValueAsBytes(body);

            // 5. 使用AES-GCM加密响应
            AesUtil.GcmEncryptionResult encryptionResult = AesUtil.encrypt(aesKey, responseIv, responseBodyBytes);
            byte[] encryptedCiphertext = encryptionResult.getCiphertext();
            byte[] gcmTag = encryptionResult.getTag();

            // 6. 设置响应头
            HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
            servletResponse.setHeader(HEADER_ENCRYPT, "true");
            servletResponse.setHeader(HEADER_REQ_ID, requestId);
            servletResponse.setHeader(HEADER_IV_RESP, AesUtil.toBase64(responseIv));
            servletResponse.setHeader(HEADER_TAG_RESP, AesUtil.toBase64(gcmTag));

            // 设置CORS响应头，确保前端能访问自定义响应头
            String origin = request.getHeaders().getFirst("Origin");
            if (origin != null && !origin.isEmpty()) {
                servletResponse.setHeader("Access-Control-Allow-Origin", origin);
                servletResponse.setHeader("Access-Control-Allow-Credentials", "true");
            } else {
                servletResponse.setHeader("Access-Control-Allow-Origin", "*");
            }

            // 暴露自定义响应头，让前端可以访问
            servletResponse.setHeader("Access-Control-Expose-Headers",
                    String.join(", ", HEADER_ENCRYPT, HEADER_REQ_ID, HEADER_IV_RESP, HEADER_TAG_RESP));

            // 7. 修改Content-Type为纯文本（因为返回的是Base64字符串）
            servletResponse.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");

            // 调试日志
            System.out.println("[EncryptResponseAdvice] 设置响应头:");
            System.out.println("[EncryptResponseAdvice]   " + HEADER_ENCRYPT + ": true");
            System.out.println("[EncryptResponseAdvice]   " + HEADER_REQ_ID + ": " + requestId);
            System.out.println("[EncryptResponseAdvice]   " + HEADER_IV_RESP + ": " + AesUtil.toBase64(responseIv));
            System.out.println("[EncryptResponseAdvice]   Access-Control-Expose-Headers: " +
                    String.join(", ", HEADER_ENCRYPT, HEADER_REQ_ID, HEADER_IV_RESP, HEADER_TAG_RESP));

            // 8. 返回Base64编码的密文
            return AesUtil.toBase64(encryptedCiphertext);

        } catch (Exception e) {
            // 加密失败时返回错误信息
            // 注意：这里不能抛出异常，否则会导致响应中断
            return new ErrorResponse("ENCRYPT_ERROR", "响应加密失败: " + e.getMessage());
        } finally {
            // 9. 清理ThreadLocal，防止内存泄漏
            CryptoContextHolder.clear();
        }
    }

    /**
     * 加密错误响应对象
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
