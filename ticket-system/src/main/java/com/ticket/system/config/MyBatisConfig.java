package com.ticket.system.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ticket-system 模块的 MyBatis 配置。
 * <p>
 * 启动类在 {@code com.ticket.web}，MyBatis 的自动 mapper 扫描只覆盖启动类所在包，
 * 扫不到本模块的 {@code com.ticket.system.mapper} —— 故在此显式声明。
 * <p>
 * 每个模块各自声明自己的 {@code @MapperScan}，
 * 避免某一处集中配置反过来耦合所有模块的包结构。
 * <p>
 * <b>分页插件</b>：MyBatis Plus 3.5 起分页需要显式注册 {@link PaginationInnerInterceptor}，
 * 否则 {@code BaseMapper.selectPage} 只会返回行不会统计 total，
 * 集成测试里 PageVO.total 永远是 0。
 * <p>
 * 不传 {@code DbType}：由 MP 自动从 DataSource 探测，
 * 让集成测试（H2）与生产（MySQL）共用一份配置。
 */
@Configuration
@MapperScan("com.ticket.system.mapper")
public class MyBatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
