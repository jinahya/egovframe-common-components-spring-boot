package com.github.jinahya.egovframe.boot.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.github.jinahya.egovframe.boot.autoconfigure.components.EgovComponentConfigurations;

final class Runners {

    static ApplicationContextRunner bare() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EgovFrameAutoConfiguration.class));
    }

    /** Boot's DataSource (H2 in memory, Hikari), transaction manager, AOP, then ours. */
    static ApplicationContextRunner withH2() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
                        AopAutoConfiguration.class, EgovFrameAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:egov;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa");
    }

    /** Every generated component except those needing beans only the application can provide. */
    static List<String> selfContainedComponents() {
        List<String> keys = new ArrayList<>();
        for (String key : EgovComponentConfigurations.PROPERTY_KEYS) {
            if (!EgovComponentConfigurations.EXTERNAL_BEANS.containsKey(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    static String[] enabled(List<String> keys, String... more) {
        List<String> values = new ArrayList<>();
        for (String key : keys) {
            values.add(key + ".enabled=true");
        }
        values.addAll(List.of(more));
        return values.toArray(String[]::new);
    }

    private Runners() {
    }
}
