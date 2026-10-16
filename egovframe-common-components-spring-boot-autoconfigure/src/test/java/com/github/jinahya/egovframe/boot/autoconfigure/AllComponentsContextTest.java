package com.github.jinahya.egovframe.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;

import egovframework.com.cmm.service.impl.EgovComAbstractDAO;

/** Every self-contained component in one context: no duplicate bean names, every mapper parses. */
class AllComponentsContextTest {

    @Test
    void allSelfContainedComponentsInOneContext() {
        var keys = Runners.selfContainedComponents();
        Runners.withH2().withPropertyValues(Runners.enabled(keys, "egovframe.database=postgres")).run(context -> {
            assertThat(context).hasNotFailed();
            var daos = context.getBeansOfType(EgovComAbstractDAO.class);
            var configuration = context.getBean("egov.sqlSession", SqlSessionFactory.class).getConfiguration();
            Set<String> namespaces = new TreeSet<>();
            for (String name : configuration.getMappedStatementNames()) {
                if (name.contains(".")) {
                    namespaces.add(name.substring(0, name.lastIndexOf('.')));
                }
            }
            System.out.printf("components enabled: %d, egovframework beans: %d, DAOs: %d, mapper namespaces: %d, statements: %d%n",
                    keys.size(),
                    context.getBeanDefinitionNames().length,
                    daos.size(), namespaces.size(), configuration.getMappedStatementNames().size() / 2);
            assertThat(daos).hasSizeGreaterThan(100);
            assertThat(namespaces).hasSizeGreaterThan(100);
        });
    }
}
