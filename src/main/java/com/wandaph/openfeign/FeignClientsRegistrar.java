/**
 * Software License Declaration.
 * <p>
 * wandaph.com, Co,. Ltd.
 * Copyright ? 2017 All Rights Reserved.
 * <p>
 * Copyright Notice
 * This documents is provided to wandaph contracting agent or authorized programmer only.
 * This source code is written and edited by wandaph Co,.Ltd Inc specially for financial
 * business contracting agent or authorized cooperative company, in order to help them to
 * install, programme or central control in certain project by themselves independently.
 * <p>
 * Disclaimer
 * If this source code is needed by the one neither contracting agent nor authorized programmer
 * during the use of the code, should contact to wandaph Co,. Ltd Inc, and get the confirmation
 * and agreement of three departments managers  - Research Department, Marketing Department and
 * Production Department.Otherwise wandaph will charge the fee according to the programme itself.
 * <p>
 * Any one,including contracting agent and authorized programmer,cannot share this code to
 * the third party without the agreement of wandaph. If Any problem cannot be solved in the
 * procedure of programming should be feedback to wandaph Co,. Ltd Inc in time, Thank you!
 */
package com.wandaph.openfeign;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.niws.loadbalancer.DefaultNIWSServerListFilter;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.niws.loadbalancer.NIWSDiscoveryPing;
import com.wandaph.openfeign.annotation.FeignClient;
import com.wandaph.openfeign.eureka.EurekaRegistry;
import com.wandaph.openfeign.support.ResponseEntityDecoder;
import com.wandaph.openfeign.support.SpringDecoder;
import com.wandaph.openfeign.support.SpringEncoder;
import com.wandaph.openfeign.support.SpringMvcContract;
import feign.Feign;
import feign.Feign.Builder;
import feign.Target;
import feign.Target.HardCodedTarget;
import feign.hystrix.FallbackFactory;
import feign.hystrix.HystrixFeign;
import feign.ribbon.LBClient;
import feign.ribbon.LBClientFactory;
import feign.ribbon.RibbonClient;
import feign.slf4j.Slf4jLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.inject.Provider;
import java.util.Map;
import java.util.Set;

/**
 * 扫描包路径带@FeiClient注解类,注入Spring容器
 *
 * @author lvzhen
 * @version Id: FeignRegistry.java, v 0.1 2019/3/5 14:44 lvzhen Exp $$
 */
@Component
public class FeignClientsRegistrar implements BeanFactoryPostProcessor, EnvironmentAware, BeanClassLoaderAware, ResourceLoaderAware {

    public Logger log = LoggerFactory.getLogger(FeignClientsRegistrar.class);

    /**
     * 扫描Feign客户端包默认路径
     **/
    private static final String SCAN_BASE_PACKAGE_DEFAULT = "com.hsjry.p2p.web.mvc.clients";
    private static final String SERVICE_URL_DEFAULT = "http://localhost:8761/eureka/";

    private Environment environment;
    private ClassLoader classLoader;
    private ResourceLoader resourceLoader;

    /**
     * 应用名
     */
    private String appName;
    /**
     * 应用端口号
     */
    private int port;
    /**
     * @FeignClient 注解标注包路径
     */
    private String[] scanBasePackages;
    /**
     * Eureka服务地址 默认 SERVICE_URL_DEFAULT
     */
    private String serviceUrl;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String[] getScanBasePackages() {
        if (scanBasePackages == null || scanBasePackages.length == 0) {
            return new String[]{SCAN_BASE_PACKAGE_DEFAULT};
        }
        return scanBasePackages;
    }

    public void setScanBasePackages(String[] scanBasePackages) {
        this.scanBasePackages = scanBasePackages;
    }

    public String getServiceUrl() {
        return StringUtils.isEmpty(serviceUrl) ? SERVICE_URL_DEFAULT : serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        ClassPathScanningCandidateComponentProvider classScanner = getClassScanner();
        classScanner.setResourceLoader(this.resourceLoader);
        AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(FeignClient.class);
        classScanner.addIncludeFilter(annotationTypeFilter);
        for (String basePackage : getScanBasePackages()) {
            Set<BeanDefinition> beanDefinitionSet = classScanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidateComponent : beanDefinitionSet) {
                if (candidateComponent instanceof AnnotatedBeanDefinition) {
                    AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                    AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
                    registerBeans(annotationMetadata, configurableListableBeanFactory);
                }
            }
        }
    }

    /**
     * 注入Feign标注接口
     *
     * @param annotationMetadata
     * @param configurableListableBeanFactory
     */
    private void registerBeans(AnnotationMetadata annotationMetadata, ConfigurableListableBeanFactory configurableListableBeanFactory) {
        Class<?> targetInterface = null;
        try {
            targetInterface = Class.forName(annotationMetadata.getClassName());
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage());
        }
        Assert.isTrue(annotationMetadata.isInterface(), "@FeignClient can only be specified on an interface");
        Map<String, Object> attributes = annotationMetadata.getAnnotationAttributes(FeignClient.class.getCanonicalName());
        validate(attributes);
        String serviceId = (String) attributes.get("value");
        serviceId = resolve(serviceId);
        //获取断路器失败返回
        Class<?> fallbackFactory = (Class<?>) attributes.get("fallbackFactory");
        Class<?> fallback = (Class<?>) attributes.get("fallback");

        Object target = buildProxyTarget(targetInterface, serviceId, fallback, fallbackFactory);
        configurableListableBeanFactory.registerSingleton(targetInterface.getName(), target);
    }

    private void validate(Map<String, Object> attributes) {
        AnnotationAttributes annotation = AnnotationAttributes.fromMap(attributes);
        validateFallback(annotation.getClass("fallback"));
        validateFallbackFactory(annotation.getClass("fallbackFactory"));
    }

    static void validateFallback(final Class clazz) {
        Assert.isTrue(!clazz.isInterface(),
                "Fallback class must implement the interface annotated by @FeignClient");
    }

    static void validateFallbackFactory(final Class clazz) {
        Assert.isTrue(!clazz.isInterface(), "Fallback factory must produce instances "
                + "of fallback classes that implement the interface annotated by @FeignClient");
    }

    /**
     * 占位符解析
     *
     * @param value
     * @return
     * @FeignClient(value = "${wandaph-cif-center}")
     */
    private String resolve(String value) {
        if (StringUtils.hasText(value)) {
            return this.environment.resolvePlaceholders(value);
        }
        return value;
    }

    /**
     * 扫描包路径
     *
     * @return
     */
    private ClassPathScanningCandidateComponentProvider getClassScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
            @Override
            protected boolean isCandidateComponent(
                    AnnotatedBeanDefinition beanDefinition) {
                if (beanDefinition.getMetadata().isInterface()) {
                    try {
                        Class<?> target = ClassUtils.forName(beanDefinition.getMetadata().getClassName(), classLoader);
                        return !target.isAnnotation();
                    } catch (Exception e) {
                        log.error("loadClass Exception:", e);
                    }
                }
                return false;
            }
        };
    }


    /**
     * 构建Feign代理对象
     *
     * @param targetInterface
     * @param serviceId
     * @return
     */
    public Object buildProxyTarget(Class<?> targetInterface, String serviceId, Class<?> fallback, Class<?> fallbackFactory) {
        //获取EurekaClient 客户端
        final EurekaClient client = EurekaRegistry.getEurekaClientInstance(getServiceUrl(), getAppName(), getPort());

        //主要的作用就是装载配置信息，用于初始化客户端和负载均衡器。默认的实现方式是DefaultClientConfigImpl。
        final IClientConfig clientConfig = new DefaultClientConfigImpl();
        clientConfig.loadDefaultValues();

        //设置vipAddress，该值对应spring.application.name配置，指定某个应用
        clientConfig.set(CommonClientConfigKey.DeploymentContextBasedVipAddresses, serviceId);
        Provider<EurekaClient> eurekaClientProvider = new Provider<EurekaClient>() {
            @Override
            public synchronized EurekaClient get() {
                return client;
            }
        };

        //根据eureka client获取服务列表，client以provide形式提供
        DiscoveryEnabledNIWSServerList discoveryEnabledNIWSServerList = new DiscoveryEnabledNIWSServerList(clientConfig, eurekaClientProvider);

        /**实例化负载均衡器接口ILoadBalancer，这里使用了ZoneAwareLoadBalancer，
         * 这也是spring cloud默认使用的。该实现可以避免了跨区域（Zone）访问的情况。
         *  其中的参数分别为，
         * 1）某个具体应用的客户端配置，
         * 2）负载均衡的处理规则IRule对象，负载均衡器实际进行服务实例选择任务是委托给了IRule实例中的choose函数来实现,这里使用了ZoneAvoidanceRule，
         * 3）实例化检查服务实例是否正常服务的IPing接口对象，负载均衡器启动一个用于定时检查Server是否健康的任务。该任务默认的执行间隔为：10秒。这里没有做真实的ping操作，他只是检查DiscoveryEnabledNIWSServerList定时刷新过来的服务列表中的每个服务的状态；
         * 4）如上，ServerList接口有两个方法，分别为获取初始化的服务实例清单和获取更新的服务实例清单；
         * 5）ServerListFilter接口实现，用于对服务实例列表的过滤，根据规则返回过滤后的服务列表；
         * 6）ServerListUpdater服务更新器接口 实现动态获取更新服务列表，默认30秒执行一次*/
        final ILoadBalancer loadBalancer = new ZoneAwareLoadBalancer(clientConfig, new ZoneAvoidanceRule(),
                new NIWSDiscoveryPing(), discoveryEnabledNIWSServerList, new DefaultNIWSServerListFilter());

        RibbonClient ribbonClient = RibbonClient.builder().lbClientFactory(new LBClientFactory() {
            @Override
            public LBClient create(String clientName) {
                return LBClient.create(loadBalancer, clientConfig);
            }
        }).build();

        if (!serviceId.startsWith("http://") && !serviceId.startsWith("https://")) {
            serviceId = "http://" + serviceId;
        }

        //熔断降级,处理失败返回
        if (fallback != void.class || fallbackFactory != void.class) {
            HystrixFeign.Builder hystrixFeign = HystrixFeign.builder()
                    .client(ribbonClient)
                    .logger(new Slf4jLogger(targetInterface))
                    .encoder(new SpringEncoder())
                    .decoder(new ResponseEntityDecoder(new SpringDecoder()))
                    .contract(new SpringMvcContract());
            try {
                return fallback != void.class ?
                        hystrixFeign.target(new HardCodedTarget(targetInterface, serviceId), fallback.newInstance())
                        : hystrixFeign.target(targetInterface, serviceId, (FallbackFactory) fallbackFactory.newInstance());
            } catch (Exception e) {
                throw new IllegalStateException(String.format(
                        "No  fallbackMechanism instance of type  found for feign client %s", targetInterface.getName()));
            }
        } else {
            Feign.Builder feign = Feign.builder()
                    .client(ribbonClient)
                    .logger(new Slf4jLogger(targetInterface))
                    .encoder(new SpringEncoder())
                    .decoder(new ResponseEntityDecoder(new SpringDecoder()))
                    .contract(new SpringMvcContract());
            return feign.target(targetInterface, serviceId);
        }
    }

}