package com.usmind.config;

import com.usmind.common.JwtUtil;
import com.usmind.ws.MindMapWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket 注册：/ws 端点，握手时用 ?token= 校验 JWT 身份 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MindMapWsHandler mindMapWsHandler;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(MindMapWsHandler mindMapWsHandler, JwtUtil jwtUtil) {
        this.mindMapWsHandler = mindMapWsHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mindMapWsHandler, "/ws")
                .addInterceptors(new MindMapWsHandler.AuthHandshakeInterceptor(jwtUtil))
                .setAllowedOriginPatterns("*");
    }
}
