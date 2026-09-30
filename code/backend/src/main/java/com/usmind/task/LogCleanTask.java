package com.usmind.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.entity.RequestLog;
import com.usmind.mapper.RequestLogMapper;
import com.usmind.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 请求日志定时清理任务：每天在后台配置的时间（logCleanTime，默认 03:00）执行一次，
 * 删除超过 logRetainDays（默认 90）天的日志记录。
 * 实现：每分钟轮询一次（fixedDelay=60s），命中配置时间且当天未执行过才清理，避免复杂 cron 动态拼接。
 */
@Slf4j
@Component
public class LogCleanTask {

    private final RequestLogMapper requestLogMapper;
    private final SettingsService settingsService;

    public LogCleanTask(RequestLogMapper requestLogMapper, SettingsService settingsService) {
        this.requestLogMapper = requestLogMapper;
        this.settingsService = settingsService;
    }

    private volatile String lastCleanDay = "";

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void clean() {
        try {
            String time = settingsService.get("logCleanTime", "03:00");
            String today = LocalDate.now().toString();
            if (lastCleanDay.equals(today)) return;
            // 校验时间格式 HH:mm
            String[] parts = time == null ? new String[]{"03", "00"} : time.trim().split(":");
            if (parts.length != 2) return;
            int hour;
            int minute;
            try {
                hour = Integer.parseInt(parts[0].trim());
                minute = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return;
            }
            LocalTime now = LocalTime.now();
            if (now.isBefore(LocalTime.of(hour, minute))) return;
            lastCleanDay = today;

            int retainDays = settingsService.getInt("logRetainDays", 90);
            if (retainDays <= 0) retainDays = 90;
            LocalDateTime deadline = LocalDate.now().minusDays(retainDays).atStartOfDay();
            int removed = requestLogMapper.delete(new LambdaQueryWrapper<RequestLog>()
                    .lt(RequestLog::getRequestAt, deadline));
            if (removed > 0) {
                log.info("请求日志清理完成：删除 {} 条 {} 天前的日志", removed, retainDays);
            }
        } catch (Exception e) {
            log.warn("请求日志清理任务异常: {}", e.getMessage());
        }
    }
}
