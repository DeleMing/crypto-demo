/**
 * RSA加密工具
 * 使用JSEncrypt库实现RSA公钥加密
 * 主要用于加密AES密钥
 */

import JSEncrypt from 'jsencrypt';

/**
 * 使用RSA公钥加密数据
 * @param {string} publicKeyPem - PEM格式的RSA公钥
 * @param {string} data - 要加密的数据（通常是Base64编码的AES密钥）
 * @returns {string} Base64编码的加密数据
 * @throws {Error} 加密失败时抛出异常
 */
export function encryptWithRsaPublicKey(publicKeyPem, data) {
  if (!publicKeyPem) {
    throw new Error('RSA公钥不能为空');
  }
  if (!data) {
    throw new Error('待加密数据不能为空');
  }

  // 验证公钥格式（JSEncrypt 支持 BEGIN PUBLIC KEY / BEGIN RSA PUBLIC KEY）
  if (
    !publicKeyPem.includes('-----BEGIN PUBLIC KEY-----') &&
    !publicKeyPem.includes('-----BEGIN RSA PUBLIC KEY-----')
  ) {
    throw new Error('RSA公钥必须是PEM格式（包含BEGIN/END标记）');
  }

  console.log('[RSA] ========== RSA加密流程 ==========');
  console.log('[RSA] 使用的公钥 PEM 内容开头:', publicKeyPem.slice(0, 50) + '...');
  
  // 提取公钥的Base64部分用于对比
  const publicKeyBase64 = publicKeyPem
    .replace('-----BEGIN PUBLIC KEY-----', '')
    .replace('-----END PUBLIC KEY-----', '')
    .replace(/\s+/g, '');
  console.log('[RSA] 使用的公钥Base64(不含PEM标记)前50字符:', publicKeyBase64.substring(0, 50) + '...');
  console.log('[RSA] 使用的公钥Base64长度:', publicKeyBase64.length, '字符');
  
  console.log('[RSA] 要加密的原始数据(Base64):', data);
  console.log('[RSA] 原始数据长度:', data.length, '字符');

  const encryptor = new JSEncrypt();
  // setPublicKey 没有返回值，失败时会在 encrypt 阶段返回 null
  encryptor.setPublicKey(publicKeyPem);
  console.log('[RSA] 公钥已设置到JSEncrypt');

  const encrypted = encryptor.encrypt(data);
  if (!encrypted) {
    throw new Error('RSA加密失败，可能是数据过长或公钥格式不正确');
  }

  console.log('[RSA] 加密成功！');
  console.log('[RSA] 原始数据长度:', data.length, '字符');
  console.log('[RSA] 加密后Base64长度:', encrypted.length, '字符');
  console.log('[RSA] 加密后Base64前50字符:', encrypted.substring(0, 50) + '...');
  console.log('[RSA] ========== RSA加密完成 ==========');
  return encrypted;
}

/**
 * 验证RSA公钥格式是否正确
 * @param {string} publicKeyPem - PEM格式的RSA公钥
 * @returns {boolean} 格式正确返回true，否则返回false
 */
export function validatePublicKey(publicKeyPem) {
  if (!publicKeyPem || typeof publicKeyPem !== 'string') {
    return false;
  }

  try {
    const encryptor = new JSEncrypt();
    encryptor.setPublicKey(publicKeyPem);
    return true;
  } catch (error) {
    return false;
  }
}
