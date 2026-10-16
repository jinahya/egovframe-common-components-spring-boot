package com.github.jinahya.egovframe.boot.autoconfigure;

import java.util.Map;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Loads each XML named by {@link EgovIdGeneration} into a scratch factory and copies the definitions
 * the real registry does not have yet.
 */
class EgovIdGenerationRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    static final String LOCATION = "classpath:egovframework/spring/com/idgn/";

    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(EgovIdGeneration.class.getName());
        if (attributes == null) {
            return;
        }
        for (String file : (String[]) attributes.get("value")) {
            DefaultListableBeanFactory scratch = new DefaultListableBeanFactory();
            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(scratch);
            if (resourceLoader != null) {
                reader.setResourceLoader(resourceLoader);
            }
            reader.loadBeanDefinitions(LOCATION + file);
            for (String name : scratch.getBeanDefinitionNames()) {
                if (registry.containsBeanDefinition(name)) {
                    continue;
                }
                registry.registerBeanDefinition(name, scratch.getBeanDefinition(name));
                for (String alias : scratch.getAliases(name)) {
                    if (!registry.isAlias(alias) && !registry.containsBeanDefinition(alias)) {
                        registry.registerAlias(name, alias);
                    }
                }
            }
        }
    }
}
