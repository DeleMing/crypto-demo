# 接口加解密Demo - AES+RSA混合加密方案

本项目演示了前后端接口加解密的完整实现，使用AES-256-GCM和RSA-OAEP混合加密方案。

## 项目结构

```
lmtDemo/
├── backend/           # Spring Boot后端
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/example/demo/
│   │       │       ├── DemoApplication.java
│   │       │       ├── config/        # 配置类
│   │       │       ├── util/          # 加解密工具类
│   │       │       ├── filter/        # 请求解密过滤器
│   │       │       ├── advice/        # 响应加密增强器
│   │       │       ├── controller/    # 控制器
│   │       │       ├── holder/        # ThreadLocal持有者
│   │       │       └── wrapper/       # 请求包装器
│   │       └── resources/
│   │           └── application.yml
│   └── pom.xml
│
└── frontend/          # Vue前端
    ├── src/
    │   ├── api/           # API接口
    │   ├── utils/
    │   │   ├── crypto/    # 加解密工具
    │   │   └── request.js # Axios配置
    │   ├── views/         # 页面组件
    │   ├── App.vue
    │   └── main.js
    ├── public/
    │   └── index.html
    └── package.json
```

## 加密方案

### 技术选型

- **AES-256-GCM**：用于加密实际数据（请求体/响应体）
  - 密钥长度：256位
  - IV长度：12字节
  - 认证标签长度：128位
  - 提供认证加密（AEAD），自带完整性校验

- **RSA-OAEP**：用于加密传输AES密钥
  - 密钥长度：2048位
  - 填充模式：OAEP with SHA-256
  - 仅加密短数据（AES密钥）

### 加密流程

#### 请求加密流程

1. 前端生成随机UUID作为请求ID
2. 前端生成随机AES-256密钥和IV（12字节）
3. 前端从后端获取RSA公钥
4. 前端使用RSA公钥加密AES密钥
5. 前端使用AES-GCM加密请求体JSON
6. 前端在Map中保存{requestId: {aesKey, timestamp}}
7. 前端发送请求，携带请求头：
   - `X-Encrypt: true`
   - `X-Req-Id: UUID`
   - `X-Key: Base64(RSA加密的AES key)`
   - `X-IV: Base64(IV)`
   - `X-Tag: Base64(GCM tag)`
   - 请求体：Base64(密文)

#### 请求解密流程

1. 后端Filter检查`X-Encrypt`头
2. 后端使用RSA私钥解密`X-Key`得到AES密钥
3. 后端使用AES-GCM解密请求体（同时验证tag）
4. 后端将AES密钥和请求ID存入ThreadLocal
5. 后端将解密后的数据传递给Controller

#### 响应加密流程

1. Controller处理业务逻辑，返回数据
2. ResponseBodyAdvice从ThreadLocal获取AES密钥
3. 生成新的IV（不能复用请求的IV）
4. 使用AES-GCM加密响应JSON
5. 设置响应头：
   - `X-Encrypt: true`
   - `X-Req-Id: UUID`（回传）
   - `X-IV-Resp: Base64(响应IV)`
   - `X-Tag-Resp: Base64(响应tag)`
   - 响应体：Base64(密文)
6. 清理ThreadLocal

#### 响应解密流程

1. 前端响应拦截器检查`X-Encrypt`头
2. 前端从`X-Req-Id`获取请求ID
3. 前端从Map中获取对应的AES密钥
4. 前端使用AES-GCM解密响应体
5. 前端清理Map中的密钥记录
6. 返回解密后的JSON对象

### 并发请求管理

- 前端使用`requestId -> {aesKey, expiresAt}`的Map管理多个并发请求
- 每个请求使用独立的AES密钥和IV
- 密钥超时时间：60秒
- 定期清理过期密钥（每10秒）

## 后端运行

### 环境要求

- JDK 1.8+
- Maven 3.6+

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
   ```

2. **测试接口（支持加密/明文）**
   ```
   POST /api/test/echo
   POST /api/test/user-info
   GET  /api/test/server-info
   ```

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

## 使用说明

1. 启动后端服务（端口8080）
2. 启动前端服务（端口8081）
3. 打开浏览器访问 `http://localhost:8081`
4. 在测试页面中：
   - 输入消息内容和用户ID
   - 点击"🔐 发送加密请求"测试加密功能
   - 点击"📄 发送明文请求"测试明文功能
   - 点击"ℹ️ 获取服务器信息"测试GET请求

## 安全特性

1. ✅ 使用AES-GCM认证加密，提供完整性校验
2. ✅ RSA使用OAEP填充，防止选择密文攻击
3. ✅ 每次请求生成新的AES密钥，防止密钥重用
4. ✅ 每次加密使用新的IV，防止IV重用
5. ✅ 请求和响应使用不同的IV
6. ✅ 使用SecureRandom生成随机数
7. ✅ 密钥自动过期和清理机制
8. ✅ 完善的异常处理和错误提示

## 注意事项

1. **生产环境建议**：
   - 使用HTTPS传输，保护整个通信通道
   - RSA密钥应持久化存储，避免每次启动重新生成
   - 添加请求签名或时间戳，防止重放攻击
   - 添加请求频率限制，防止DoS攻击
   - 考虑实现会话级密钥复用，减少RSA操作
   - 添加公钥指纹校验
   - 实施密钥轮换策略

2. **性能优化**：
   - AES-GCM硬件加速普遍可用，性能优异
   - RSA只加密短数据（AES密钥），开销可控
   - 考虑对高频接口使用会话密钥

3. **兼容性**：
   - 前端使用Web Crypto API，需要现代浏览器支持
   - 需要HTTPS环境（本地开发可使用localhost）

## 技术栈

### 后端
- Spring Boot 2.7.18
- Java 8
- Jackson（JSON序列化）

### 前端
- Vue 2.6.14
- Axios 1.6.0
- JSEncrypt 3.3.2（RSA加密）
- Web Crypto API（AES-GCM）

## 许可证

MIT License
