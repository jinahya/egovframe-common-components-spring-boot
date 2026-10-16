package com.github.jinahya.egovframe.boot.autoconfigure;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A contribution of mapper directories to the single {@code egov.sqlSession}. Each enabled component
 * registers one; the auto-configuration takes the union, so the same directory contributed twice is
 * loaded once.
 * <p>
 * A directory is relative to {@code egovframework/mapper/com}, e.g. {@code sym/log/clg}. Every
 * {@code *_SQL_<database>.xml} in it is loaded, with the database from {@code egovframe.database}.
 */
public final class EgovMappers {

    private final Set<String> directories;

    private EgovMappers(Set<String> directories) {
        this.directories = Collections.unmodifiableSet(new TreeSet<>(directories));
    }

    public static EgovMappers of(String... directories) {
        return new EgovMappers(new TreeSet<>(Arrays.asList(directories)));
    }

    public Set<String> getDirectories() {
        return directories;
    }

    @Override
    public String toString() {
        return "EgovMappers" + directories;
    }
}
