package com.mcg.tools.remoting.common;

import java.lang.reflect.Field;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import com.mcg.tools.remoting.api.ImportedService;
import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;

@Service
@Configuration
public class RemotingImportService implements BeanPostProcessor, Ordered, ApplicationContextAware {

	private static Log log = LogFactory.getLog(RemotingExportService.class);

	private ApplicationContext applicationContext;
	
	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public <T> ImportedServiceImpl<T> importService(Class<T> serviceInterface) {
		log.info(" = = = = = importing service: "+serviceInterface.getName());
		return new ImportedServiceImpl(serviceInterface);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {	
	    
		log.debug("postprocessing: "+bean.getClass());
		
		final Class<?> clazz = bean.getClass();

	    ReflectionUtils.doWithFields(clazz, new ReflectionUtils.FieldCallback() {
	        @Override
	        public void doWith(final Field field) {
	            try {
	            	
	            	Class<?> s = field.getType();
	            	ReflectionUtils.makeAccessible(field);

	            	log.debug("postprocessing: "+bean.getClass()+" --- "+field.getName()+" / "+s.getName());

	            	if(field.get(bean) != null) {
	            		log.debug("postprocessing: "+bean.getClass()+" / not null, already wired");
	            		return; // not a remoting service
	            	}
	            	
	            	if(s.getAnnotation(RemoteEndpoint.class) == null) {
	            		log.debug("postprocessing: "+bean.getClass()+" / not a remoting endpoint");
	            		return; // not a remoting service
	            	}
	            	
	            	if(applicationContext.getBeanNamesForType(s).length > 0) {
	            		log.debug("postprocessing: "+bean.getClass()+" / already wired");
	            		return; // already there
	            	}

                    ImportedService<?> is = applicationContext.getBean(ImportedService.class, s);
                    
            		log.debug("postprocessing: "+bean.getClass()+" / injecting proxy");
                    field.set(bean, is.getProxy());

	            } catch(IllegalAccessException e) {
	                log.error("Cannot set " + field.getName() + " in " + beanName, e);
	            }
	        }
	    });
        log.debug("postprocessing done!");

	    return bean;
	}
	
	
}
