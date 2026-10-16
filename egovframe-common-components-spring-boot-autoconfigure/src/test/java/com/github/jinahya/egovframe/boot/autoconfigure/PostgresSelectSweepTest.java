package com.github.jinahya.egovframe.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Upstream's PostgreSQL DDL loaded into a real PostgreSQL, every self-contained component enabled,
 * every {@code <select>} executed with upstream's paging parameters and nothing else. A statement
 * that fails because a table is missing (SQLSTATE 42P01) means the component's wiring and the schema
 * disagree, which is the one defect this project owns. A missing column (42703) is upstream's SQL
 * against upstream's DDL. Anything else is a null parameter of the wrong type for this sweep and
 * says nothing about the schema. All three are printed; only the first is asserted.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresSelectSweepTest {

    static final String DDL = "egovframe/common-components/script/ddl/postgres/com_DDL_postgres.sql";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void everySelectRunsAgainstUpstreamDdl() {
        var keys = Runners.selfContainedComponents();
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
                        org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class,
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class,
                        EgovFrameAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword())
                .withPropertyValues(Runners.enabled(keys, "egovframe.database=postgres"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection()) {
                        ScriptUtils.executeSqlScript(connection, new ClassPathResource(DDL));
                    }
                    SqlSessionFactory factory = context.getBean("egov.sqlSession", SqlSessionFactory.class);
                    var configuration = factory.getConfiguration();
                    int selects = 0;
                    Map<String, String> failures = new TreeMap<>();
                    List<String> missingRelations = new ArrayList<>();
                    List<String> missingColumns = new ArrayList<>();
                    // upstream's paging contract (PaginationInfo): every list query binds these
                    Map<String, Object> paging = new HashMap<>();
                    paging.put("firstIndex", 0);
                    paging.put("lastIndex", 10);
                    paging.put("recordCountPerPage", 10);
                    paging.put("pageIndex", 1);
                    paging.put("pageSize", 10);
                    paging.put("pageUnit", 10);
                    for (String name : new ArrayList<>(configuration.getMappedStatementNames())) {
                        if (!name.contains(".")) {
                            continue;
                        }
                        MappedStatement statement = configuration.getMappedStatement(name);
                        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
                            continue;
                        }
                        selects++;
                        try (SqlSession session = factory.openSession()) {
                            session.selectList(name, new HashMap<>(paging));
                        } catch (Exception e) {
                            Throwable root = e;
                            while (root.getCause() != null && root.getCause() != root) {
                                root = root.getCause();
                            }
                            String state = root instanceof SQLException sql ? sql.getSQLState() : null;
                            failures.put(name, state + " " + String.valueOf(root.getMessage()).lines().findFirst().orElse(""));
                            if ("42P01".equals(state)) {
                                missingRelations.add(name);
                            } else if ("42703".equals(state)) {
                                missingColumns.add(name);
                            }
                        }
                    }
                    System.out.printf("postgres sweep: %d selects, %d failed: %d on a missing relation (ours), "
                            + "%d on a missing column (upstream SQL vs upstream DDL), %d on parameter typing (sweep)%n",
                            selects, failures.size(), missingRelations.size(), missingColumns.size(),
                            failures.size() - missingRelations.size() - missingColumns.size());
                    missingRelations.forEach(k -> System.out.println("  missing relation: " + k + " -> " + failures.get(k)));
                    missingColumns.forEach(k -> System.out.println("  missing column:   " + k + " -> " + failures.get(k)));
                    assertThat(selects).isGreaterThan(500);
                    assertThat(missingRelations).isEmpty();
                });
    }
}
