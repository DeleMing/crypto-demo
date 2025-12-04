package com.lmt.crypto.wrapper;

import lombok.Getter;
import org.springframework.util.StreamUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 可缓存请求体的HttpServletRequest包装器
 * 用于支持多次读取请求体
 * <p>
 * 背景：HttpServletRequest的输入流只能读取一次
 * 在Filter中解密请求体后，需要将解密后的内容传递给Controller
 * 因此需要包装请求，使其支持重复读取
 *
 * @author mingtao_liao
 */
@Getter
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /**
     * 缓存的请求体字节数组
     * -- GETTER --
     * 获取缓存的请求体字节数组
     * 主要用于调试和日志记录
     * <p>
     * 请求体字节数组
     */
    private final byte[] cachedBody;

    /**
     * 构造函数 - 使用提供的字节数组作为请求体
     *
     * @param request 原始请求对象
     * @param body    要缓存的请求体字节数组
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.cachedBody = body != null ? body : new byte[0];
    }

    /**
     * 构造函数 - 从原始请求中读取请求体
     *
     * @param request 原始请求对象
     * @throws IOException 读取请求体失败时抛出
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * 返回可重复读取的输入流
     * 每次调用都返回一个新的ByteArrayInputStream，指向同一个字节数组
     *
     * @return 可重复读取的ServletInputStream
     */
    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 不支持异步读取
                throw new UnsupportedOperationException("不支持异步读取");
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    /**
     * 返回字符编码
     * 统一使用UTF-8编码
     */
    @Override
    public String getCharacterEncoding() {
        return StandardCharsets.UTF_8.name();
    }

}
