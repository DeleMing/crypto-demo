/**
 * Axios请求配置
 * 包含请求/响应加解密拦截器
 */

import axios from 'axios';
import { encryptWithRsaPublicKey } from './crypto/rsa';
import {
  aesGcmDecrypt,
  aesGcmEncrypt,
  exportAesKeyAsBase64,
  generateAesKey,
  generateIv,
} from './crypto/aes';
import { uint8ArrayToBase64, base64ToArrayBuffer } from './crypto/base64';
import { fetchPublicKey } from '../api/crypto';

/**
 * 生成简单的UUID
 * @returns {string} UUID字符串
 */
function generateUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * 密钥存储Map
 * 用于存储请求ID和对应的AES密钥
 * 格式：requestId -> { key: CryptoKey, expiresAt: timestamp }
 */
const keyStore = new Map();

/**
 * 密钥过期时间（毫秒）
 */
const KEY_TTL_MS = 60 * 1000; // 60秒

/**
 * 清理过期的密钥
 */
function cleanupExpiredKeys() {
  const now = Date.now();
  const expiredKeys = [];

  keyStore.forEach((value, key) => {
    if (value.expiresAt <= now) {
      expiredKeys.push(key);
    }
  });

  expiredKeys.forEach((key) => {
    keyStore.delete(key);
    console.log(`[Crypto] 清理过期密钥: ${key}`);
  });

  if (expiredKeys.length > 0) {
    console.log(`[Crypto] 清理了${expiredKeys.length}个过期密钥`);
  }
}

/**
 * 定期清理过期密钥
 */
setInterval(cleanupExpiredKeys, 10 * 1000); // 每10秒清理一次

/**
 * 创建Axios实例
 */
const service = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 请求拦截器 - 加密请求
 */
service.interceptors.request.use(
  async (config) => {
    // 检查是否需要加密
    // config.encrypt = true 表示需要加密
    if (!config.encrypt) {
      return config;
    }

    console.log('[Crypto] 开始加密请求');

    try {
      // 1. 生成请求ID
      const requestId = generateUuid();
      console.log(`[Crypto] 请求ID: ${requestId}`);

      // 2. 生成随机AES密钥（返回Base64字符串）
      const aesKey = await generateAesKey();

      // 3. 导出AES密钥为原始字节，然后转换为十六进制字符串（用于RSA加密）
      // 注意：JSEncrypt只能加密字符串，所以我们需要将32字节的密钥转换为64字符的十六进制字符串
      // aesKey现在是Base64字符串，需要转换为字节数组
      const rawKey = base64ToArrayBuffer(aesKey);
      const keyBytes = new Uint8Array(rawKey);
      const aesKeyHex = Array.from(keyBytes)
        .map(b => b.toString(16).padStart(2, '0'))
        .join('');
      
      console.log('[Crypto] ========== 密钥流程开始 ==========');
      console.log('[Crypto] [1] AES密钥原始字节长度:', keyBytes.length, '字节');
      console.log('[Crypto] [1] AES密钥十六进制字符串长度:', aesKeyHex.length, '字符');
      console.log('[Crypto] [1] AES密钥十六进制前32字符:', aesKeyHex.substring(0, 32) + '...');

      // 4. 生成随机IV
      const iv = generateIv();
      const ivBase64 = uint8ArrayToBase64(iv);
      console.log('[Crypto] [2] IV Base64:', ivBase64);
      console.log('[Crypto] [2] IV Base64长度:', ivBase64.length, '字符');

      // 5. 获取RSA公钥（如果缓存存在但解密失败，可以尝试强制刷新）
      let publicKeyPem;
      try {
        publicKeyPem = await fetchPublicKey();
      } catch (error) {
        console.warn('[Crypto] 获取公钥失败，尝试强制刷新:', error);
        // 如果获取失败，尝试强制刷新
        const { fetchPublicKeyForce } = await import('../api/crypto');
        publicKeyPem = await fetchPublicKeyForce();
      }
      console.log('[Crypto] [3] RSA公钥PEM前100字符:', publicKeyPem.substring(0, 100) + '...');
      console.log('[Crypto] [3] RSA公钥PEM长度:', publicKeyPem.length, '字符');
      console.log('[Crypto] [3] RSA公钥PEM是否包含BEGIN:', publicKeyPem.includes('-----BEGIN PUBLIC KEY-----'));
      console.log('[Crypto] [3] RSA公钥PEM是否包含END:', publicKeyPem.includes('-----END PUBLIC KEY-----'));

      // 6. 使用RSA公钥加密AES密钥（十六进制字符串）
      console.log('[Crypto] [4] 准备RSA加密，原始数据(AES密钥十六进制):', aesKeyHex.substring(0, 32) + '...');
      const encryptedAesKey = encryptWithRsaPublicKey(publicKeyPem, aesKeyHex);
      console.log('[Crypto] [4] RSA加密后的Base64前100字符:', encryptedAesKey.substring(0, 100) + '...');
      console.log('[Crypto] [4] RSA加密后的Base64长度:', encryptedAesKey.length, '字符');

      // 7. 使用AES-GCM加密请求体
      const payload = config.data || {};
      const { ciphertext, tag } = await aesGcmEncrypt(aesKey, iv, payload);

      // 8. 将AES密钥存储到Map中（用于响应解密）
      keyStore.set(requestId, {
        key: aesKey,
        expiresAt: Date.now() + KEY_TTL_MS,
      });
      console.log(`[Crypto] 密钥已存储，当前存储数量: ${keyStore.size}`);

      // 9. 修改请求配置
      config.headers['X-Encrypt'] = 'true';
      config.headers['X-Req-Id'] = requestId;
      config.headers['X-Key'] = encryptedAesKey;
      config.headers['X-IV'] = ivBase64;
      config.headers['X-Tag'] = tag;

      // 10. 设置请求体为Base64编码的密文
      // 重要：将Content-Type改为text/plain，避免Axios将字符串JSON序列化（加引号）
      config.headers['Content-Type'] = 'text/plain';
      config.data = ciphertext;
      
      console.log('[Crypto] [5] 请求体密文Base64长度:', ciphertext.length, '字符');
      console.log('[Crypto] [5] 请求体密文Base64前50字符:', ciphertext.substring(0, 50) + '...');
      console.log('[Crypto] 请求加密完成');
      return config;
    } catch (error) {
      console.error('[Crypto] 请求加密失败:', error);
      return Promise.reject(new Error(`请求加密失败: ${error.message}`));
    }
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器 - 解密响应
 */
service.interceptors.response.use(
  async (response) => {
    // 检查响应是否加密
    const isEncrypted = response.headers['x-encrypt'] === 'true' || 
                        response.headers['X-Encrypt'] === 'true';
    if (!isEncrypted) {
      return response;
    }

    console.log('[Crypto] ========== 开始解密响应 ==========');

    try {
      // 1. 获取请求ID
      const requestId = response.headers['x-req-id'] || response.headers['X-Req-Id'];
      if (!requestId) {
        throw new Error('响应头缺少X-Req-Id');
      }
      console.log(`[Crypto] [1] 响应请求ID: ${requestId}`);

      // 2. 获取响应IV和Tag
      const ivResp = response.headers['x-iv-resp'] || response.headers['X-IV-Resp'];
      const tagResp = response.headers['x-tag-resp'] || response.headers['X-Tag-Resp'];
      if (!ivResp || !tagResp) {
        console.error('[Crypto] 响应头信息:', {
          'x-encrypt': response.headers['x-encrypt'] || response.headers['X-Encrypt'],
          'x-req-id': response.headers['x-req-id'] || response.headers['X-Req-Id'],
          'x-iv-resp': ivResp,
          'x-tag-resp': tagResp,
        });
        throw new Error('响应头缺少X-IV-Resp或X-Tag-Resp');
      }
      console.log(`[Crypto] [2] IV Base64: ${ivResp}`);
      console.log(`[Crypto] [2] Tag Base64: ${tagResp}`);

      // 3. 从Map中获取对应的AES密钥
      const keyInfo = keyStore.get(requestId);
      if (!keyInfo) {
        console.error(`[Crypto] 当前keyStore中的请求ID:`, Array.from(keyStore.keys()));
        throw new Error(`未找到请求ID对应的密钥: ${requestId}`);
      }
      console.log(`[Crypto] [3] 找到对应的AES密钥`);

      // 4. 获取响应体（可能是字符串或对象）
      let ciphertextBase64;
      if (typeof response.data === 'string') {
        ciphertextBase64 = response.data;
      } else if (response.data && typeof response.data === 'object') {
        // 如果Axios已经解析为对象，尝试获取原始字符串
        ciphertextBase64 = JSON.stringify(response.data);
      } else {
        throw new Error('响应体格式不正确');
      }
      
      console.log(`[Crypto] [4] 响应体密文Base64长度: ${ciphertextBase64.length} 字符`);
      console.log(`[Crypto] [4] 响应体密文Base64前50字符: ${ciphertextBase64.substring(0, 50)}...`);

      // 5. 使用AES-GCM解密响应体
      const decryptedData = await aesGcmDecrypt(keyInfo.key, ivResp, ciphertextBase64, tagResp);
      console.log(`[Crypto] [5] ✓ 响应解密成功！`);
      console.log(`[Crypto] [5] 解密后的数据:`, decryptedData);

      // 6. 清理密钥
      keyStore.delete(requestId);
      console.log(`[Crypto] [6] 密钥已清理，当前存储数量: ${keyStore.size}`);

      // 7. 替换响应数据
      response.data = decryptedData;

      console.log('[Crypto] ========== 响应解密完成 ==========');
      return response;
    } catch (error) {
      console.error('[Crypto] 响应解密失败:', error);
      // 清理可能存在的密钥
      const requestId = response.headers['x-req-id'];
      if (requestId) {
        keyStore.delete(requestId);
      }
      return Promise.reject(new Error(`响应解密失败: ${error.message}`));
    }
  },
  (error) => {
    // 请求失败时也要清理密钥
    if (error.config && error.config.headers) {
      const requestId = error.config.headers['X-Req-Id'];
      if (requestId) {
        keyStore.delete(requestId);
        console.log(`[Crypto] 请求失败，清理密钥: ${requestId}`);
      }
    }
    return Promise.reject(error);
  }
);

export default service;
