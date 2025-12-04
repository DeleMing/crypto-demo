/**
 * 加密相关API
 * 提供获取RSA公钥的功能
 */

import axios from 'axios';

/**
 * localStorage缓存键
 */
const PUBLIC_KEY_CACHE_KEY = 'CRYPTO_PUBLIC_KEY_PEM';

/**
 * 内存缓存
 */
let cachedPublicKey = null;

/**
 * API基础URL
 */
const BASE_URL = 'http://localhost:8080';

/**
 * 获取RSA公钥
 * 优先从内存缓存读取，其次从localStorage，最后从服务器获取
 *
 * @returns {Promise<string>} PEM格式的RSA公钥
 * @throws {Error} 获取失败时抛出异常
 */
export async function fetchPublicKey() {
  // 临时禁用缓存，强制每次都从服务器获取（用于调试密钥不匹配问题）
  const FORCE_REFRESH = true; // 设置为 false 恢复缓存功能
  
  if (!FORCE_REFRESH) {
    // 1. 检查内存缓存
    if (cachedPublicKey) {
      console.log('[Crypto] ⚠️ 从内存缓存读取公钥（可能是旧公钥！）');
      const base64Only = cachedPublicKey
        .replace('-----BEGIN PUBLIC KEY-----', '')
        .replace('-----END PUBLIC KEY-----', '')
        .replace(/\s+/g, '');
      console.log('[Crypto] 缓存公钥Base64前50字符:', base64Only.substring(0, 50) + '...');
      return cachedPublicKey;
    }

    // 2. 检查localStorage缓存
    try {
      const localStorageKey = window.localStorage.getItem(PUBLIC_KEY_CACHE_KEY);
      if (localStorageKey) {
        // 验证是否为PEM格式，如果不是则清除缓存
        if (localStorageKey.includes('-----BEGIN PUBLIC KEY-----') && 
            localStorageKey.includes('-----END PUBLIC KEY-----')) {
          console.log('[Crypto] ⚠️ 从localStorage读取公钥（PEM格式，可能是旧公钥！）');
          const base64Only = localStorageKey
            .replace('-----BEGIN PUBLIC KEY-----', '')
            .replace('-----END PUBLIC KEY-----', '')
            .replace(/\s+/g, '');
          console.log('[Crypto] localStorage公钥Base64前50字符:', base64Only.substring(0, 50) + '...');
          cachedPublicKey = localStorageKey;
          return cachedPublicKey;
        } else {
          console.warn('[Crypto] localStorage中的公钥不是PEM格式，清除缓存');
          window.localStorage.removeItem(PUBLIC_KEY_CACHE_KEY);
        }
      }
    } catch (error) {
      console.warn('[Crypto] 读取localStorage失败:', error);
    }
  } else {
    console.log('[Crypto] 🔄 强制刷新模式：清除所有缓存，从服务器获取新公钥');
    cachedPublicKey = null;
    try {
      window.localStorage.removeItem(PUBLIC_KEY_CACHE_KEY);
    } catch (error) {
      // 忽略错误
    }
  }

  // 3. 从服务器获取公钥
  try {
    console.log('[Crypto] 从服务器获取公钥');
    const response = await axios.get(`${BASE_URL}/api/crypto/public-key`, {
      responseType: 'text',
      timeout: 10000,
    });

    let publicKey = response.data;
    if (!publicKey || typeof publicKey !== 'string') {
      throw new Error('服务器返回的公钥格式不正确');
    }

    // 清理可能的空白字符，确保PEM格式正确
    publicKey = publicKey.trim();
    
    // 验证是否为PEM格式
    if (!publicKey.includes('-----BEGIN PUBLIC KEY-----') || 
        !publicKey.includes('-----END PUBLIC KEY-----')) {
      console.warn('[Crypto] 警告：服务器返回的公钥可能不是PEM格式，尝试使用');
    }

    // 缓存到内存和localStorage
    cachedPublicKey = publicKey;
    try {
      window.localStorage.setItem(PUBLIC_KEY_CACHE_KEY, publicKey);
    } catch (error) {
      console.warn('[Crypto] 保存公钥到localStorage失败:', error);
    }

    // 提取公钥的Base64部分（不含PEM标记）用于对比
    const base64Only = publicKey
      .replace('-----BEGIN PUBLIC KEY-----', '')
      .replace('-----END PUBLIC KEY-----', '')
      .replace(/\s+/g, '');
    
    console.log('[Crypto] 公钥获取成功，格式:', publicKey.substring(0, 30) + '...');
    console.log('[Crypto] 公钥Base64(不含PEM标记)长度:', base64Only.length, '字符');
    console.log('[Crypto] 公钥Base64前50字符:', base64Only.substring(0, 50) + '...');
    return publicKey;
  } catch (error) {
    throw new Error(`获取RSA公钥失败: ${error.message}`);
  }
}

/**
 * 清除公钥缓存
 * 当公钥更新时调用
 */
export function clearPublicKeyCache() {
  cachedPublicKey = null;
  try {
    window.localStorage.removeItem(PUBLIC_KEY_CACHE_KEY);
  } catch (error) {
    console.warn('[Crypto] 清除localStorage缓存失败:', error);
  }
  console.log('[Crypto] 公钥缓存已清除');
}

/**
 * 强制从服务器获取新的公钥（忽略缓存）
 * 用于解决密钥不匹配问题
 *
 * @returns {Promise<string>} PEM格式的RSA公钥
 * @throws {Error} 获取失败时抛出异常
 */
export async function fetchPublicKeyForce() {
  // 清除所有缓存
  clearPublicKeyCache();
  
  // 从服务器获取新公钥
  return await fetchPublicKey();
}
