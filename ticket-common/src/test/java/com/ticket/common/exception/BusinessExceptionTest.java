package com.ticket.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BusinessException 单元测试。
 * <p>
 * 验证业务异常的契约：
 * <ul>
 *     <li>code 与 message 正确暴露</li>
 *     <li>code 是字符串（符合 ADR-0009 的 "T0101" 风格）</li>
 *     <li>继承 RuntimeException，无需显式捕获</li>
 *     <li>支持携带 cause</li>
 *     <li>基于 {@link BusinessExceptionCode} 的工厂方法按枚举生成 code</li>
 * </ul>
 */
class BusinessExceptionTest {

    @Test
    void constructor_with_code_message_exposes_both() {
        BusinessException ex = new BusinessException("T0101", "工单不存在");

        assertThat(ex.getCode()).isEqualTo("T0101");
        assertThat(ex.getMessage()).isEqualTo("工单不存在");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructor_with_code_message_cause_keeps_cause() {
        Throwable cause = new IllegalStateException("底层失败");
        BusinessException ex = new BusinessException("C0500", "系统异常", cause);

        assertThat(ex.getCode()).isEqualTo("C0500");
        assertThat(ex.getMessage()).isEqualTo("系统异常");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void is_runtime_exception_so_unchecked() {
        BusinessException ex = new BusinessException("C0400", "参数错误");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void factory_of_enum_uses_enum_code_and_default_message() {
        BusinessException ex = BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);

        assertThat(ex.getCode()).isEqualTo("T0101");
        assertThat(ex.getMessage()).isEqualTo("工单不存在");
    }

    @Test
    void factory_of_enum_with_message_overrides_default() {
        BusinessException ex = BusinessException.of(BusinessExceptionCode.INTERNAL_ERROR, "数据库连接失败");

        assertThat(ex.getCode()).isEqualTo("C0500");
        assertThat(ex.getMessage()).isEqualTo("数据库连接失败");
    }
}