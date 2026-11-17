package com.chinasofti.huateng.acc.security.server.config;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public class LocalCache {


    private static final long DEFAULT_TIMEOUT = 3 * 1000;


    private static final long TASK_TIME = 1;

    private static final ConcurrentHashMap<String, CacheEntity> cacheMap = new ConcurrentHashMap<>();
    private static AtomicLong sequence = new AtomicLong();
    private static volatile LocalCache instance = new LocalCache();

    private LocalCache() {
        new Thread(new TimeoutTimer()).start();
    }

    public static String getSequence() {
        long temp = sequence.incrementAndGet();
        return StringUtils.leftPad(String.valueOf(temp), 14, "0");
    }


    public static boolean set(String key, Object value, long expire) {
        cacheMap.put(key, setEntity(key, value, expire));
        return true;
    }

    public static boolean set(String key, Object value) {
        cacheMap.put(key, setEntity(key, value, DEFAULT_TIMEOUT));
        return true;
    }

    private static CacheEntity setEntity(String key, Object value, long expire) {

        CacheEntity entity = new CacheEntity();
        entity.setValue(value);
        entity.setExpire(expire);
        entity.setTimeStamp(System.currentTimeMillis());

        return entity;
    }

    public static Object get(String key) {
        CacheEntity entity = cacheMap.get(key);

        if (entity == null) {
            return null;
        } else {
            Object value = entity.getValue();
            if (value == null) {
                return null;
            }
            return value;
        }
    }

    public static void remove(String key) {
        cacheMap.remove(key);
    }

    static class TimeoutTimer implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(TASK_TIME);
                    for (String key : cacheMap.keySet()) {
                        CacheEntity entity = cacheMap.get(key);
                        long now = System.currentTimeMillis();
                        if ((now - entity.getTimeStamp()) >= entity.getExpire()) {
                            cacheMap.remove(key);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    static class CacheEntity {


        private Object value;


        private long expire;


        private long timeStamp;

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public long getExpire() {
            return expire;
        }

        public void setExpire(long expire) {
            this.expire = expire;
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public void setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
        }
    }

}