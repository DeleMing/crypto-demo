# 接口加解密Demo - AES+RSA混合加密方案

本项目演示了前后端接口加解密的完整实现，使用AES-256-GCM和RSA混合加密方案。

## 项目结构

```
crypto-demo/
├── backend/                    # Spring Boot后端
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/lmt/crypto/
│   │       │       ├── DemoApplication.java
│   │       │       ├── config/              # 配置类（密钥加载、过滤器配置）
│   │       │       │   ├── KeyPairLoader.java
│   │       │       │   ├── CryptoConfig.java
│   │       │       │   └── FilterConfig.java
│   │       │       ├── util/                # 加解密工具类
│   │       │       │   ├── AesUtil.java     # AES-GCM加解密
│   │       │       │   └── RsaUtil.java     # RSA加解密
│   │       │       ├── filter/              # 请求解密过滤器
│   │       │       │   └── DecryptRequestFilter.java
│   │       │       ├── advice/              # 响应加密增强器
│   │       │       │   └── EncryptResponseAdvice.java
│   │       │       ├── controller/          # 控制器
│   │       │       │   └── CryptoController.java
│   │       │       ├── holder/              # ThreadLocal持有者
│   │       │       │   └── CryptoContextHolder.java
│   │       │       └── wrapper/             # 请求包装器
│   │       │           └── CachedBodyHttpServletRequest.java
│   │       └── resources/
│   │           ├── application.yml
│   │           └── crypto/                  # RSA密钥文件目录
│   │               ├── rsa_public.pem       # RSA公钥（PEM格式）
│   │               └── rsa_private_pkcs8.pem # RSA私钥（PKCS8格式）
│   └── pom.xml
│
└── frontend/                   # Vue前端
    ├── src/
    │   ├── api/
    │   │   └── crypto.js      # 加密相关API（获取公钥）
    │   ├── utils/
    │   │   ├── crypto/        # 加解密工具
    │   │   │   ├── aes.js     # AES-GCM加解密（使用node-forge）
    │   │   │   ├── rsa.js     # RSA加密（使用JSEncrypt）
    │   │   │   └── base64.js  # Base64工具
    │   │   └── request.js     # Axios配置（请求/响应拦截器）
    │   ├── views/
    │   │   └── TestPage.vue   # 测试页面
    │   ├── App.vue
    │   └── main.js
    ├── public/
    │   └── index.html
    ├── package.json
    └── vue.config.js
```

## 加密方案

### 技术选型

- **AES-256-GCM**：用于加密实际数据（请求体/响应体）
  - 密钥长度：256位（32字节）
  - IV长度：12字节（推荐长度）
  - 认证标签长度：128位（16字节）
  - 提供认证加密（AEAD），自带完整性校验
  - 后端使用Java标准库 `javax.crypto`
  - 前端使用 `node-forge` 库

- **RSA-PKCS1**：用于加密传输AES密钥
  - 密钥长度：2048位
  - 填充模式：PKCS1Padding（`RSA/ECB/PKCS1Padding`）
  - 仅加密短数据（AES密钥，转换为64字符十六进制字符串）
  - 后端使用Java标准库 `java.security`
  - 前端使用 `JSEncrypt` 库（兼容PEM格式公钥）
  - **注意**：由于JSEncrypt库限制，使用PKCS1填充而非OAEP（OAEP更安全但JSEncrypt不支持）

### 加密流程

#### 请求加密流程

1. 前端生成随机UUID作为请求ID
2. 前端生成随机AES-256密钥（32字节）和IV（12字节）
3. 前端从后端获取RSA公钥（PEM格式，支持缓存）
4. 前端将AES密钥转换为64字符十六进制字符串（因为JSEncrypt只能加密字符串）
5. 前端使用RSA公钥加密AES密钥（十六进制字符串），得到Base64编码的密文
6. 前端使用AES-GCM加密请求体JSON，得到密文和认证标签
7. 前端在Map中保存 `{requestId: {key: aesKeyBase64, expiresAt: timestamp}}`
8. 前端发送请求，携带请求头：
   - `X-Encrypt: true` - 标识请求已加密
   - `X-Req-Id: UUID` - 请求ID，用于关联响应
   - `X-Key: Base64(RSA加密的AES密钥十六进制字符串)` - RSA加密的AES密钥
   - `X-IV: Base64(IV)` - 初始化向量
   - `X-Tag: Base64(GCM认证标签)` - GCM认证标签
   - `Content-Type: text/plain` - 避免Axios自动JSON序列化
   - 请求体：Base64编码的密文（纯文本）

#### 请求解密流程

1. `DecryptRequestFilter` 检查 `X-Encrypt` 请求头，如果为 `true` 则进行解密
2. 从请求头提取：`X-Key`（RSA加密的AES密钥）、`X-IV`、`X-Tag`、`X-Req-Id`
3. 使用RSA私钥解密 `X-Key`，得到AES密钥的十六进制字符串
4. 将十六进制字符串转换为32字节的AES密钥字节数组
5. 使用AES-GCM解密请求体（同时验证GCM标签，确保数据完整性）
6. 将AES密钥和请求ID存入 `CryptoContextHolder`（ThreadLocal）
7. 使用 `CachedBodyHttpServletRequest` 包装请求，替换为解密后的JSON数据
8. 修改 `Content-Type` 为 `application/json`，传递给Controller

#### 响应加密流程

1. Controller处理业务逻辑，返回数据对象
2. `EncryptResponseAdvice` 检查 `CryptoContextHolder` 是否存在加密上下文
3. 如果存在，从ThreadLocal获取AES密钥和请求ID
4. 生成新的IV（**重要**：不能复用请求的IV）
5. 使用FastJSON将响应对象序列化为JSON字节数组
6. 使用AES-GCM加密响应JSON，得到密文和认证标签
7. 设置响应头：
   - `X-Encrypt: true` - 标识响应已加密
   - `X-Req-Id: UUID` - 回传请求ID，前端用于查找对应密钥
   - `X-IV-Resp: Base64(响应IV)` - 响应使用的IV
   - `X-Tag-Resp: Base64(响应GCM标签)` - 响应GCM认证标签
   - `Content-Type: text/plain;charset=UTF-8` - 响应体为Base64字符串
   - CORS相关头（`Access-Control-Expose-Headers`） - 暴露自定义响应头
8. 返回Base64编码的密文（纯文本）
9. 清理ThreadLocal，防止内存泄漏

#### 响应解密流程

1. Axios响应拦截器检查 `X-Encrypt` 响应头
2. 如果为 `true`，从响应头提取：`X-Req-Id`、`X-IV-Resp`、`X-Tag-Resp`
3. 使用请求ID从Map中查找对应的AES密钥（Base64字符串）
4. 如果找不到密钥，说明已过期或被清理，抛出错误
5. 使用AES-GCM解密响应体（Base64密文），同时验证GCM标签
6. 将解密后的JSON字符串解析为对象
7. 清理Map中的密钥记录（无论成功或失败）
8. 返回解密后的JSON对象给业务代码

### 并发请求管理

- 前端使用`requestId -> {aesKey, expiresAt}`的Map管理多个并发请求
- 每个请求使用独立的AES密钥和IV
- 密钥超时时间：60秒
- 定期清理过期密钥（每10秒）

## 后端运行

### 环境要求

- JDK 1.8+
- Maven 3.6+

### RSA密钥准备

项目需要RSA密钥对文件，放置在 `backend/src/main/resources/crypto/` 目录下：

- `rsa_public.pem` - RSA公钥（PEM格式，X.509 SubjectPublicKeyInfo）
- `rsa_private_pkcs8.pem` - RSA私钥（PEM格式，PKCS8编码）

**生成密钥对的方法**（使用OpenSSL）：

```bash
# 生成2048位RSA私钥（PKCS8格式）
openssl genpkey -algorithm RSA -out rsa_private_pkcs8.pem -pkcs8 -pkeyopt rsa_keygen_bits:2048

# 从私钥提取公钥
openssl rsa -in rsa_private_pkcs8.pem -pubout -out rsa_public.pem
```

**注意**：密钥文件会在应用启动时由 `KeyPairLoader` 自动加载。

### 启动步骤

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务将启动在：`http://localhost:8080`

### 可用接口

1. **获取RSA公钥**
   ```
   GET /api/crypto/public-key
   返回：PEM格式的RSA公钥（纯文本）
   ```

2. **测试RSA密钥对**
   ```
   GET /api/crypto/test-keypair
   返回：密钥对自验证结果（JSON）
   ```

3. **测试接口（支持加密/明文）**
   ```
   POST /api/test/echo
   请求体：JSON对象（userId, message, timestamp）
   返回：回显请求内容
   
   POST /api/test/user-info
   请求体：JSON对象（userId, message, timestamp）
   返回：处理后的用户信息
   
   GET /api/test/server-info
   返回：服务器信息（无需加密）
   ```

**接口加密说明**：
- 如果请求头包含 `X-Encrypt: true`，则请求会被自动解密，响应会被自动加密
- 如果请求头不包含 `X-Encrypt`，则按普通HTTP请求处理（明文传输）

## 前端运行

### 环境要求

- Node.js 14+
- npm 6+

### 安装依赖

```bash
cd frontend
npm install
```

### 启动开发服务器

```bash
npm run serve
```

前端应用将启动在：`http://localhost:8081`

### 生产构建

```bash
npm run build
```

## 快速开始

### 1. 准备RSA密钥对

```bash
cd backend/src/main/resources/crypto
# 生成私钥
openssl genpkey -algorithm RSA -out rsa_private_pkcs8.pem -pkcs8 -pkeyopt rsa_keygen_bits:2048
# 提取公钥
openssl rsa -in rsa_private_pkcs8.pem -pubout -out rsa_public.pem
```

### 2. 启动后端服务

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端将在 `http://localhost:8080` 启动

### 3. 启动前端服务

```bash
cd frontend
npm install
npm run serve
```

前端将在 `http://localhost:8081` 启动

### 4. 测试加密功能

打开浏览器访问 `http://localhost:8081`，在测试页面中：
- 输入消息内容和用户ID
- 点击"🔐 发送加密请求"测试加密功能
- 点击"📄 发送明文请求"测试明文功能
- 点击"ℹ️ 获取服务器信息"测试GET请求

## 前端使用示例

### 发送加密请求

```javascript
import request from '@/utils/request'

// 发送加密请求（自动加密请求体和响应体）
const response = await request({
  url: '/api/test/echo',
  method: 'POST',
  data: {
    userId: 123,
    message: 'Hello, Crypto!',
    timestamp: new Date().toISOString()
  },
  encrypt: true  // 关键：设置此标志启用加密
})

console.log('解密后的响应:', response.data)
```

### 发送明文请求

```javascript
// 发送明文请求（不加密）
const response = await request({
  url: '/api/test/server-info',
  method: 'GET'
  // 不设置 encrypt 或设置为 false
})

console.log('响应数据:', response.data)
```

### 手动加解密（高级用法）

```javascript
import { generateAesKey, aesGcmEncrypt, aesGcmDecrypt } from '@/utils/crypto/aes'
import { encryptWithRsaPublicKey } from '@/utils/crypto/rsa'
import { fetchPublicKey } from '@/api/crypto'

// 1. 获取RSA公钥
const publicKeyPem = await fetchPublicKey()

// 2. 生成AES密钥和IV
const aesKey = await generateAesKey()
const iv = generateIv()

// 3. 加密数据
const data = { userId: 123, message: 'Hello' }
const { ciphertext, tag } = await aesGcmEncrypt(aesKey, iv, data)

// 4. 加密AES密钥
const aesKeyHex = // ... 转换为十六进制
const encryptedAesKey = encryptWithRsaPublicKey(publicKeyPem, aesKeyHex)
```

## 安全特性

1. ✅ 使用AES-GCM认证加密（AEAD），提供完整性和机密性校验
2. ✅ RSA使用PKCS1填充（兼容JSEncrypt库），加密传输AES密钥
3. ✅ 每次请求生成新的AES密钥（32字节随机密钥），防止密钥重用
4. ✅ 每次加密使用新的IV（12字节随机IV），防止IV重用
5. ✅ 请求和响应使用不同的IV（响应使用 `X-IV-Resp`）
6. ✅ 使用 `SecureRandom`（后端）和 `CryptoJS.lib.WordArray.random`（前端）生成随机数
7. ✅ 密钥自动过期和清理机制（前端60秒TTL，每10秒清理一次）
8. ✅ ThreadLocal自动清理，防止内存泄漏
9. ✅ 完善的异常处理和错误提示（包含详细的错误码和消息）
10. ✅ GCM标签验证，确保数据未被篡改
11. ✅ CORS支持，暴露必要的响应头给前端

## 注意事项

1. **RSA密钥管理**：
   - RSA密钥对需要预先生成并放置在 `resources/crypto/` 目录
   - 生产环境建议使用密钥管理服务（KMS）或安全的密钥存储
   - 私钥必须严格保密，不应提交到代码仓库
   - 建议定期轮换密钥对

2. **RSA填充模式**：
   - 当前使用PKCS1Padding（`RSA/ECB/PKCS1Padding`）
   - 原因：JSEncrypt库不支持OAEP填充
   - **安全提示**：PKCS1填充存在选择密文攻击风险，但本项目中RSA仅用于加密短数据（AES密钥），风险可控
   - 如需更高安全性，可考虑使用支持OAEP的前端库（如 `node-forge` 的RSA实现）

3. **AES密钥传输格式**：
   - AES密钥（32字节）转换为64字符十六进制字符串后使用RSA加密
   - 后端解密后需要将十六进制字符串转换回字节数组
   - 确保前后端转换逻辑一致

4. **生产环境建议**：
   - ✅ 使用HTTPS传输，保护整个通信通道
   - ✅ RSA密钥应持久化存储，避免每次启动重新生成
   - ✅ 添加请求签名或时间戳，防止重放攻击
   - ✅ 添加请求频率限制，防止DoS攻击
   - ✅ 考虑实现会话级密钥复用，减少RSA操作（当前每次请求都生成新密钥）
   - ✅ 添加公钥指纹校验，防止中间人攻击
   - ✅ 实施密钥轮换策略
   - ✅ 添加请求日志和监控

5. **性能优化**：
   - AES-GCM硬件加速普遍可用，性能优异
   - RSA只加密短数据（64字符十六进制字符串），开销可控
   - 考虑对高频接口使用会话密钥（当前每次请求都生成新密钥）
   - 前端密钥缓存机制（内存+localStorage）减少公钥获取次数

6. **兼容性**：
   - 前端使用 `node-forge` 和 `crypto-js`，兼容现代浏览器
   - JSEncrypt需要浏览器支持，IE11+支持
   - 本地开发可使用HTTP（localhost），生产环境必须使用HTTPS

## 技术栈

### 后端
- **Spring Boot 2.6.11**
- **Java 8**
- **FastJSON 1.2.83** - JSON序列化/反序列化
- **Lombok** - 简化Java代码
- **Java标准库**：
  - `javax.crypto` - AES-GCM加解密
  - `java.security` - RSA加解密

### 前端
- **Vue 2.6.14**
- **Axios 1.6.0** - HTTP客户端
- **JSEncrypt 3.3.2** - RSA公钥加密（兼容PEM格式）
- **node-forge 1.3.1** - AES-GCM加解密
- **crypto-js 4.2.0** - 随机数生成和Base64工具
- **Vue CLI 5.0.8** - 构建工具

## 故障排查

### 常见问题

1. **RSA密钥加载失败**
   - 检查密钥文件是否存在于 `backend/src/main/resources/crypto/` 目录
   - 确认密钥文件格式正确（PEM格式，包含BEGIN/END标记）
   - 检查私钥是否为PKCS8格式（`-----BEGIN PRIVATE KEY-----`）

2. **前端无法获取公钥**
   - 检查后端服务是否正常启动（`http://localhost:8080`）
   - 检查浏览器控制台是否有CORS错误
   - 尝试清除浏览器缓存和localStorage中的公钥缓存

3. **解密失败：BadPaddingException**
   - 检查前后端使用的RSA密钥是否匹配
   - 确认前端使用的是PEM格式的公钥
   - 检查AES密钥的十六进制转换是否正确（64字符）

4. **GCM标签验证失败**
   - 检查IV是否正确（12字节）
   - 确认密文和标签没有被修改
   - 检查AES密钥是否正确

5. **响应解密失败：找不到密钥**
   - 检查请求ID是否正确传递
   - 确认密钥未过期（60秒TTL）
   - 检查前端密钥存储Map是否正常工作

### 调试技巧

1. **查看后端日志**：
   - `DecryptRequestFilter` 和 `EncryptResponseAdvice` 会输出详细的加解密日志
   - 关注密钥长度、Base64编码等关键信息

2. **查看前端控制台**：
   - 所有加密操作都有详细的 `[Crypto]` 日志
   - 检查密钥生成、加密、解密各步骤的输出

3. **测试RSA密钥对**：
   ```bash
   curl http://localhost:8080/api/crypto/test-keypair
   ```

## 许可证

MIT License
