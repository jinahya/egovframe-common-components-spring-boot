package com.github.jinahya.egovframe.boot.autoconfigure;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.egovframe.rte.fdl.cmmn.trace.handler.DefaultTraceHandler;
import org.egovframe.rte.fdl.cmmn.trace.handler.TraceHandler;
import org.egovframe.rte.fdl.cmmn.trace.manager.DefaultTraceHandleManager;
import org.egovframe.rte.fdl.cmmn.trace.manager.TraceHandlerService;
import org.egovframe.rte.fdl.property.impl.EgovPropertyServiceImpl;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Role;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import com.github.jinahya.egovframe.boot.autoconfigure.components.EgovComponentConfigurations;

import egovframework.com.cmm.EgovMessageSource;
import egovframework.com.cmm.config.EgovLoginConfig;
import egovframework.com.cmm.service.EgovUserDetailsService;
import egovframework.com.cmm.service.impl.EgoDummyUserDetailsServiceImpl;
import egovframework.com.cmm.service.impl.EgovUserDetailsSessionServiceImpl;
import egovframework.com.cmm.util.EgovUserDetailsHelper;

/**
 * The bridge between Spring Boot and the eGovFrame runtime: every bean upstream's code injects by
 * name and upstream's deployment XML used to define, built once from Boot's own beans.
 * <ul>
 * <li>{@code egov.dataSource}, {@code txManager}: aliases of Boot's single DataSource and transaction
 * manager. Aliases, not second beans; a second DataSource would break Boot's own conditions.</li>
 * <li>{@code egov.sqlSession}: one {@link SqlSessionFactory} over the union of every enabled
 * component's {@link EgovMappers}, for the database named by {@code egovframe.database}.</li>
 * <li>{@code messageSource}: the runtime's ID-generation and property bundles plus upstream's
 * message bundles plus the application's own ({@code spring.messages.*}). The runtime looks this
 * bean up by name, so Boot's {@code MessageSourceAutoConfiguration} backs off.</li>
 * <li>{@code egovMessageSource}, {@code leaveaTrace}, {@code propertiesService}, {@code egovLoginConfig},
 * {@code egovUserDetailsHelper}, the three whitelists: as upstream's context-*.xml define them.</li>
 * <li>upstream's declarative transaction on {@code egovframework.com..*Impl.*(..)}.</li>
 * </ul>
 * Every bean backs off to one the application defines under the same name.
 */
@AutoConfiguration(
        after = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                TransactionAutoConfiguration.class},
        before = MessageSourceAutoConfiguration.class)
@ConditionalOnClass({EgovAbstractServiceImpl.class, SqlSessionFactoryBean.class})
@EnableConfigurationProperties(EgovFrameProperties.class)
@PropertySource(value = "classpath:egovframework/egovProps/globals.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
@Import(EgovComponentConfigurations.class)
public class EgovFrameAutoConfiguration {

    static final String MAPPER_CONFIG = "classpath:egovframework/mapper/config/mapper-config.xml";

    static final String MAPPER_ROOT = "classpath*:egovframework/mapper/com/";

    /** Upstream's context-common.xml basenames, plus the runtime bundles the runtime reads by key. */
    static final List<String> MESSAGE_BASENAMES = List.of(
            "classpath:egovframework/message/com/message-common",
            "classpath:egovframework/message/com/message-validation",
            "classpath:org/egovframe/rte/fdl/idgnr/messages/idgnr",
            "classpath:org/egovframe/rte/fdl/property/messages/properties",
            "classpath:egovframework/egovProps/globals");

    // ---------------------------------------------------------------------------------------------- globals.properties

    /**
     * Upstream's static {@code EgovProperties} (and {@code Globals}, whose constants every file-handling
     * service touches at class-initialization) read {@code egovProps/globals.properties} as a file.
     * The components jar carries no directory entries, so upstream resolves that path relative to
     * the working directory; this copies the bundled {@code egovframework/egovProps/**} there once,
     * never overwriting, before any upstream class can initialize. Off with
     * {@code egovframe.globals.materialize=false}, when the application ships its own.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static BeanFactoryPostProcessor egovGlobalsMaterializer(Environment environment, ResourceLoader resourceLoader) {
        return beanFactory -> {
            if (!environment.getProperty("egovframe.globals.materialize", Boolean.class, true)) {
                return;
            }
            EgovGlobals.materialize(resourceLoader, java.nio.file.Path.of("egovProps"));
        };
    }

    // ---------------------------------------------------------------------------------------------- aliases

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static BeanFactoryPostProcessor egovAliases() {
        return beanFactory -> {
            alias(beanFactory, DataSource.class, "egov.dataSource");
            alias(beanFactory, PlatformTransactionManager.class, "txManager");
        };
    }

    private static void alias(ConfigurableListableBeanFactory beanFactory, Class<?> type, String alias) {
        if (beanFactory.containsBean(alias)) {
            return;
        }
        String[] names = beanFactory.getBeanNamesForType(type, true, false);
        String target = null;
        if (names.length == 1) {
            target = names[0];
        } else {
            for (String name : names) {
                if (beanFactory.containsBeanDefinition(name) && beanFactory.getBeanDefinition(name).isPrimary()) {
                    target = name;
                    break;
                }
            }
        }
        if (target != null) {
            beanFactory.registerAlias(target, alias);
        }
    }

    // ---------------------------------------------------------------------------------------------- messages

    @Bean("messageSource")
    @ConditionalOnMissingBean(name = "messageSource", search = SearchStrategy.CURRENT)
    ReloadableResourceBundleMessageSource messageSource(Environment environment) {
        MessageSourceProperties properties = Binder.get(environment)
                .bind("spring.messages", MessageSourceProperties.class)
                .orElseGet(MessageSourceProperties::new);
        List<String> basenames = new ArrayList<>(MESSAGE_BASENAMES);
        if (properties.getBasename() != null) {
            for (String basename : properties.getBasename()) {
                if (StringUtils.hasText(basename)) {
                    basenames.add(basename.trim());
                }
            }
        }
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(basenames.toArray(String[]::new));
        if (properties.getEncoding() != null) {
            messageSource.setDefaultEncoding(properties.getEncoding().name());
        }
        messageSource.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
        Duration cacheDuration = properties.getCacheDuration();
        if (cacheDuration != null) {
            messageSource.setCacheMillis(cacheDuration.toMillis());
        }
        messageSource.setAlwaysUseMessageFormat(properties.isAlwaysUseMessageFormat());
        messageSource.setUseCodeAsDefaultMessage(properties.isUseCodeAsDefaultMessage());
        return messageSource;
    }

    /**
     * Upstream wraps the context's message source; it only works with a
     * {@link ReloadableResourceBundleMessageSource}, so when the application's {@code messageSource}
     * is something else, a private one over the same basenames is built for it.
     */
    @Bean("egovMessageSource")
    @ConditionalOnMissingBean(name = "egovMessageSource")
    EgovMessageSource egovMessageSource(BeanFactory beanFactory, Environment environment) {
        Object messageSource = beanFactory.getBean("messageSource");
        ReloadableResourceBundleMessageSource delegate;
        if (messageSource instanceof ReloadableResourceBundleMessageSource reloadable) {
            delegate = reloadable;
        } else {
            delegate = messageSource(environment);
        }
        EgovMessageSource egovMessageSource = new EgovMessageSource();
        egovMessageSource.setReloadableResourceBundleMessageSource(delegate);
        return egovMessageSource;
    }

    // ---------------------------------------------------------------------------------------------- runtime beans

    /** Upstream's context-common.xml: one manager matching every class, one default handler. */
    @Bean("leaveaTrace")
    @ConditionalOnMissingBean(name = "leaveaTrace")
    LeaveaTrace leaveaTrace() {
        DefaultTraceHandleManager manager = new DefaultTraceHandleManager();
        manager.setReqExpMatcher(new AntPathMatcher());
        manager.setPatterns(new String[] {"*"});
        manager.setHandlers(new TraceHandler[] {new DefaultTraceHandler()});
        LeaveaTrace leaveaTrace = new LeaveaTrace();
        leaveaTrace.setTraceHandlerServices(new TraceHandlerService[] {manager});
        return leaveaTrace;
    }

    /**
     * Upstream's context-properties.xml map, with {@code Globals.*} values from the environment
     * (upstream's globals.properties is a property source of lowest precedence; application
     * properties override it), then {@code egovframe.properties.*}.
     */
    @Bean("propertiesService")
    @ConditionalOnMissingBean(name = "propertiesService")
    EgovPropertyServiceImpl propertiesService(Environment environment, EgovFrameProperties properties) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("pageUnit", "10");
        map.put("pageSize", "10");
        map.put("posblAtchFileSize", environment.getProperty("Globals.fileUpload.maxRequestSize", "104857600"));
        map.put("Globals.fileStorePath", environment.getProperty("Globals.fileStorePath", ""));
        map.put("Globals.addedOptions", "false");
        map.putAll(properties.getProperties());
        EgovPropertyServiceImpl service = new EgovPropertyServiceImpl();
        service.setProperties(map);
        return service;
    }

    @Bean("egovLoginConfig")
    @ConditionalOnMissingBean(name = "egovLoginConfig")
    EgovLoginConfig egovLoginConfig(EgovFrameProperties properties) {
        EgovLoginConfig config = new EgovLoginConfig();
        config.setLock(properties.getLogin().isLock());
        config.setLockCount(properties.getLogin().getLockCount());
        return config;
    }

    @Bean("egovPageLinkWhitelist")
    @ConditionalOnMissingBean(name = "egovPageLinkWhitelist")
    List<String> egovPageLinkWhitelist(EgovFrameProperties properties) {
        return List.copyOf(properties.getWhitelist().getPageLink());
    }

    @Bean("egovRSSWhitelist")
    @ConditionalOnMissingBean(name = "egovRSSWhitelist")
    List<String> egovRSSWhitelist(EgovFrameProperties properties) {
        return List.copyOf(properties.getWhitelist().getRss());
    }

    @Bean("egovNextUrlWhitelist")
    @ConditionalOnMissingBean(name = "egovNextUrlWhitelist")
    List<String> egovNextUrlWhitelist(EgovFrameProperties properties) {
        return List.copyOf(properties.getWhitelist().getNextUrl());
    }

    // ---------------------------------------------------------------------------------------------- user details

    /** Upstream's context-egovuserdetailshelper.xml profiles, selected by {@code egovframe.user-details}. */
    @Configuration(proxyBeanMethods = false)
    static class UserDetailsServiceConfiguration {

        @Bean("egovUserDetailsService")
        @ConditionalOnMissingBean(EgovUserDetailsService.class)
        @ConditionalOnProperty(prefix = "egovframe", name = "user-details", havingValue = "dummy")
        EgovUserDetailsService dummyUserDetailsService() {
            return new EgoDummyUserDetailsServiceImpl();
        }

        @Bean("egovUserDetailsService")
        @ConditionalOnMissingBean(EgovUserDetailsService.class)
        @ConditionalOnProperty(prefix = "egovframe", name = "user-details", havingValue = "session")
        EgovUserDetailsService sessionUserDetailsService() {
            return new EgovUserDetailsSessionServiceImpl();
        }

        @Bean("egovUserDetailsSecurityService")
        @ConditionalOnMissingBean(EgovUserDetailsService.class)
        @ConditionalOnProperty(prefix = "egovframe", name = "user-details", havingValue = "security")
        @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
        EgovUserDetailsService securityUserDetailsService() {
            return new egovframework.com.sec.ram.service.impl.EgovUserDetailsSecurityServiceImpl();
        }
    }

    /** The static helper every upstream service calls; wired only when a user-details service exists. */
    @Bean("egovUserDetailsHelper")
    @ConditionalOnMissingBean(name = "egovUserDetailsHelper")
    @ConditionalOnBean(EgovUserDetailsService.class)
    EgovUserDetailsHelper egovUserDetailsHelper(EgovUserDetailsService userDetailsService) {
        EgovUserDetailsHelper helper = new EgovUserDetailsHelper();
        helper.setEgovUserDetailsService(userDetailsService);
        return helper;
    }

    // ---------------------------------------------------------------------------------------------- data access

    /**
     * Upstream's context-mapper.xml: one factory over the union of what the enabled components
     * contribute. Boot's own MyBatis auto-configuration, if present, backs off from this bean and its
     * {@code mybatis.mapper-locations} is not consulted.
     */
    @Bean("egov.sqlSession")
    @ConditionalOnMissingBean(name = "egov.sqlSession")
    @ConditionalOnBean(DataSource.class)
    SqlSessionFactory egovSqlSessionFactory(BeanFactory beanFactory, ObjectProvider<EgovMappers> mappers,
            EgovFrameProperties properties, ResourceLoader resourceLoader) throws Exception {
        Set<String> directories = new TreeSet<>();
        mappers.orderedStream().forEach(m -> directories.addAll(m.getDirectories()));
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(beanFactory.getBean("egov.dataSource", DataSource.class));
        factory.setConfigLocation(resourceLoader.getResource(MAPPER_CONFIG));
        if (!directories.isEmpty()) {
            factory.setMapperLocations(mapperLocations(directories, properties.getDatabase(), resourceLoader));
        }
        return factory.getObject();
    }

    static Resource[] mapperLocations(Set<String> directories, EgovDatabase database, ResourceLoader resourceLoader)
            throws IOException {
        if (database == null) {
            throw new IllegalStateException("egovframe.database is not set but components contributing mapper XML are"
                    + " enabled; set it to one of " + Arrays.toString(EgovDatabase.values()));
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        List<Resource> resources = new ArrayList<>();
        for (String directory : directories) {
            resources.addAll(Arrays.asList(resolver.getResources(MAPPER_ROOT + directory + "/*" + database.mapperSuffix())));
        }
        return resources.toArray(Resource[]::new);
    }

    @Bean("egov.sqlSessionTemplate")
    @ConditionalOnMissingBean(name = "egov.sqlSessionTemplate")
    @ConditionalOnBean(name = "egov.sqlSession")
    SqlSessionTemplate egovSqlSessionTemplate(BeanFactory beanFactory) {
        return new SqlSessionTemplate(beanFactory.getBean("egov.sqlSession", SqlSessionFactory.class));
    }

    @Bean("egov.lobHandler")
    @ConditionalOnMissingBean(name = "egov.lobHandler")
    DefaultLobHandler egovLobHandler() {
        return new DefaultLobHandler();
    }

    // ---------------------------------------------------------------------------------------------- transactions

    /**
     * Upstream's context-transaction.xml: REQUIRED on every method of {@code egovframework.com..*Impl},
     * rolling back on any Exception. The transaction manager is resolved by name ({@code txManager})
     * on first use, so nothing is instantiated early.
     */
    @Bean("egov.transactionAdvisor")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "egov.transactionAdvisor")
    @ConditionalOnProperty(prefix = "egovframe.transaction", name = "enabled", havingValue = "true", matchIfMissing = true)
    static DefaultPointcutAdvisor egovTransactionAdvisor(BeanFactory beanFactory) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionAttribute.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Exception.class)));
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource((method, targetClass) -> attribute);
        interceptor.setTransactionManagerBeanName("txManager");
        interceptor.setBeanFactory(beanFactory);
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* egovframework.com..*Impl.*(..))");
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
        advisor.setOrder(Integer.MAX_VALUE - 100);
        return advisor;
    }
}
