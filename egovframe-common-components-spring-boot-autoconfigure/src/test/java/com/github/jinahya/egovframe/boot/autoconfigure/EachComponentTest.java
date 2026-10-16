package com.github.jinahya.egovframe.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.jinahya.egovframe.boot.autoconfigure.components.EgovComponentConfigurations;

/**
 * Every generated component boots alone, with only its own closure, on an empty H2 and the
 * PostgreSQL mapper set (parsed, never executed). Components that need beans only the application
 * can provide are expected to fail naming exactly those beans.
 */
class EachComponentTest {

    static List<String> keys() {
        return EgovComponentConfigurations.PROPERTY_KEYS;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keys")
    void bootsAlone(String key) {
        Runners.withH2().withPropertyValues(key + ".enabled=true", "egovframe.database=postgres").run(context -> {
            List<String> external = EgovComponentConfigurations.EXTERNAL_BEANS.get(key);
            if (external == null) {
                assertThat(context).hasNotFailed();
            } else {
                assertThat(context).hasFailed();
                StringBuilder messages = new StringBuilder();
                for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause() == t ? null : t.getCause()) {
                    messages.append(t.getMessage()).append('\n');
                }
                String message = messages.toString();
                assertThat(external).anySatisfy(requirement -> {
                    if (requirement.startsWith("library ")) {
                        // "library project:smeapi:2.7.0 (...)": the failure names a class of that library
                        String artifact = requirement.split(" ")[1].split(":")[1];
                        assertThat(message).containsIgnoringCase(artifact);
                    } else {
                        assertThat(message).contains("'" + requirement + "'");
                    }
                });
            }
        });
    }
}
