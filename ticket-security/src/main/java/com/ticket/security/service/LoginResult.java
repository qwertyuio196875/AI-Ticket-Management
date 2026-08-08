package com.ticket.security.service;

/**
 * 登录成功的结果载荷（模块间传输用）。
 * <p>
 * 定义在 {@code ticket-security} 而非 {@code ticket-web}，是为了不让本模块
 * 反向依赖 Controller 层；{@code ticket-web} 再把它映射成对外的 {@code LoginVO}。
 * <p>
 * 刻意<b>不含密码哈希</b> —— 避免凭证材料泄漏到 Web 层。
 *
 * @param token     JWT 字符串
 * @param tokenType 固定 {@code "Bearer"}，前端拼 {@code Authorization} 头用
 * @param expiresIn 有效期（秒）
 * @param userId    sys_user.id
 * @param username  登录名
 * @param nickname  展示名
 */
public record LoginResult(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        String nickname
) {
}
