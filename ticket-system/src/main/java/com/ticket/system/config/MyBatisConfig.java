package com.ticket.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
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
 */
@Configuration
@MapperScan("com.ticket.system.mapper")
public class MyBatisConfig {

    /**
     * MyBatis Plus 拦截器链 —— 仅注册分页拦截器（按需扩展）。
     * <p>
     * {@link DbType#H2}：集成测试用 H2，MySQL 模式下也能通用。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }
}
