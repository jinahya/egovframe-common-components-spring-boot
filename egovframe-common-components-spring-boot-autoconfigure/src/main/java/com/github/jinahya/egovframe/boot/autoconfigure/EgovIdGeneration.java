package com.github.jinahya.egovframe.boot.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Registers the beans of upstream ID-generation contexts ({@code egovframework/spring/com/idgn/context-idgn-*.xml}).
 * <p>
 * Unlike {@code @ImportResource}, a context named by two enabled components is loaded once: bean
 * names already present in the registry are skipped. Boot forbids bean definition overriding, and
 * {@code @ImportResource} re-reads a resource for every configuration class that names it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EgovIdGenerationRegistrar.class)
public @interface EgovIdGeneration {

    /** File names under {@code egovframework/spring/com/idgn/}. */
    String[] value();
}
