package com.lmt.crypto.config;

import com.lmt.crypto.filter.DecryptRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;

/**
 * 过滤器配置类
 * 注册解密过滤器到Spring容器
 *
 * @author mingtao_liao
 */
@Configuration
public class FilterConfig {

    /**
     * 注册请求解密过滤器
     *
     * @param rsaKeyPair   RSA密钥对
     * @param objectMapper JSON序列化工具
     * @return Filter注册Bean
     */
    @Bean
    public FilterRegistrationBean<DecryptRequestFilter> decryptRequestFilter(
            KeyPair rsaKeyPair,
            ObjectMapper objectMapper) {

        FilterRegistrationBean<DecryptRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DecryptRequestFilter(rsaKeyPair, objectMapper));

        // 拦截所有URL
        registration.addUrlPatterns("/*");

        // 设置过滤器优先级（数字越小优先级越高）
        // 设置为较高优先级，确保在其他过滤器之前执行
        registration.setOrder(1);

        return registration;
    }
}
