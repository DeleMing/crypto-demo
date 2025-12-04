/**
 * ArrayBuffer与Base64转换工具
 * 用于在Web Crypto API的ArrayBuffer和Base64字符串之间转换
 */

/**
 * 将ArrayBuffer转换为Base64字符串
 * @param {ArrayBuffer} buffer - 要转换的ArrayBuffer
 * @returns {string} Base64编码的字符串
 */
export function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/**
 * 将Base64字符串转换为ArrayBuffer
 * @param {string} base64 - Base64编码的字符串
 * @returns {ArrayBuffer} 转换后的ArrayBuffer
 */
export function base64ToArrayBuffer(base64) {
  const binary = atob(base64);
  const length = binary.length;
  const buffer = new ArrayBuffer(length);
  const bytes = new Uint8Array(buffer);

  for (let i = 0; i < length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }

  return buffer;
}

/**
 * 将Uint8Array转换为Base64字符串
 * @param {Uint8Array} uint8Array - 要转换的Uint8Array
 * @returns {string} Base64编码的字符串
 */
export function uint8ArrayToBase64(uint8Array) {
  return arrayBufferToBase64(uint8Array.buffer);
}

/**
 * 将Base64字符串转换为Uint8Array
 * @param {string} base64 - Base64编码的字符串
 * @returns {Uint8Array} 转换后的Uint8Array
 */
export function base64ToUint8Array(base64) {
  return new Uint8Array(base64ToArrayBuffer(base64));
}
