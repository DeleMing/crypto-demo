<template>
  <div class="test-page">
    <div class="container">
      <h1>接口加解密测试</h1>
      <p class="description">此页面用于测试前后端AES+RSA混合加解密功能</p>

      <div class="form-section">
        <h2>测试数据</h2>
        <div class="form-group">
          <label for="message">消息内容：</label>
          <input
            id="message"
            v-model="message"
            type="text"
            placeholder="请输入要发送的消息"
            class="form-control"
          />
        </div>

        <div class="form-group">
          <label for="userId">用户ID：</label>
          <input
            id="userId"
            v-model="userId"
            type="text"
            placeholder="请输入用户ID"
            class="form-control"
          />
        </div>
      </div>

      <div class="action-section">
        <h2>操作</h2>
        <button @click="sendEncryptedRequest" class="btn btn-primary" :disabled="loading">
          <span v-if="loading">处理中...</span>
          <span v-else>🔐 发送加密请求</span>
        </button>
        <button @click="sendPlainRequest" class="btn btn-secondary" :disabled="loading">
          <span v-if="loading">处理中...</span>
          <span v-else>📄 发送明文请求</span>
        </button>
        <button @click="getServerInfo" class="btn btn-info" :disabled="loading">
          <span v-if="loading">处理中...</span>
          <span v-else>ℹ️ 获取服务器信息</span>
        </button>
      </div>

      <div class="result-section" v-if="showResult">
        <h2>结果</h2>
        <div class="result-box" :class="{ success: isSuccess, error: !isSuccess }">
          <div class="result-item">
            <strong>请求类型：</strong>
            <span>{{ requestType }}</span>
          </div>
          <div class="result-item">
            <strong>请求数据：</strong>
            <pre>{{ formatJson(requestData) }}</pre>
          </div>
          <div class="result-item" v-if="isSuccess">
            <strong>响应数据：</strong>
            <pre>{{ formatJson(responseData) }}</pre>
          </div>
          <div class="result-item" v-if="!isSuccess">
            <strong>错误信息：</strong>
            <span class="error-message">{{ errorMessage }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import request from '../utils/request';

export default {
  name: 'TestPage',
  data() {
    return {
      message: '',
      userId: '',
      loading: false,
      showResult: false,
      isSuccess: false,
      requestType: '',
      requestData: null,
      responseData: null,
      errorMessage: '',
    };
  },
  methods: {
    /**
     * 发送加密请求
     */
    async sendEncryptedRequest() {
      this.loading = true;
      this.showResult = false;

      try {
        const payload = {
          message: this.message || 'Hello, Encrypted World!',
          userId: this.userId || '123',
          timestamp: Date.now(),
        };

        this.requestType = '加密请求 (AES+RSA)';
        this.requestData = payload;

        // config.encrypt = true 表示需要加密
        const response = await request.post('/api/test/echo', payload, {
          encrypt: true,
        });

        this.isSuccess = true;
        this.responseData = response.data;
        this.showResult = true;
      } catch (error) {
        this.isSuccess = false;
        this.errorMessage = error.message || '请求失败';
        this.showResult = true;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 发送明文请求
     */
    async sendPlainRequest() {
      this.loading = true;
      this.showResult = false;

      try {
        const payload = {
          message: this.message || 'Hello, Plain World!',
          userId: this.userId || '456',
          timestamp: Date.now(),
        };

        this.requestType = '明文请求 (无加密)';
        this.requestData = payload;

        // config.encrypt = false 或不设置，表示不加密
        const response = await request.post('/api/test/echo', payload, {
          encrypt: false,
        });

        this.isSuccess = true;
        this.responseData = response.data;
        this.showResult = true;
      } catch (error) {
        this.isSuccess = false;
        this.errorMessage = error.message || '请求失败';
        this.showResult = true;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 获取服务器信息
     */
    async getServerInfo() {
      this.loading = true;
      this.showResult = false;

      try {
        this.requestType = 'GET请求 (无加密)';
        this.requestData = { method: 'GET', url: '/api/test/server-info' };

        const response = await request.get('/api/test/server-info');

        this.isSuccess = true;
        this.responseData = response.data;
        this.showResult = true;
      } catch (error) {
        this.isSuccess = false;
        this.errorMessage = error.message || '请求失败';
        this.showResult = true;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 格式化JSON显示
     */
    formatJson(data) {
      if (!data) return '';
      return JSON.stringify(data, null, 2);
    },
  },
};
</script>

<style scoped>
.test-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
}

.container {
  max-width: 800px;
  margin: 0 auto;
  background: white;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

h1 {
  color: #333;
  margin-bottom: 8px;
  font-size: 28px;
}

h2 {
  color: #555;
  font-size: 20px;
  margin-top: 32px;
  margin-bottom: 16px;
  border-bottom: 2px solid #667eea;
  padding-bottom: 8px;
}

.description {
  color: #666;
  margin-bottom: 24px;
}

.form-section {
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #555;
  font-weight: 500;
}

.form-control {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.form-control:focus {
  outline: none;
  border-color: #667eea;
}

.action-section {
  margin-bottom: 24px;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  margin-right: 12px;
  margin-bottom: 12px;
  transition: all 0.3s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #667eea;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #5568d3;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-secondary {
  background: #48bb78;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #38a169;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(72, 187, 120, 0.4);
}

.btn-info {
  background: #4299e1;
  color: white;
}

.btn-info:hover:not(:disabled) {
  background: #3182ce;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(66, 153, 225, 0.4);
}

.result-section {
  margin-top: 32px;
}

.result-box {
  padding: 20px;
  border-radius: 8px;
  border: 2px solid #ddd;
}

.result-box.success {
  background: #f0fdf4;
  border-color: #48bb78;
}

.result-box.error {
  background: #fef2f2;
  border-color: #f56565;
}

.result-item {
  margin-bottom: 16px;
}

.result-item:last-child {
  margin-bottom: 0;
}

.result-item strong {
  display: block;
  margin-bottom: 8px;
  color: #333;
}

.result-item pre {
  background: #f7fafc;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #2d3748;
}

.error-message {
  color: #e53e3e;
  font-weight: 500;
}
</style>
