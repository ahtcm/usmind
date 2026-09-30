package com.usmind.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.Result;
import com.usmind.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.usmind.security.IpGuardFilter ipGuardFilter;
    private final com.usmind.filter.RequestLogFilter requestLogFilter;
    private final com.usmind.service.SettingsService settingsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          com.usmind.security.IpGuardFilter ipGuardFilter,
                          com.usmind.filter.RequestLogFilter requestLogFilter,
                          com.usmind.service.SettingsService settingsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.ipGuardFilter = ipGuardFilter;
        this.requestLogFilter = requestLogFilter;
        this.settingsService = settingsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // 跨域白名单（后台可配置 corsOrigins，逗号分隔；默认仅本机开发域名）
        String origins = settingsService.get("corsOrigins",
                "http://127.0.0.1:8866,http://127.0.0.1:8867,http://127.0.0.1:80,"
                        + "http://localhost:8866,http://localhost:8867,http://localhost:80");
        List<String> allowed = java.util.Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        cfg.setAllowedOrigins(allowed);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /** 请求日志过滤器：由 RequestLogFilterConfig 提供 Bean，仅注册一次（Security 链内） */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/admin-login", "/api/auth/register", "/api/auth/captcha", "/api/auth/send-code", "/api/auth/wechat/**").permitAll()
                        .requestMatchers("/api/log/report").permitAll()
                        .requestMatchers("/api/config/client", "/api/member/plans", "/api/pay/wechat/notify").permitAll()
                        .requestMatchers("/api/share/**").permitAll()
                        .requestMatchers("/api/themes", "/api/icons").permitAll()
                        .requestMatchers("/api/preview-config").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/preview/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余 /api/**（含 /api/file/** 上传/删除/预览）必须登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            // 只获取一次 writer；write() 不触发 PrintWriter autoFlush，
                            // 必须显式 flush 否则响应体停留在缓冲区丢失（客户端收到空 body）
                            java.io.PrintWriter w = response.getWriter();
                            w.write(new ObjectMapper().writeValueAsString(
                                    Result.error(401, "未登录或登录已过期")));
                            w.flush();
                        })
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            java.io.PrintWriter w = response.getWriter();
                            w.write(new ObjectMapper().writeValueAsString(
                                    Result.error(403, "没有权限")));
                            w.flush();
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(ipGuardFilter, com.usmind.security.JwtAuthenticationFilter.class)
                .addFilterAfter(requestLogFilter, com.usmind.security.IpGuardFilter.class);
        return http.build();
    }
}
