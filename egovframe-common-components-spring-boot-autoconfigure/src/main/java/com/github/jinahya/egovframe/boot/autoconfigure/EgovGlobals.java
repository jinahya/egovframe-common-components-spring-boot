package com.github.jinahya.egovframe.boot.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Copies upstream's bundled {@code egovframework/egovProps/**} (globals.properties and the monitoring
 * scripts) to a directory, keeping files already there. See {@link EgovFrameAutoConfiguration}.
 */
final class EgovGlobals {

    private static final Logger LOGGER = LoggerFactory.getLogger(EgovGlobals.class);

    static final String SOURCE = "classpath*:egovframework/egovProps/**";

    static final String SOURCE_PREFIX = "egovframework/egovProps/";

    static void materialize(ResourceLoader resourceLoader, Path target) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        int copied = 0;
        try {
            for (Resource resource : resolver.getResources(SOURCE)) {
                if (!resource.isReadable()) {
                    continue;
                }
                String url = resource.getURL().toString();
                int index = url.lastIndexOf(SOURCE_PREFIX);
                if (index < 0 || url.endsWith("/")) {
                    continue;
                }
                Path file = target.resolve(url.substring(index + SOURCE_PREFIX.length()));
                if (Files.exists(file)) {
                    continue;
                }
                Files.createDirectories(file.getParent());
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to materialize " + SOURCE + " into " + target.toAbsolutePath(), e);
        }
        if (copied > 0) {
            LOGGER.info("copied {} file(s) of upstream's egovProps into {}; upstream reads globals.properties there",
                    copied, target.toAbsolutePath());
        }
    }

    private EgovGlobals() {
    }
}
