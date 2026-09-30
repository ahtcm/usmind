package com.usmind.config;

import com.usmind.common.JwtUtil;
import com.usmind.mapper.MindMapMapper;
import com.usmind.ws.CommentWsHandler;
import com.usmind.ws.MindMapWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket 注册：/ws 端点，握手时用 ?token= 校验 JWT 身份；/ws/comment 为评论实时推送通道（作者 JWT 或游客 shareToken） */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MindMapWsHandler mindMapWsHandler;
    private final CommentWsHandler commentWsHandler;
    private final JwtUtil jwtUtil;
    private final MindMapMapper mindMapMapper;

    public WebSocketConfig(MindMapWsHandler mindMapWsHandler, CommentWsHandler commentWsHandler,
                           JwtUtil jwtUtil, MindMapMapper mindMapMapper) {
        this.mindMapWsHandler = mindMapWsHandler;
        this.commentWsHandler = commentWsHandler;
        this.jwtUtil = jwtUtil;
        this.mindMapMapper = mindMapMapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mindMapWsHandler, "/ws")
                .addInterceptors(new MindMapWsHandler.AuthHandshakeInterceptor(jwtUtil))
                .setAllowedOriginPatterns("*");
        registry.addHandler(commentWsHandler, "/ws/comment")
                .addInterceptors(new CommentWsHandler.CommentHandshakeInterceptor(jwtUtil, mindMapMapper))
                .setAllowedOriginPatterns("*");
    }
}
