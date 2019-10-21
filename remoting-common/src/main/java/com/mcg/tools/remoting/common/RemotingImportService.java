package com.mcg.tools.remoting.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Configuration
public class RemotingImportService {

	private static Log log = LogFactory.getLog(RemotingExportService.class);
	
	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public <T> ImportedServiceImpl<T> importService(Class<T> serviceInterface) {
		log.info(" = = = = = importing service: "+serviceInterface.getName());
		return new ImportedServiceImpl(serviceInterface);
	}

	/**
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		try (ScanResult scanResult =
		        new ClassGraph()
		            .enableAllInfo()             // Scan classes, methods, fields, annotations
		            .scan()) {                   // Start the scan
			
			for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(RemoteEndpoint.class.getCanonicalName())) {

				Class c = Class.forName(classInfo.getName());
				
				boolean implFound = false;

				for (ClassInfo classInfoImpl : scanResult.getClassesWithAnnotation(RemotingEndpoint.class.getCanonicalName())) {
					Class cimpl = Class.forName(classInfoImpl.getName());
					RemotingEndpoint re = (RemotingEndpoint)cimpl.getAnnotation(RemotingEndpoint.class);
					if(re.value().getName().equals(classInfoImpl.getName())) {
						implFound = true;
						break;
					}
				}

				if(beanFactory.getBeanNamesForType(c).length > 0) {
					implFound = true;
				}
				
				if(implFound) {
		    		log.info(c.getName()+" already has an implementation ... skipping");
		    		continue;
				}
				
				

	    		log.info(c.getName()+" has NO implementation ... adding bean definition ");

	    		ImportedService is = new ImportedService(c);
	    		
	    		// we register the "ImportedService" as a bean, so we can get stuff autowired
				GenericBeanDefinition bdSupplier = new GenericBeanDefinition();
				bdSupplier.setBeanClassName(ImportedService.class.getCanonicalName());
				bdSupplier.setInstanceSupplier(new ImportedServiceSupplier(is));
				((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(classInfo.getName()+"_configurer", bdSupplier);				
	    		log.info(c.getName()+" has NO implementation ... supplier registered");
	    		
	    		// now we register the proxy (getProxy() of "ImportedService") as a bean
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
	**/
	
	
	
}
