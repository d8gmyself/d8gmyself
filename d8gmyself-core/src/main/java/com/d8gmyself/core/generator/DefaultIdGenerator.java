package com.d8gmyself.core.generator;

/**
 * 默认实现
 *
 * @author zhangduo -- 2019-04-01
 */
public class DefaultIdGenerator implements LongIdGenerator {

    private final IdWorker idWorker;

    public DefaultIdGenerator(int dataCenterId, int workerId) {
        this.idWorker = new IdWorker(workerId, dataCenterId);
    }

    @Override
    public long nextId() {
        return idWorker.nextId();
    }


}
