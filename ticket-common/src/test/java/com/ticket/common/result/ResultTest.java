package com.ticket.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Result<T> 单元测试。
 * <p>
 * 验证公共返回包装类的基础契约：
 * <ul>
 *     <li>{@code success} 产出 {@code code="200"}、{@code message="success"}、data 非 null</li>
 *     <li>{@code error} 产出指定 code / message、data 为 null</li>
 *     <li>getter 可正确返回字段</li>
 * </ul>
 */
class ResultTest {

    @Test
    void success_with_data_returns_code_200_and_data() {
        Result<String> result = Result.success("pong");

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("pong");
    }

    @Test
    void error_with_code_and_message_returns_null_data() {
        Result<Void> result = Result.error("T0101", "工单不存在");

        assertThat(result.getCode()).isEqualTo("T0101");
        assertThat(result.getMessage()).isEqualTo("工单不存在");
        assertThat(result.getData()).isNull();
    }

    @Test
    void generic_data_type_preserves_payload_type() {
        Result<Integer> result = Result.success(42);

        assertThat(result.getData()).isInstanceOf(Integer.class).isEqualTo(42);
    }
}