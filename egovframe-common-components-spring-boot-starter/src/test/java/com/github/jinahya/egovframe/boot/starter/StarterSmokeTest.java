package com.github.jinahya.egovframe.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import egovframework.com.sym.log.clg.service.EgovLoginLogService;

/**
 * A plain Boot application with only the starter, H2 and two components on: boots, upstream's
 * services are injectable, and the logging exclusions hold (Logback is the only binding).
 */
@SpringBootTest(classes = StarterSmokeTest.App.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:starter;DB_CLOSE_DELAY=-1",
        "egovframe.database=postgres",
        "egovframe.components.sym.log.clg.enabled=true",
        "egovframe.components.sec.ram.enabled=true"})
class StarterSmokeTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    EgovLoginLogService loginLogService;

    @Test
    void boots() {
        assertThat(loginLogService).isNotNull();
        assertThat(context.containsBean("egov.sqlSession")).isTrue();
        assertThat(context.containsBean("authorManageDAO") || context.containsBean("AuthorManageDAO")).isTrue();
        assertThat(org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName()).contains("logback");
    }
}
