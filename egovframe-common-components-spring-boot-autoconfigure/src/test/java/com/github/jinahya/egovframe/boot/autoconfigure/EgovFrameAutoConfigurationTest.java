package com.github.jinahya.egovframe.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.MessageSource;
import org.springframework.transaction.PlatformTransactionManager;

import egovframework.com.cmm.EgovMessageSource;
import egovframework.com.cmm.service.EgovUserDetailsService;
import egovframework.com.cmm.util.EgovUserDetailsHelper;

class EgovFrameAutoConfigurationTest {

    @Test
    void withoutDataSource_coreBeansOnly() {
        Runners.bare().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("messageSource").hasBean("egovMessageSource").hasBean("leaveaTrace")
                    .hasBean("propertiesService").hasBean("egovLoginConfig").hasBean("egov.lobHandler")
                    .hasBean("egovPageLinkWhitelist").hasBean("egovRSSWhitelist").hasBean("egovNextUrlWhitelist");
            assertThat(context).doesNotHaveBean("egov.sqlSession").doesNotHaveBean("egov.sqlSessionTemplate")
                    .doesNotHaveBean("egovUserDetailsHelper");
            assertThat(context.containsBean("egov.dataSource")).isFalse();
            // the runtime's own bundle, read by name "messageSource" inside the runtime
            MessageSource messageSource = context.getBean("messageSource", MessageSource.class);
            assertThat(messageSource.getMessage("error.idgnr.greater.maxid", new Object[] {"Long"}, Locale.ENGLISH))
                    .isNotBlank();
            assertThat(context.getBean(EgovMessageSource.class).getReloadableResourceBundleMessageSource())
                    .isSameAs(messageSource);
            assertThat(context.getBean(EgovPropertyService.class).getString("pageUnit")).isEqualTo("10");
            assertThat(context.getBean(LeaveaTrace.class).countOfTheTraceHandlerService()).isEqualTo(1);
            assertThat(context).hasSingleBean(DefaultPointcutAdvisor.class);
        });
    }

    @Test
    void withDataSource_aliasesAndSqlSession() {
        Runners.withH2().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("egov.dataSource")).isSameAs(context.getBean(DataSource.class));
            assertThat(context.getBean("txManager")).isSameAs(context.getBean(PlatformTransactionManager.class));
            assertThat(context).hasBean("egov.sqlSession").hasBean("egov.sqlSessionTemplate");
            assertThat(context.getBean("egov.sqlSession", SqlSessionFactory.class).getConfiguration()
                    .getTypeAliasRegistry().resolveAlias("egovMap")).isNotNull();
        });
    }

    @Test
    void componentEnabled_databaseUnset_failsWithHint() {
        Runners.withH2().withPropertyValues("egovframe.components.sym.log.clg.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("egovframe.database");
        });
    }

    @Test
    void componentEnabled_loginLog() {
        Runners.withH2().withPropertyValues("egovframe.components.sym.log.clg.enabled=true",
                "egovframe.database=postgres").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("EgovLoginLogService").hasBean("loginLogDAO").hasBean("egovLoginLogIdGnrService");
            var statements = context.getBean("egov.sqlSession", SqlSessionFactory.class).getConfiguration()
                    .getMappedStatementNames();
            assertThat(statements).anyMatch(s -> s.startsWith("LoginLog."));
            // the login log's dependency on the shared code tables is not pulled in: nothing else enabled
            assertThat(context).doesNotHaveBean("EgovCmmUseService");
        });
    }

    @Test
    void userDetails_dummy() {
        Runners.bare().withPropertyValues("egovframe.user-details=dummy").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EgovUserDetailsService.class).hasBean("egovUserDetailsHelper");
            assertThat(context.getBean(EgovUserDetailsHelper.class).getEgovUserDetailsService())
                    .isSameAs(context.getBean(EgovUserDetailsService.class));
        });
    }

    @Test
    void applicationBeansWin() {
        Runners.bare().withBean("leaveaTrace", LeaveaTrace.class, LeaveaTrace::new).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LeaveaTrace.class).countOfTheTraceHandlerService()).isEqualTo(0);
        });
    }
}
