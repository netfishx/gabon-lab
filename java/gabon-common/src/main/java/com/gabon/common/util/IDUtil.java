package com.gabon.common.util;

/**
 * ID 生成工具类 - 使用 Snowflake 算法
 */
public class IDUtil {

    private static final SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(1, 1);

    /**
     * 生成雪花 ID
     * @return 唯一 ID
     */
    public static Comparable<?> geneSnowFlakeID() {
        return snowflakeIdGenerator.nextId();
    }

    /**
     * 轻量级 Snowflake ID 生成器
     * 替代 ShardingSphere 依赖
     */
    private static class SnowflakeIdGenerator {
        // 起始时间戳 (2024-01-01)
        private static final long EPOCH = 1704067200000L;
        
        // 各部分占用位数
        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;
        
        // 最大值
        private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
        private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
        
        // 位移
        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
        
        private final long workerId;
        private final long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        public SnowflakeIdGenerator(long workerId, long datacenterId) {
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException(String.format("Worker ID 必须在 0 到 %d 之间", MAX_WORKER_ID));
            }
            if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
                throw new IllegalArgumentException(String.format("Datacenter ID 必须在 0 到 %d 之间", MAX_DATACENTER_ID));
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();

            // 时钟回拨检测
            if (timestamp < lastTimestamp) {
                throw new RuntimeException(String.format("时钟回拨，拒绝生成 ID %d 毫秒", lastTimestamp - timestamp));
            }

            // 同一毫秒内
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    // 序列号耗尽，等待下一毫秒
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            // 组装 ID
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }

        private long waitNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }
    }
}
