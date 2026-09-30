package com.usmind.config;

import com.usmind.common.JwtUtil;
import com.usmind.filter.RequestLogFilter;
import com.usmind.mapper.RequestLogMapper;
import com.usmind.service.SettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 请求日志过滤器 Bean 定义。
 * 独立配置类避免与 SecurityConfig 形成循环依赖；
 * 只注册为 Security 链内过滤器（不标注 @Component，防止容器级自动注册造成双重包装）。
 */
@Configuration
public class RequestLogFilterConfig {

    @Bean
    public RequestLogFilter requestLogFilter(JwtUtil jwtUtil, RequestLogMapper requestLogMapper,
                                             SettingsService settingsService) {
        return new RequestLogFilter(jwtUtil, requestLogMapper, settingsService);
    }
}
