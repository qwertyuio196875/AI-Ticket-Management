package com.ticket.ticket.task;

import com.ticket.ticket.entity.TaskExecutionLog;
import com.ticket.ticket.enums.TaskExecutionStatus;
import com.ticket.ticket.mapper.TaskExecutionLogMapper;
import com.ticket.ticket.task.TaskExecutionRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JwtBlacklistCleanupTask} 单元测试（ticket 11 AC）。
 * <p>
 * 覆盖 spec 验收点：
 * <ul>
 *     <li>用 Redis SCAN（{@code StringRedisTemplate.scan}）而不是 KEYS</li>
 *     <li>扫描到 key 后调用 {@code redisTemplate.delete(key)}</li>
 *     <li>正常完成 → task_execution_log 落 SUCCESS 行（含 start_time / end_time）</li>
 *     <li>SCAN 抛异常 → task_execution_log 落 FAILED 行（error_message 非空）</li>
 *     <li>扫描结果为空 → 不调 delete、不抛错、仍落 SUCCESS 日志</li>
 * </ul>
 *
 * <p><b>不在范围</b>：{@code RedisTokenBlacklistStore} 自身的 TTL 行为（ticket 02 已测），
 * 以及任务 cron 表达式（启动后 Spring 框架按 cron 触发，本测试手动调 {@code run()}）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtBlacklistCleanupTaskTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock TaskExecutionLogMapper taskExecutionLogMapper;
    @Mock Cursor<String> cursor;

    /**
     * 构造一个能枚举若干 key 的 {@link Cursor} mock。
     * <p>
     * Cursor 是 closeable + Iterable，Mockito 直接 mock 起来繁琐。
     * 使用 {@code doReturn().when(...).method()} 风格避免在嵌套 mock 工厂里
     * 触发 Mockito "unfinished stubbing" 检测（{@code when().thenAnswer(...)}
     * 嵌套对 Mockito 状态机不友好）。
     */
    private static Cursor<String> cursorOf(String... keys) {
        @SuppressWarnings("unchecked")
        Cursor<String> c = org.mockito.Mockito.mock(Cursor.class);
        Iterator<String> it = java.util.Arrays.asList(keys).iterator();
        org.mockito.Mockito.doAnswer(inv -> it.hasNext()).when(c).hasNext();
        org.mockito.Mockito.doAnswer(inv -> it.next()).when(c).next();
        return c;
    }

    @Test
    void run_scans_blacklist_prefix_and_batch_deletes_keys() {
        // SCAN 返回 3 个 key
        Cursor<String> c = cursorOf("jwt:blacklist:jti-1", "jwt:blacklist:jti-2", "jwt:blacklist:jti-3");
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(c);

        JwtBlacklistCleanupTask task = new JwtBlacklistCleanupTask(stringRedisTemplate, new TaskExecutionRecorder(taskExecutionLogMapper));
        task.run();

        // SCAN 用的是带 MATCH "jwt:blacklist:*" 的 ScanOptions，而不是 KEYS
        ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(stringRedisTemplate, times(1)).scan(optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getPattern()).isEqualTo("jwt:blacklist:*");
        assertThat(optionsCaptor.getValue().getCount()).isPositive();

        // 3 个 key 被一次批量 DEL（cursor 关闭后），避免 SCAN 迭代中修改 key 的语义陷阱
        ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(stringRedisTemplate).delete(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue())
                .containsExactlyInAnyOrder("jwt:blacklist:jti-1", "jwt:blacklist:jti-2", "jwt:blacklist:jti-3");
    }

    @Test
    void run_empty_scan_does_not_call_delete_but_logs_success() {
        // 显式取出 cursor mock，避免 when(...).thenReturn(cursorOf(...)) 嵌套调用触发 Mockito 状态机异常
        Cursor<String> emptyCursor = cursorOf();
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(emptyCursor);

        JwtBlacklistCleanupTask task = new JwtBlacklistCleanupTask(stringRedisTemplate, new TaskExecutionRecorder(taskExecutionLogMapper));
        task.run();

        verify(stringRedisTemplate, never()).delete((String) any());
        // 仍然写一条 SUCCESS 日志（start + end 都填）
        ArgumentCaptor<TaskExecutionLog> captor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(taskExecutionLogMapper, times(1)).insert(captor.capture());
        TaskExecutionLog row = captor.getValue();
        assertThat(row.getTaskName()).isEqualTo("jwtBlacklistCleanupTask");
        assertThat(row.getStatus()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(row.getErrorMessage()).isNull();
        assertThat(row.getStartTime()).isBeforeOrEqualTo(row.getEndTime());
    }

    @Test
    void run_when_scan_throws_logs_failed_with_error_message() {
        when(stringRedisTemplate.scan(any(ScanOptions.class)))
                .thenThrow(new RuntimeException("Redis 连接失败"));

        JwtBlacklistCleanupTask task = new JwtBlacklistCleanupTask(stringRedisTemplate, new TaskExecutionRecorder(taskExecutionLogMapper));
        // 必须捕获异常 —— 定时任务失败不能让进程挂掉（见 ADR-0031）
        task.run();

        ArgumentCaptor<TaskExecutionLog> captor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(taskExecutionLogMapper, times(1)).insert(captor.capture());
        TaskExecutionLog row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(row.getErrorMessage()).contains("Redis 连接失败");
        assertThat(row.getStartTime()).isBeforeOrEqualTo(row.getEndTime());
    }

    @Test
    void run_does_not_call_redis_keys_only_scan() {
        // 显式断言：禁止 KEYS 命令（生产 KEYS 会阻塞 Redis）
        Cursor<String> emptyCursor = cursorOf();
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(emptyCursor);

        JwtBlacklistCleanupTask task = new JwtBlacklistCleanupTask(stringRedisTemplate, new TaskExecutionRecorder(taskExecutionLogMapper));
        task.run();

        verify(stringRedisTemplate, never()).keys((String) any());
    }

    @Test
    void getTaskName_returns_bean_name() {
        JwtBlacklistCleanupTask task = new JwtBlacklistCleanupTask(stringRedisTemplate, new TaskExecutionRecorder(taskExecutionLogMapper));
        assertThat(task.getTaskName()).isEqualTo("jwtBlacklistCleanupTask");
    }
}