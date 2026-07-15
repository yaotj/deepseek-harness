package com.chinasofti.huateng.common.core.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JVM local cache for single-instance deployments.
 */
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
public class LocalCache
{
    private static final long NEVER_EXPIRE = -1L;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public <T> void setCacheObject(final String key, final T value)
    {
        cache.put(key, new CacheEntry(value, NEVER_EXPIRE));
    }

    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit)
    {
        long expireAt = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        cache.put(key, new CacheEntry(value, expireAt));
    }

    public boolean expire(final String key, final long timeout)
    {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    public boolean expire(final String key, final long timeout, final TimeUnit unit)
    {
        CacheEntry entry = getEntry(key);
        if (entry == null)
        {
            return false;
        }
        entry.expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
        return true;
    }

    public long getExpire(final String key)
    {
        CacheEntry entry = getEntry(key);
        if (entry == null)
        {
            return -2L;
        }
        if (entry.expireAt == NEVER_EXPIRE)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(entry.expireAt - System.currentTimeMillis()));
    }

    public Boolean hasKey(String key)
    {
        return getEntry(key) != null;
    }

    public <T> T getCacheObject(final String key)
    {
        CacheEntry entry = getEntry(key);
        return entry == null ? null : (T) entry.value;
    }

    public boolean deleteObject(final String key)
    {
        return cache.remove(key) != null;
    }

    public boolean deleteObject(final Collection collection)
    {
        if (collection == null || collection.isEmpty())
        {
            return false;
        }
        boolean removed = false;
        for (Object key : collection)
        {
            removed = cache.remove(String.valueOf(key)) != null || removed;
        }
        return removed;
    }

    public <T> long setCacheList(final String key, final List<T> dataList)
    {
        List<T> value = dataList == null ? Collections.emptyList() : new ArrayList<>(dataList);
        setCacheObject(key, value);
        return value.size();
    }

    public <T> List<T> getCacheList(final String key)
    {
        List<T> value = getCacheObject(key);
        return value == null ? Collections.emptyList() : value;
    }

    public <T> long setCacheSet(final String key, final Set<T> dataSet)
    {
        Set<T> value = dataSet == null ? Collections.emptySet() : new HashSet<>(dataSet);
        setCacheObject(key, value);
        return value.size();
    }

    public <T> Set<T> getCacheSet(final String key)
    {
        Set<T> value = getCacheObject(key);
        return value == null ? Collections.emptySet() : value;
    }

    public <T> void setCacheMap(final String key, final Map<String, T> dataMap)
    {
        if (dataMap != null)
        {
            setCacheObject(key, new HashMap<>(dataMap));
        }
    }

    public <T> Map<String, T> getCacheMap(final String key)
    {
        Map<String, T> value = getCacheObject(key);
        return value == null ? Collections.emptyMap() : value;
    }

    public <T> void setCacheMapValue(final String key, final String hKey, final T value)
    {
        Map<String, T> map = getCacheMap(key);
        Map<String, T> copy = new HashMap<>(map);
        copy.put(hKey, value);
        setCacheObject(key, copy);
    }

    public <T> T getCacheMapValue(final String key, final String hKey)
    {
        return this.<T>getCacheMap(key).get(hKey);
    }

    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys)
    {
        Map<String, T> map = getCacheMap(key);
        List<T> values = new ArrayList<>();
        for (Object hKey : hKeys)
        {
            values.add(map.get(String.valueOf(hKey)));
        }
        return values;
    }

    public boolean deleteCacheMapValue(final String key, final String hKey)
    {
        Map<String, Object> map = getCacheMap(key);
        if (!map.containsKey(hKey))
        {
            return false;
        }
        Map<String, Object> copy = new HashMap<>(map);
        copy.remove(hKey);
        setCacheObject(key, copy);
        return true;
    }

    public Collection<String> keys(final String pattern)
    {
        cleanExpiredEntries();
        Pattern regex = Pattern.compile(toRegex(pattern));
        return cache.keySet().stream().filter(key -> regex.matcher(key).matches()).toList();
    }

    public long size()
    {
        cleanExpiredEntries();
        return cache.size();
    }

    public void clear()
    {
        cache.clear();
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredEntries()
    {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private CacheEntry getEntry(String key)
    {
        CacheEntry entry = cache.get(key);
        if (entry == null)
        {
            return null;
        }
        if (entry.isExpired())
        {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    private String toRegex(String pattern)
    {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray())
        {
            if (c == '*')
            {
                regex.append(".*");
            }
            else
            {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.toString();
    }

    private static class CacheEntry
    {
        private final Object value;

        private volatile long expireAt;

        private CacheEntry(Object value, long expireAt)
        {
            this.value = value;
            this.expireAt = expireAt;
        }

        private boolean isExpired()
        {
            return expireAt != NEVER_EXPIRE && System.currentTimeMillis() >= expireAt;
        }
    }
}
