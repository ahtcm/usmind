package com.usmind.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 请求体缓存包装：读取并缓存请求体前 MAX_BODY_BYTES 字节，controller 仍可正常读取。
 * 仅用于日志记录；multipart 请求体由过滤器层跳过（不进本包装）。
 */
public class CappedBodyRequestWrapper extends HttpServletRequestWrapper {

    public static final int MAX_BODY_BYTES = 64 * 1024;

    private final ByteArrayOutputStream cache = new ByteArrayOutputStream();
    private boolean readDone = false;

    public CappedBodyRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ServletInputStream original = super.getInputStream();
        return new ServletInputStream() {
            private boolean finished = false;

            @Override
            public int read() throws IOException {
                int b = original.read();
                if (b >= 0 && !readDone) {
                    if (cache.size() < MAX_BODY_BYTES) cache.write(b);
                    else readDone = true;
                } else if (b < 0) {
                    readDone = true;
                }
                return b;
            }

            @Override
            public boolean isFinished() {
                return finished;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                original.setReadListener(readListener);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n = original.read(b, off, len);
                if (n > 0 && !readDone) {
                    int room = MAX_BODY_BYTES - cache.size();
                    if (room > 0) cache.write(b, off, Math.min(n, room));
                    else readDone = true;
                } else if (n < 0) {
                    readDone = true;
                }
                return n;
            }

            @Override
            public void close() throws IOException {
                super.close();
                finished = true;
            }
        };
    }

    /** 返回缓存的请求体文本（UTF-8，可能截断）。未读取时为 null。 */
    public String cachedBody() {
        if (cache.size() == 0) return null;
        return cache.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
