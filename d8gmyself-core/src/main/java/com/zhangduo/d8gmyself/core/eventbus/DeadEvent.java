package com.zhangduo.d8gmyself.core.eventbus;

/**
 * 无效事件封装
 *
 * @author zhangduo -- 2018/1/28
 */
public class DeadEvent {

    private final Object eventBus;
    private final Object event;

    public DeadEvent(Object eventBus, Object event) {
        this.eventBus = eventBus;
        this.event = event;
    }

    public Object getEventBus() {
        return eventBus;
    }

    public Object getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "DeadEvent{" +
                "eventBus=" + eventBus +
                ", event=" + event +
                '}';
    }
}
