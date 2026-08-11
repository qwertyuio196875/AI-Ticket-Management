package com.ticket.ticket.enums;

/**
 * 工单评论类型（ticket 07，详见 ADR-0034）。
 * <p>
 * 持久化到 {@code ticket_comment.comment_type}：
 * <ul>
 *     <li>{@link #CUSTOMER}：客户发（由内部员工代发）</li>
 *     <li>{@link #AGENT}：客服 / 运维坐席发</li>
 *     <li>{@link #INTERNAL}：内部备注 —— 仅内部角色（admin / agent）可见，
 *         不外发客户</li>
 * </ul>
 * <p>
 * <b>校验</b>：Service 接收外部入参时用 {@link #fromValue(String)} 做白名单校验，
 * 非法值直接抛 {@code PARAM_INVALID}（{@code C0400}），避免恶意字符串污染 DB。
 */
public enum CommentType {

    CUSTOMER,
    AGENT,
    INTERNAL;

    /**
     * 按枚举名（大小写敏感）解析 —— DB 与 DTO 均直接传枚举名，解析失败抛参数异常。
     *
     * @param value 字符串值（{@code null} / 空串 / 非法值都视为非法）
     * @return 合法枚举值
     * @throws com.ticket.common.exception.BusinessException {@code PARAM_INVALID} 当 value 非法
     */
    public static CommentType fromValue(String value) {
        if (value == null || value.isEmpty()) {
            throw com.ticket.common.exception.BusinessException.of(
                    com.ticket.common.exception.BusinessExceptionCode.PARAM_INVALID,
                    "评论类型不能为空");
        }
        try {
            return CommentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw com.ticket.common.exception.BusinessException.of(
                    com.ticket.common.exception.BusinessExceptionCode.PARAM_INVALID,
                    "评论类型非法: " + value);
        }
    }

    /**
     * 当前评论是否仅内部可见。
     */
    public boolean isInternalOnly() {
        return this == INTERNAL;
    }
}
