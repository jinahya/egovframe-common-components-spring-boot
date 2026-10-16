package com.github.jinahya.egovframe.boot.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code egovframe.*}. The per-component toggles, {@code egovframe.components.<package>.enabled},
 * are read by the generated configurations directly and listed in the configuration metadata.
 */
@ConfigurationProperties(prefix = "egovframe")
public class EgovFrameProperties {

    /**
     * Which database's mapper XML to load ({@code *_SQL_<database>.xml}). Required once any
     * component is enabled; there is no default, upstream ships eight sets and picks none.
     */
    private EgovDatabase database;

    /**
     * How {@code EgovUserDetailsHelper} learns the current user. {@code none} (the default) leaves
     * the helper unconfigured unless the application defines an {@code EgovUserDetailsService} bean.
     */
    private UserDetails userDetails = UserDetails.none;

    private final Transaction transaction = new Transaction();

    private final Login login = new Login();

    private final Whitelist whitelist = new Whitelist();

    /** Extra or overriding entries of the {@code propertiesService} bean (upstream's context-properties.xml map). */
    private Map<String, String> properties = new LinkedHashMap<>();

    public enum UserDetails {
        /** No helper unless the application provides an {@code EgovUserDetailsService}. */
        none,
        /** Upstream's dummy user, for development. */
        dummy,
        /** The user in the HTTP session ({@code LoginVO}); upstream's "session" profile. */
        session,
        /** The Spring Security principal; upstream's "security" profile. */
        security
    }

    public static class Transaction {
        /** Upstream's declarative transaction on {@code egovframework.com..*Impl.*(..)}, rollback on any Exception. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Login {
        /** Upstream {@code Globals.login.Lock}: lock an account after too many failed logins. */
        private boolean lock = true;
        /** Upstream {@code Globals.login.LockCount}. */
        private int lockCount = 5;

        public boolean isLock() {
            return lock;
        }

        public void setLock(boolean lock) {
            this.lock = lock;
        }

        public int getLockCount() {
            return lockCount;
        }

        public void setLockCount(int lockCount) {
            this.lockCount = lockCount;
        }
    }

    /** Upstream's context-whitelist.xml lists, with its values as defaults. */
    public static class Whitelist {
        private List<String> pageLink = new ArrayList<>(List.of(
                "/egovframework/com/sym/mnu/stm/EgovSiteMap", "/cmm/sym/mpm/EgovSiteMap", "/egovframework/com/main_bottom"));
        private List<String> rss = new ArrayList<>(List.of("comthtrsmrcvmntrngloginfo", "comtczip"));
        private List<String> nextUrl = new ArrayList<>(List.of(
                "/uss/umt/EgovMberSbscrbView.do", "/uss/umt/EgovEntrprsSbscrbView.do", "/uss/olp/cns/CnsltDtlsRegistView.do"));

        public List<String> getPageLink() {
            return pageLink;
        }

        public void setPageLink(List<String> pageLink) {
            this.pageLink = pageLink;
        }

        public List<String> getRss() {
            return rss;
        }

        public void setRss(List<String> rss) {
            this.rss = rss;
        }

        public List<String> getNextUrl() {
            return nextUrl;
        }

        public void setNextUrl(List<String> nextUrl) {
            this.nextUrl = nextUrl;
        }
    }

    public EgovDatabase getDatabase() {
        return database;
    }

    public void setDatabase(EgovDatabase database) {
        this.database = database;
    }

    public UserDetails getUserDetails() {
        return userDetails;
    }

    public void setUserDetails(UserDetails userDetails) {
        this.userDetails = userDetails;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Login getLogin() {
        return login;
    }

    public Whitelist getWhitelist() {
        return whitelist;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
