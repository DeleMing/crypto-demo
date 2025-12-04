# 🎉 项目运行状态

## ✅ 运行状态总览

### 后端 (Spring Boot)
- **状态**: ✅ 运行中
- **地址**: http://localhost:8080
- **启动时间**: 2025-12-03 16:13:39
- **RSA密钥**: ✅ 已初始化 (2048位)
- **进程ID**: 47854

### 前端 (Vue)
- **状态**: ✅ 运行中
- **地址**: http://localhost:8081
- **本地访问**: http://localhost:8081/
- **网络访问**: http://192.168.100.103:8081/
- **编译状态**: ✅ 成功 (831ms)

---

## 🧪 接口测试结果

### 1. RSA公钥接口
```
GET /api/crypto/public-key
```
- **状态**: ✅ 成功
- **公钥长度**: 392字符
- **格式**: Base64编码的RSA-2048公钥

### 2. Echo测试接口（明文）
```
POST /api/test/echo
Content-Type: application/json

{"message":"Hello","userId":"user123"}
```
- **状态**: ✅ 成功
- **响应示例**:
```json
{
  "echo": {
    "message": "Hello",
    "userId": "user123"
  },
  "message": "请求处理成功",
  "status": "success",
  "timestamp": 1764750220629
}
```

### 3. 服务器信息接口
```
GET /api/test/server-info
```
- **状态**: ✅ 成功
- **响应示例**:
```json
{
  "serverName": "Crypto Demo Server",
  "version": "1.0.0",
  "cryptoEnabled": true,
  "timestamp": 1764750220655
}
```

---

## 🔐 加解密功能说明

### 请求头说明

#### 加密请求需要的请求头
```
X-Encrypt: true                    # 标识这是加密请求
X-Req-Id: [UUID]                   # 请求唯一ID
X-Key: [Base64]                    # RSA加密的AES密钥
X-IV: [Base64]                     # AES-GCM的IV（12字节）
X-Tag: [Base64]                    # GCM认证标签（16字节）
Content-Type: text/plain           # 请求体为Base64密文
```

#### 加密响应的响应头
```
X-Encrypt: true                    # 标识这是加密响应
X-Req-Id: [UUID]                   # 回传的请求ID
X-IV-Resp: [Base64]                # 响应使用的IV
X-Tag-Resp: [Base64]               # 响应的GCM认证标签
Content-Type: text/plain           # 响应体为Base64密文
```

### 加密流程

1. **前端请求加密**:
   - 生成随机AES-256密钥
   - 生成随机IV（12字节）
   - 用RSA公钥加密AES密钥
   - 用AES-GCM加密请求JSON
   - 发送加密数据和头部

2. **后端请求解密**:
   - 读取加密头部
   - 用RSA私钥解密AES密钥
   - 用AES-GCM解密请求体
   - 传递给业务逻辑

3. **后端响应加密**:
   - 获取请求中的AES密钥
   - 生成新的IV（不复用请求IV）
   - 用AES-GCM加密响应JSON
   - 返回加密数据和头部

4. **前端响应解密**:
   - 从请求ID映射获取AES密钥
   - 用AES-GCM解密响应体
   - 返回解密后的JSON对象

---

## 🎨 前端测试页面

打开浏览器访问: **http://localhost:8081**

### 功能特性
- ✅ 精美的渐变背景UI
- ✅ 三种测试按钮：
  - 🔐 发送加密请求
  - 📄 发送明文请求
  - ℹ️ 获取服务器信息
- ✅ 实时显示请求/响应数据
- ✅ 错误处理和提示
- ✅ JSON格式化显示

### 使用方法
1. 输入消息内容和用户ID
2. 点击"🔐 发送加密请求"测试加密功能
3. 点击"📄 发送明文请求"测试明文功能
4. 查看实时结果显示

---

## 📊 技术栈

### 后端
- Spring Boot 2.7.18
- Java 8+
- AES-256-GCM加密
- RSA-2048 PKCS1填充
- Jackson JSON

### 前端
- Vue 2.6.14
- Axios 1.6.0
- JSEncrypt 3.3.2 (RSA)
- Web Crypto API (AES-GCM)

---

## 🛠️ 管理命令

### 查看进程
```bash
# 查看后端进程
ps aux | grep crypto-demo

# 查看前端进程
ps aux | grep vue-cli-service
```

### 停止服务
```bash
# 停止后端（PID 47854）
kill 47854

# 停止前端
# 在前端终端按 Ctrl+C
```

### 重新启动
```bash
# 后端
cd backend
java -jar target/crypto-demo-1.0.0.jar

# 前端
cd frontend
npm run serve
```

---

## 📝 测试日志

生成时间: 2025-12-03 16:24:00

所有接口测试通过 ✅
前后端运行正常 ✅
准备就绪，可以开始使用！ 🚀
