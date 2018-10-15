package com.d8gmyself.core.eventbus;

import java.util.Iterator;

/**
 * 事件分发
 *
 * @author zhangduo -- 2018/1/28
 */
interface Dispatcher {

    /**
     * 分发事件
     *
     * @param event       事件
     * @param subscribers 订阅者Iterator
     */
    void dispatch(Object event, Iterator<Subscriber> subscribers);

}
