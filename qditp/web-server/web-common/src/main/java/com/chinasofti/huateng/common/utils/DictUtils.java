package com.chinasofti.huateng.common.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.chinasofti.huateng.common.constant.CacheConstants;
import com.chinasofti.huateng.common.core.domain.entity.SysDictData;
import com.chinasofti.huateng.common.core.cache.LocalCache;
import com.chinasofti.huateng.common.utils.spring.SpringUtils;


public class DictUtils
{

    public static final String SEPARATOR = ",";


    public static void setDictCache(String key, List<SysDictData> dictDatas)
    {
        SpringUtils.getBean(LocalCache.class).setCacheObject(getCacheKey(key), dictDatas);
    }


    public static List<SysDictData> getDictCache(String key)
    {
        return SpringUtils.getBean(LocalCache.class).getCacheObject(getCacheKey(key));
    }


    public static String getDictLabel(String dictType, String dictValue)
    {
        if (StringUtils.isEmpty(dictValue))
        {
            return StringUtils.EMPTY;
        }
        return getDictLabel(dictType, dictValue, SEPARATOR);
    }


    public static String getDictValue(String dictType, String dictLabel)
    {
        if (StringUtils.isEmpty(dictLabel))
        {
            return StringUtils.EMPTY;
        }
        return getDictValue(dictType, dictLabel, SEPARATOR);
    }


    public static String getDictLabel(String dictType, String dictValue, String separator)
    {
        StringBuilder propertyString = new StringBuilder();
        List<SysDictData> datas = getDictCache(dictType);
        if (StringUtils.isNull(datas))
        {
            return StringUtils.EMPTY;
        }
        if (StringUtils.containsAny(separator, dictValue))
        {
            Set<String> values = new HashSet<>(Arrays.asList(dictValue.split(separator)));
            for (SysDictData dict : datas)
            {
                if (values.contains(dict.getDictValue()))
                {
                    propertyString.append(dict.getDictLabel()).append(separator);
                }
            }
        }
        else
        {
            for (SysDictData dict : datas)
            {
                if (dictValue.equals(dict.getDictValue()))
                {
                    return dict.getDictLabel();
                }
            }
        }
        return StringUtils.stripEnd(propertyString.toString(), separator);
    }


    public static String getDictValue(String dictType, String dictLabel, String separator)
    {
        StringBuilder propertyString = new StringBuilder();
        List<SysDictData> datas = getDictCache(dictType);
        if (StringUtils.isNull(datas))
        {
            return StringUtils.EMPTY;
        }
        if (StringUtils.containsAny(separator, dictLabel))
        {
            Set<String> labels = new HashSet<>(Arrays.asList(dictLabel.split(separator)));
            for (SysDictData dict : datas)
            {
                if (labels.contains(dict.getDictLabel()))
                {
                    propertyString.append(dict.getDictValue()).append(separator);
                }
            }
        }
        else
        {
            for (SysDictData dict : datas)
            {
                if (dictLabel.equals(dict.getDictLabel()))
                {
                    return dict.getDictValue();
                }
            }
        }
        return StringUtils.stripEnd(propertyString.toString(), separator);
    }


    public static String getDictValues(String dictType)
    {
        StringBuilder propertyString = new StringBuilder();
        List<SysDictData> datas = getDictCache(dictType);
        if (StringUtils.isNull(datas))
        {
            return StringUtils.EMPTY;
        }
        for (SysDictData dict : datas)
        {
            propertyString.append(dict.getDictValue()).append(SEPARATOR);
        }
        return StringUtils.stripEnd(propertyString.toString(), SEPARATOR);
    }


    public static String getDictLabels(String dictType)
    {
        StringBuilder propertyString = new StringBuilder();
        List<SysDictData> datas = getDictCache(dictType);
        if (StringUtils.isNull(datas))
        {
            return StringUtils.EMPTY;
        }
        for (SysDictData dict : datas)
        {
            propertyString.append(dict.getDictLabel()).append(SEPARATOR);
        }
        return StringUtils.stripEnd(propertyString.toString(), SEPARATOR);
    }


    public static void removeDictCache(String key)
    {
        SpringUtils.getBean(LocalCache.class).deleteObject(getCacheKey(key));
    }


    public static void clearDictCache()
    {
        Collection<String> keys = SpringUtils.getBean(LocalCache.class).keys(CacheConstants.SYS_DICT_KEY + "*");
        SpringUtils.getBean(LocalCache.class).deleteObject(keys);
    }


    public static String getCacheKey(String configKey)
    {
        return CacheConstants.SYS_DICT_KEY + configKey;
    }
}

