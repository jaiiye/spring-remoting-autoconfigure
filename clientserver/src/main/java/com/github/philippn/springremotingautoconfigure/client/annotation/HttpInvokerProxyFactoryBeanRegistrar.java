/*
 * Copyright (C) 2015-2016 Philipp Nanz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package com.github.philippn.springremotingautoconfigure.client.annotation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.github.philippn.springremotingautoconfigure.annotation.RemoteExport;
import com.github.philippn.springremotingautoconfigure.util.RemotingUtils;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * @author Philipp Nanz
 */
public class HttpInvokerProxyFactoryBeanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private final Logger logger = LoggerFactory.getLogger(HttpInvokerProxyFactoryBeanRegistrar.class);

    private static final Set<String> alreadyProxiedSet
            = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private final String baseUrl = "http://localhost:8080";

    private Map<String, Object> customizeBaseUrls = new HashMap<>(0);

    /**
     * @return the baseUrl
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    public Map<String, Object> getCustomizeBaseUrls() {
        return customizeBaseUrls;
    }

    public void setCustomizeBaseUrls(Map<String, Object> customizeBaseUrls) {
        this.customizeBaseUrls = customizeBaseUrls;
    }

    /* (non-Javadoc)
     * @see org.springframework.context.annotation.ImportBeanDefinitionRegistrar#registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata, org.springframework.beans.factory.support.BeanDefinitionRegistry)
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Set<String> basePackages = new HashSet<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition definition = registry.getBeanDefinition(beanName);
            if (definition.getBeanClassName() != null) {
                try {
                    Class<?> resolvedClass = ClassUtils.forName(definition.getBeanClassName(), null);
                    EnableHttpInvokerAutoProxy autoProxy
                            = AnnotationUtils.findAnnotation(resolvedClass, EnableHttpInvokerAutoProxy.class);
                    if (autoProxy != null) {
                        if (autoProxy.basePackages().length > 0) {
                            Collections.addAll(basePackages, autoProxy.basePackages());
                        } else {
                            basePackages.add(resolvedClass.getPackage().getName());
                        }
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Unable to inspect class "
                            + definition.getBeanClassName() + " for @EnableHttpInvokerAutoProxy annotations");
                }
            }
        }

        if (basePackages.isEmpty()) {
            return;
        }

        ClassPathScanningCandidateComponentProvider scanner
                = new ClassPathScanningCandidateComponentProvider(false) {

            /* (non-Javadoc)
             * @see org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition)
             */
            @Override
            protected boolean isCandidateComponent(
                    AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface()
                        && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(RemoteExport.class));

        for (String basePackage : basePackages) {
            for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
                if (definition.getBeanClassName() != null) {
                    try {
                        Class<?> resolvedClass
                                = ClassUtils.forName(definition.getBeanClassName(), null);
                        setupProxy(resolvedClass, registry);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException("Unable to inspect class "
                                + definition.getBeanClassName() + " for @RemoteExport annotations");
                    }
                }
            }
        }
    }

    protected void setupProxy(Class<?> clazz, BeanDefinitionRegistry registry) {
        Assert.isTrue(clazz.isInterface(),
                "Annotation @RemoteExport may only be used on interfaces");

        if (alreadyProxiedSet.contains(clazz.getName())) {
            return;
        }
        alreadyProxiedSet.add(clazz.getName());

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(HttpInvokerProxyFactoryBean.class)
                .setLazyInit(true)
                .addPropertyValue("serviceInterface", clazz)
                .addPropertyValue("serviceUrl", customizeBaseUrl(clazz) + RemotingUtils.buildMappingPath(clazz));

        registry.registerBeanDefinition(clazz.getSimpleName() + "Proxy",
                builder.getBeanDefinition());

        logger.info("Created HttpInvokerProxyFactoryBean for " + clazz.getSimpleName());
    }

    protected String customizeBaseUrl(Class<?> clazz) {
        // https://github.com/philippn/spring-remoting-autoconfigure/issues/1
        final String[] keys = new String[]{clazz.getCanonicalName(), clazz.getPackage().getName()};
        final Map<String, Object> baseUrls = getCustomizeBaseUrls();
        if (!baseUrls.isEmpty()) {
            for (String key : keys) {
                if (baseUrls.containsKey(key)) {
                    return baseUrls.get(key).toString();
                }
            }
        }
        return getBaseUrl();
    }

    @Override
    public void setEnvironment(Environment environment) {
        initCustomizeBaseUrls(environment);
    }

    private void initCustomizeBaseUrls(Environment environment) {
        final String keyPrefix = "remote.baseUrl.";
        RelaxedPropertyResolver propertyResolver = new RelaxedPropertyResolver(environment);
        setCustomizeBaseUrls(propertyResolver.getSubProperties(keyPrefix));
    }

}
