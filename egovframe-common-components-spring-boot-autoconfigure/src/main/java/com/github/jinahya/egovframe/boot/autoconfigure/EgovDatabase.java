package com.github.jinahya.egovframe.boot.autoconfigure;

/**
 * The databases upstream ships mapper XML for; the name is the file suffix ({@code *_SQL_<name>.xml}).
 */
public enum EgovDatabase {
    postgres, mysql, maria, oracle, tibero, altibase, cubrid, goldilocks;

    /** The suffix of the mapper files for this database, e.g. {@code _SQL_postgres.xml}. */
    public String mapperSuffix() {
        return "_SQL_" + name() + ".xml";
    }
}
