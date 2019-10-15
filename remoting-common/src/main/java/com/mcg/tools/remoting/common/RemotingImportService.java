package com.mcg.tools.remoting.common;

import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

@Service
public class RemotingImportService implements BeanFactoryPostProcessor {

	private static Log log = LogFactory.getLog(RemotingExportService.class);

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		try (ScanResult scanResult =
		        new ClassGraph()
		            .enableAllInfo()             // Scan classes, methods, fields, annotations
		            .scan()) {                   // Start the scan

			
			for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(RemoteEndpoint.class.getCanonicalName())) {
		    	Class c = Class.forName(classInfo.getName());
		    	try {
		    		beanFactory.getBean(c);
		    		log.info(c.getName()+" already has an implementation ... skipping");
		    		continue;
				} catch (Exception e) {
				}
	    		log.info(c.getName()+" has NO implementation ... adding bean definition ");

	    		final ImportedService is = beanFactory.getBean(ImportedService.class, c);
	    		
	    		beanFactory.autowireBean(is);
	    		
				GenericBeanDefinition bdSupplier = new GenericBeanDefinition();
				bdSupplier.setBeanClassName(ImportedService.class.getCanonicalName());
				bdSupplier.setInstanceSupplier(new ImportedServiceSupplier(is));
				((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(classInfo.getName()+"_importer", bdSupplier);				
	    		log.info(c.getName()+" has NO implementation ... supplier registered");

				GenericBeanDefinition bdService = new GenericBeanDefinition();
				bdService.setBeanClassName(classInfo.getName());
				bdService.setInstanceSupplier(new ImportedServiceProxySupplier(is));
				bdService.setLazyInit(true);
				((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(classInfo.getName(), bdService);				
	    		log.info(c.getName()+" has NO implementation ... proxy registerd");
				
		    }
			
		} catch(Exception e) {
			log.error("error looking for remote endpoints ... ",e);
		}
	
	}
	
	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public ImportedService importService(Class<?> service) {
		ImportedService im = new ImportedService(service);
		return im;
	}
	
	private class ImportedServiceSupplier implements Supplier<ImportedService> {
		
		private ImportedService importedService;
		
		public ImportedServiceSupplier(ImportedService importedService) {
			this.importedService = importedService;
		}

		@Override
		public ImportedService get() {
			return this.importedService;
		}
		
	}
	
	
	private class ImportedServiceProxySupplier implements Supplier {
		
		private ImportedService importedService;
		
		public ImportedServiceProxySupplier(ImportedService importedService) {
			this.importedService = importedService;
		}

		@Override
		public Object get() {
			return this.importedService.getProxy();
		}
		
	}
	
	
	
	
	
	
	
}
