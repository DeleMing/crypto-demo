/**
 * AES加解密工具
 * 使用node-forge实现AES-256-GCM加解密
 * GCM模式提供完整性与机密性校验
 * 
 * 注意：需要在HTML中引入CryptoJS库
 * 推荐版本：CryptoJS 4.1.1
 * CDN: https://cdnjs.cloudflare.com/ajax/libs/crypto-js/4.1.1/crypto-js.min.js
 * 或: https://cdn.jsdelivr.net/npm/crypto-js@4.1.1/crypto-js.js
 */

import { base64ToUint8Array } from './base64';
import CryptoJS from 'crypto-js';
import forge from 'node-forge';
// 确保CryptoJS已加载
if (typeof CryptoJS === 'undefined') {
  throw new Error('CryptoJS未加载，请确保在HTML中引入CryptoJS库（推荐版本4.1.1）');
}

/**
 * AES密钥长度（位）
 */
const AES_KEY_LENGTH = 256;

/**
 * GCM模式IV长度（字节）
 * 推荐12字节随机IV
 */
const IV_LENGTH_BYTES = 12;

/**
 * GCM认证标签长度（字节）
 */
const GCM_TAG_LENGTH_BYTES = 16;

/**
 * 生成随机AES-256密钥
 * @returns {Promise<string>} AES密钥对象（CryptoJS WordArray的Base64字符串）
 */
export async function generateAesKey() {
  // CryptoJS生成256位（32字节）随机密钥
  const key = CryptoJS.lib.WordArray.random(32); // 32字节 = 256位
  // 返回Base64编码的密钥字符串（用于存储和传输）
  return key.toString(CryptoJS.enc.Base64);
}

/**
 * 导出AES密钥为Base64字符串
 * 用于RSA加密传输
 * @param {string} key - AES密钥（Base64字符串）
 * @returns {Promise<string>} Base64编码的密钥字符串
 */
export async function exportAesKeyAsBase64(key) {
  // 如果已经是Base64字符串，直接返回
  return key;
}

/**
 * 从Base64字符串导入AES密钥
 * @param {string} base64Key - Base64编码的密钥字符串
 * @returns {Promise<string>} AES密钥对象（Base64字符串）
 */
export async function importAesKeyFromBase64(base64Key) {
  // 验证Base64格式
  try {
    CryptoJS.enc.Base64.parse(base64Key);
    return base64Key;
  } catch (error) {
    throw new Error(`无效的Base64密钥格式: ${error.message}`);
  }
}

/**
 * 将CryptoJS WordArray转换为Uint8Array
 * @param {CryptoJS.lib.WordArray} wordArray
 * @returns {Uint8Array}
 */
function wordArrayToUint8Array(wordArray) {
  const { words, sigBytes } = wordArray;
  const bytes = new Uint8Array(sigBytes);
  for (let i = 0; i < sigBytes; i++) {
    bytes[i] = (words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff;
  }
  return bytes;
}

/**
 * 将Uint8Array转换为node-forge所需的二进制字符串
 * @param {Uint8Array} uint8Array
 * @returns {string}
 */
function uint8ArrayToBinaryString(uint8Array) {
  let binary = '';
  for (let i = 0; i < uint8Array.length; i++) {
    binary += String.fromCharCode(uint8Array[i]);
  }
  return binary;
}

/**
 * 生成随机IV（初始化向量）
 * 每次加密必须使用新的IV，切勿重复使用
 * @returns {Uint8Array} 12字节的随机IV
 */
export function generateIv() {
  const ivWordArray = CryptoJS.lib.WordArray.random(IV_LENGTH_BYTES);
  return wordArrayToUint8Array(ivWordArray);
}

/**
 * AES-GCM加密
 * @param {string} key - AES密钥（Base64字符串）
 * @param {Uint8Array} iv - 初始化向量
 * @param {Object} data - 要加密的数据对象
 * @returns {Promise<{ciphertext: string, tag: string}>} 包含密文和标签的对象（均为Base64）
 */
export async function aesGcmEncrypt(key, iv, data) {
  if (!key) {
    throw new Error('AES密钥不能为空');
  }
  if (!iv || iv.length !== IV_LENGTH_BYTES) {
    throw new Error(`IV长度必须为${IV_LENGTH_BYTES}字节`);
  }

  try {
    const jsonString = typeof data === 'string' ? data : JSON.stringify(data);
    const keyBinary = forge.util.decode64(key);
    const ivBinary = uint8ArrayToBinaryString(iv);

    const cipher = forge.cipher.createCipher('AES-GCM', keyBinary);
    cipher.start({
      iv: ivBinary,
      tagLength: GCM_TAG_LENGTH_BYTES * 8,
    });
    cipher.update(forge.util.createBuffer(jsonString, 'utf8'));

    const success = cipher.finish();
    if (!success) {
      throw new Error('AES-GCM加密失败');
    }

    const ciphertext = forge.util.encode64(cipher.output.getBytes());
    const tag = forge.util.encode64(cipher.mode.tag.getBytes());

    return { ciphertext, tag };
  } catch (error) {
    throw new Error(`AES加密失败: ${error.message}`);
  }
}

/**
 * AES-GCM解密
 * @param {string} key - AES密钥（Base64字符串）
 * @param {string} ivBase64 - Base64编码的IV
 * @param {string} ciphertextBase64 - Base64编码的密文
 * @param {string} tagBase64 - Base64编码的认证标签
 * @returns {Promise<Object>} 解密后的数据对象
 */
export async function aesGcmDecrypt(key, ivBase64, ciphertextBase64, tagBase64) {
  if (!key) {
    throw new Error('AES密钥不能为空');
  }
  if (!ivBase64) {
    throw new Error('IV不能为空');
  }
  if (!ciphertextBase64) {
    throw new Error('密文不能为空');
  }
  if (!tagBase64) {
    throw new Error('认证标签不能为空');
  }

  try {
    const keyBinary = forge.util.decode64(key);
    const ivBytes = base64ToUint8Array(ivBase64);
    const ivBinary = uint8ArrayToBinaryString(ivBytes);
    const tagBinary = forge.util.decode64(tagBase64);
    const ciphertextBinary = forge.util.decode64(ciphertextBase64);

    const decipher = forge.cipher.createDecipher('AES-GCM', keyBinary);
    decipher.start({
      iv: ivBinary,
      tagLength: GCM_TAG_LENGTH_BYTES * 8,
      tag: tagBinary,
    });
    decipher.update(forge.util.createBuffer(ciphertextBinary));

    const success = decipher.finish();
    if (!success) {
      throw new Error('GCM标签验证失败，数据可能被篡改');
    }

    const decryptedString = decipher.output.toString('utf8');
    
    if (!decryptedString) {
      throw new Error('解密失败，可能是密钥或密文不正确');
    }
    
    // 解析JSON对象
    return JSON.parse(decryptedString || '{}');
  } catch (error) {
    throw new Error(`AES解密失败: ${error.message}`);
  }
}
