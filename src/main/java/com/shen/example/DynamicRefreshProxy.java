package com.shen.example;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author shenjianeng
 * @date 2019/12/4
 */
public class DynamicRefreshProxy implements InvocationHandler {

    private final AtomicReference<Object> atomicReference;


    public DynamicRefreshProxy(Object instance) {
        atomicReference = new AtomicReference<>(instance);
    }

    public static Object newInstance(Object obj) {
        return Proxy.newProxyInstance(
                obj.getClass().getClassLoader(),
                obj.getClass().getInterfaces(),
                new DynamicRefreshProxy(obj));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return ReflectionUtils.invokeMethod(method, atomicReference.get(), args);
    }

    public static void main(String[] args) {
        //1. 创建 dataSource 代理对象
        //2. 配置刷新之后修改 DynamicRefreshProxy 中的 atomicReference 的引用值
        //3. 修改完之后,关闭关闭旧对象相关的资源
    }
}
