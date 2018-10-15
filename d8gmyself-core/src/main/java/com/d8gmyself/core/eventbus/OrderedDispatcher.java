package com.d8gmyself.core.eventbus;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * 按排序分发事件
 *
 * @author zhangduo -- 2018/1/29
 */
class OrderedDispatcher implements Dispatcher {

    private ThreadLocal<Queue<Subscriber>> subscribersQueue = ThreadLocal.withInitial(() -> new PriorityQueue<>(Comparator.comparingInt(Subscriber::getOrder)));

    @Override
    public void dispatch(final Object event, final Iterator<Subscriber> subscribers) {
        while (subscribers.hasNext()) {
            subscribersQueue.get().add(subscribers.next());
        }
        Subscriber subscriber;
        while ((subscriber = subscribersQueue.get().poll()) != null) {
            subscriber.processEvent(event);
        }
    }

}
