package com.d8gmyself.core.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <pre>
 * 简单id生成器
 * 64位，42位用来保存时间戳，5位dataCenterId，5位workerId，12位sequence
 * </pre>
 *
 * @author zhangduo -- 2019-04-01
 */
class IdWorker {

    private static final Logger logger = LoggerFactory.getLogger(IdWorker.class);

    private static final long PUBLISH_TIMESTAMP = 1288834974657L;
    private static final int WORKER_ID_BITS_LENGTH = 5;
    private static final int DATACENTER_ID_BITS_LENGTH = 5;
    private static final int MAX_WORKER_ID = ~((~0) << WORKER_ID_BITS_LENGTH);
    private static final int MAX_DATACENTER_ID = ~((~0) << DATACENTER_ID_BITS_LENGTH);
    private static final int SEQUENCE_BITS_LENGTH = 12;
    private static final int MAX_SEQUENCE = ~((~0) << SEQUENCE_BITS_LENGTH);
    private static final int TIMESTAMP_LEFT_SHIFT = DATACENTER_ID_BITS_LENGTH + WORKER_ID_BITS_LENGTH + SEQUENCE_BITS_LENGTH;
    private static final int DATACENTER_LEFT_SHIFT = WORKER_ID_BITS_LENGTH + SEQUENCE_BITS_LENGTH;
    private static final int WORKER_LEFT_SHIFT = SEQUENCE_BITS_LENGTH;

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    //构造器
    IdWorker(final long workerId, final long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }

        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 获取生成下一个ID
     * @return  long
     */
    synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            logger.error(String.format("clock is moving backwards.  Rejecting requests until %d.", lastTimestamp));
            throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        if (lastTimestamp == timestamp) {
            if ((sequence = (sequence + 1) & MAX_SEQUENCE) == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - PUBLISH_TIMESTAMP) << TIMESTAMP_LEFT_SHIFT) | (datacenterId << DATACENTER_LEFT_SHIFT) | (workerId << WORKER_LEFT_SHIFT) | sequence;
    }

    /**
     * 获取时间戳
     * @param lastTimestamp lastTimestamp
     * @return  long
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前系统时间
     * @return  long
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

}
