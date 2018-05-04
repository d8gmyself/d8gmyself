package com.zhangduo.d8gmyself.core.eventbus;

import java.lang.reflect.Method;

/**
 * 订阅者异常处理
 *
 * @author zhangduo -- 2018/1/28
 */
public interface SubscriberExceptionHandler {

    /**
     * 处理异常
     *
     * @param exp              异常信息
     * @param subscriber       订阅者对象
     * @param subscriberMethod 抛出异常的事件处理方法
     * @param args             参数（事件对象）
     */
    void handleException(Throwable exp, Object subscriber, Method subscriberMethod, Object... args);

}
