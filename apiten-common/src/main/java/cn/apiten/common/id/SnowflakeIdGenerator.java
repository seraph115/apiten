package cn.apiten.common.id;

public class SnowflakeIdGenerator {
    private static final long EPOCH = 1735689600000L; // 2025-01-01
    private static final long WORKER_BITS = 10L;
    private static final long SEQ_BITS = 12L;
    private static final long MAX_WORKER = ~(-1L << WORKER_BITS); // 1023
    private static final long SEQ_MASK = ~(-1L << SEQ_BITS);      // 4095
    private static final long BACKWARD_TOLERANCE_MS = 10L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER) {
            throw new IllegalArgumentException("workerId must be 0-" + MAX_WORKER);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            long offset = lastTimestamp - ts;
            if (offset > BACKWARD_TOLERANCE_MS) {
                throw new IllegalStateException("clock moved backwards " + offset + "ms");
            }
            while ((ts = System.currentTimeMillis()) < lastTimestamp) {
                Thread.onSpinWait();
            }
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & SEQ_MASK;
            if (sequence == 0) {
                while ((ts = System.currentTimeMillis()) <= lastTimestamp) {
                    Thread.onSpinWait();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = ts;
        return ((ts - EPOCH) << (WORKER_BITS + SEQ_BITS)) | (workerId << SEQ_BITS) | sequence;
    }

    public String nextIdStr() { return Long.toString(nextId()); }
}
