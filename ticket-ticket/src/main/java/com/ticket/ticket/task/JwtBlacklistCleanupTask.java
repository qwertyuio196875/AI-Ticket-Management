package com.ticket.ticket.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 清理过期 JWT 黑名单条目（ticket 11 / ADR-0031）。
 * <p>
 * <b>背景</b>：{@code RedisTokenBlacklistStore} 写入黑名单时已设置 TTL = token 剩余寿命，
 * Redis 会自动回收。但作为防御性 housekeeping，每天凌晨 3 点 SCAN 一次并显式删除：
 * <ul>
 *     <li>防止历史残留（早期 ticket 02 之前未带 TTL 的条目）</li>
 *     <li>提供 SCAN 演练，便于后续运维排查</li>
 *     <li>任务执行日志（{@code task_execution_log}）作为运营观测数据</li>
 * </ul>
 *
 * <p><b>为什么用 SCAN 而不是 KEYS？</b>
 * <ul>
 *     <li>KEYS 是 O(N) 阻塞命令，会冻结 Redis 主线程；SCAN 是游标式增量迭代，
 *         COUNT 控制在 500 条 / 轮，不会阻塞生产 Redis</li>
 *     <li>KEYS 没有游标概念，无法分批处理；SCAN 可与业务高峰共存</li>
 * </ul>
 *
 * <p><b>SCAN 期间删除的安全处理</b>：Redis 文档明确警告 SCAN 迭代中修改 key
 * （{@code DEL} / {@code SET}）可能导致元素重复返回或漏返。本实现先全量
 * 把 keys 收集到本地 {@link List}，<b>关闭 cursor 后</b> 再批量删除 —— 保证
 * 删除集合与 SCAN 结果一致，计数准确。
 *
 * <p><b>失败语义</b>：housekeeping 任务单次失败不应杀死调度链路，故
 * {@link TaskExecutionRecorder#record} 传 {@code rethrowOnFailure=false}
 * —— 失败只写 {@code task_execution_log(status=FAILED)}，不抛出。
 */
@Component
public class JwtBlacklistCleanupTask {

    /** Redis blacklist key 前缀 —— 必须与 {@code RedisTokenBlacklistStore} 保持一致 */
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    /** SCAN 单次迭代的 key 数（性能 / 阻塞时间的折中） */
    private static final long SCAN_BATCH_SIZE = 500L;

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistCleanupTask.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final TaskExecutionRecorder recorder;

    public JwtBlacklistCleanupTask(StringRedisTemplate stringRedisTemplate,
                                   TaskExecutionRecorder recorder) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.recorder = recorder;
    }

    /**
     * 每天凌晨 3 点执行一次（cron = "秒 分 时 日 月 周"）。
     * <p>
     * 调度由 {@code @EnableScheduling} 开启（main 类），本类只声明触发时机。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledRun() {
        run();
    }

    /**
     * 业务入口（公开方便测试与手动调用）。
     * <p>
     * 执行 + 落 {@code task_execution_log} 统一交给 {@link TaskExecutionRecorder}；
     * 失败不抛出（{@code rethrowOnFailure=false}），详见类 Javadoc。
     */
    public void run() {
        recorder.record(getTaskName(), false, this::scanAndDelete);
    }

    /**
     * 两阶段 SCAN + DELETE 实现：
     * <ol>
     *     <li>SCAN 阶段：游标迭代到 isClosed，把所有匹配的 key 收集到 {@code List}</li>
     *     <li>DELETE 阶段：cursor 已关闭后，对 {@code List} 批量 {@code DEL}
     *         —— 避免 Redis "SCAN 中修改 key" 语义陷阱</li>
     * </ol>
     *
     * @return 实际删除的 key 数
     */
    int scanAndDelete() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(BLACKLIST_KEY_PREFIX + "*")
                .count(SCAN_BATCH_SIZE)
                .build();

        // 阶段 1: 收集 keys
        List<String> keys;
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            keys = new ArrayList<>();
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (keys.isEmpty()) {
            return 0;
        }

        // 阶段 2: 批量删除（cursor 已 close，SCAN 不会再返回这些 key）
        Long deleted = stringRedisTemplate.delete(keys);
        log.info("JwtBlacklistCleanupTask 完成，删除 {} 个黑名单 key", deleted);
        return deleted == null ? 0 : deleted.intValue();
    }

    /**
     * 暴露给测试 / 运维观测：任务 Bean 名。
     */
    public String getTaskName() {
        return "jwtBlacklistCleanupTask";
    }
}