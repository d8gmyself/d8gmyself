package com.d8gmyself.core.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 订阅注解，用于标记事件处理方法
 *
 * @author zhangduo -- 2018/1/28
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    /**
     * 是否可以并发执行
     */
    boolean allowConcurrency() default true;

    /**
     * 是否异步处理
     */
    boolean async() default false;

    /**
     * 顺序
     */
    int order() default Integer.MAX_VALUE - 1;

}
