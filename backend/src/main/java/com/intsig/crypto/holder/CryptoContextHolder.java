package com.intsig.crypto.holder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.crypto.SecretKey;

/**
 * 加密上下文持有者
 * 使用ThreadLocal在请求线程中传递加密相关的上下文信息
 * 主要用于在Filter解密后将AES密钥传递给ResponseBodyAdvice进行响应加密
 *
 * @author mingtao_liao
 */
public final class CryptoContextHolder {

    /**
     * ThreadLocal存储加密上下文
     * 每个请求线程都有独立的上下文，互不干扰
     */
    private static final ThreadLocal<CryptoContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private CryptoContextHolder() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 设置当前线程的加密上下文
     *
     * @param requestId 请求ID，用于关联请求和响应
     * @param aesKey    AES密钥，用于响应加密
     */
    public static void set(String requestId, SecretKey aesKey) {
        if (requestId == null || aesKey == null) {
            throw new IllegalArgumentException("请求ID和AES密钥不能为空");
        }
        CONTEXT_HOLDER.set(new CryptoContext(requestId, aesKey));
    }

    /**
     * 获取当前线程的加密上下文
     *
     * @return 加密上下文，如果未设置则返回null
     */
    public static CryptoContext get() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前线程的加密上下文
     * 必须在请求处理完成后调用，防止内存泄漏
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 加密上下文
     * 包含请求ID和AES密钥
     */
    @Getter
    @AllArgsConstructor
    public static class CryptoContext {
        /**
         * 请求ID
         */
        private final String requestId;
        /**
         * AES密钥
         */
        private final SecretKey aesKey;
    }
}
