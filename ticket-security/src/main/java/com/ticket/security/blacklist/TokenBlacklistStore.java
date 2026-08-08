package com.ticket.security.blacklist;

import java.time.Duration;

/**
 * JWT 黑名单存储。
 * <p>
 * 单 token 方案下，登出无法"撤回"已签发的 JWT（无状态、自包含），
 * 因此把 token 的 {@code jti} 记入黑名单，认证过滤器每次请求校验一次
 * —— 这是 spec 里"登出时把 token 加入 blacklist"的落地点。
 * <p>
 * 抽成接口而非直接用 {@code StringRedisTemplate}，是为了让
 * {@link com.ticket.security.service.AuthService} 能被纯 Mockito 单测覆盖
 * （spec Seam 1），生产实现是 {@link RedisTokenBlacklistStore}。
 */
public interface TokenBlacklistStore {

    /**
     * 拉黑一个 token。
     *
     * @param jti token 唯一 ID
     * @param ttl 存活时长，取 token 的剩余寿命 —— token 自然过期后条目自动清理，
     *            Redis 不会无界增长（spec 里的定时清理任务是二次兜底，见 ticket 11）
     */
    void blacklist(String jti, Duration ttl);

    /** 是否已被拉黑 */
    boolean isBlacklisted(String jti);
}
