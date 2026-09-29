package com.usmind.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 响应体缓存包装：缓存前 MAX_BODY_BYTES 字节用于日志，其余透传。
 * 大文件（图片/视频/PDF 等）只占用极小缓存，内存安全。
 */
public class CappedBodyResponseWrapper extends HttpServletResponseWrapper {

    public static final int MAX_BODY_BYTES = 64 * 1024;

    private final ByteArrayOutputStream cache = new ByteArrayOutputStream();
    private ServletOutputStream out;
    private PrintWriter writer;

    public CappedBodyResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) throw new IllegalStateException("getWriter() has already been called");
        if (out == null) {
            final ServletOutputStream original = getResponse().getOutputStream();
            out = new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return original.isReady();
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    original.setWriteListener(writeListener);
                }

                @Override
                public void write(int b) throws IOException {
                    if (cache.size() < MAX_BODY_BYTES) cache.write(b);
                    original.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    int room = MAX_BODY_BYTES - cache.size();
                    if (room > 0) cache.write(b, off, Math.min(len, room));
                    original.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    original.flush();
                }

                @Override
                public void close() throws IOException {
                    original.close();
                }
            };
        }
        return out;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (out != null) throw new IllegalStateException("getOutputStream() has already been called");
        if (writer == null) {
            // autoFlush=true：安全认证入口点等使用 getWriter().write() 后不手动 flush，
            // 若不 autoFlush 会导致响应体停留在 PrintWriter 缓冲区而丢失（客户端收到空 body）
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8), true);
        }
        return writer;
    }

    /** 返回缓存的响应体文本（UTF-8，可能截断）。未写入时为 null。 */
    public String cachedBody() {
        if (cache.size() == 0) return null;
        return cache.toString(StandardCharsets.UTF_8);
    }
}
