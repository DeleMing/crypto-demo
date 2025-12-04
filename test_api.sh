#!/bin/bash

echo "=== 测试接口加解密功能 ==="
echo ""

echo "1. 测试公钥接口..."
PUBLIC_KEY=$(curl -s http://localhost:8080/api/crypto/public-key)
if [ -n "$PUBLIC_KEY" ]; then
    echo "✅ 公钥获取成功 (${#PUBLIC_KEY} 字符)"
    echo "   公钥前100字符: ${PUBLIC_KEY:0:100}..."
else
    echo "❌ 公钥获取失败"
    exit 1
fi

echo ""
echo "2. 测试明文接口..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/test/echo \
    -H "Content-Type: application/json" \
    -d '{"message":"Hello","userId":"user123"}')

if echo "$RESPONSE" | grep -q "success"; then
    echo "✅ 明文接口测试成功"
    echo "   响应: $RESPONSE"
else
    echo "❌ 明文接口测试失败"
    echo "   响应: $RESPONSE"
fi

echo ""
echo "3. 测试GET接口..."
SERVER_INFO=$(curl -s http://localhost:8080/api/test/server-info)
if echo "$SERVER_INFO" | grep -q "Crypto Demo Server"; then
    echo "✅ GET接口测试成功"
    echo "   响应: $SERVER_INFO"
else
    echo "❌ GET接口测试失败"
fi

echo ""
echo "=== 测试完成 ==="
echo ""
echo "📌 前端访问地址: http://localhost:8081"
echo "📌 后端API地址: http://localhost:8080"
echo ""
echo "💡 请在浏览器中打开前端地址测试完整的加解密功能！"
