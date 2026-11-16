package com.mcg.tools.remoting.common.util;

import org.springframework.aop.framework.AopProxyUtils;

public class CglibHelper {
    private final Object proxied;
 
    public CglibHelper(Object proxied) {
        this.proxied = proxied;
    }
 
    public Object getTargetObject() {
        Object t = AopProxyUtils.getSingletonTarget(proxied);
        return t != null ? t : proxied;
    }
}
