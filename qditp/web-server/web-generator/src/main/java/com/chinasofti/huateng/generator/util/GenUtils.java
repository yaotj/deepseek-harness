package com.chinasofti.huateng.generator.util;

import java.util.Arrays;
import org.apache.commons.lang3.RegExUtils;
import com.chinasofti.huateng.common.constant.GenConstants;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.generator.config.GenConfig;
import com.chinasofti.huateng.generator.domain.GenTable;
import com.chinasofti.huateng.generator.domain.GenTableColumn;


public class GenUtils
{

    public static void initTable(GenTable genTable, String operName)
    {
        genTable.setClassName(convertClassName(genTable.getTableName()));
        genTable.setPackageName(GenConfig.getPackageName());
        genTable.setModuleName(getModuleName(GenConfig.getPackageName()));
        genTable.setBusinessName(getBusinessName(genTable.getTableName()));
        genTable.setFunctionName(replaceText(genTable.getTableComment()));
        genTable.setFunctionAuthor(GenConfig.getAuthor());
        genTable.setCreateBy(operName);
    }


    public static void initColumnField(GenTableColumn column, GenTable table)
    {
        String dataType = getDbType(column.getColumnType());
        String columnName = column.getColumnName();
        column.setTableId(table.getTableId());
        column.setCreateBy(table.getCreateBy());
        column.setJavaField(StringUtils.toCamelCase(columnName));
        column.setJavaType(GenConstants.TYPE_STRING);
        column.setQueryType(GenConstants.QUERY_EQ);

        if (arraysContains(GenConstants.COLUMNTYPE_STR, dataType) || arraysContains(GenConstants.COLUMNTYPE_TEXT, dataType))
        {
            Integer columnLength = getColumnLength(column.getColumnType());
            String htmlType = columnLength >= 500 || arraysContains(GenConstants.COLUMNTYPE_TEXT, dataType) ? GenConstants.HTML_TEXTAREA : GenConstants.HTML_INPUT;
            column.setHtmlType(htmlType);
        }
        else if (arraysContains(GenConstants.COLUMNTYPE_TIME, dataType))
        {
            column.setJavaType(GenConstants.TYPE_DATE);
            column.setHtmlType(GenConstants.HTML_DATETIME);
        }
        else if (arraysContains(GenConstants.COLUMNTYPE_NUMBER, dataType))
        {
            column.setHtmlType(GenConstants.HTML_INPUT);

            String[] str = StringUtils.split(StringUtils.substringBetween(column.getColumnType(), "(", ")"), ",");
            if (str != null && str.length == 2 && Integer.parseInt(str[1]) > 0)
            {
                column.setJavaType(GenConstants.TYPE_BIGDECIMAL);
            }
            else if (str != null && str.length == 1 && Integer.parseInt(str[0]) <= 10)
            {
                column.setJavaType(GenConstants.TYPE_INTEGER);
            }
            else
            {
                column.setJavaType(GenConstants.TYPE_LONG);
            }
        }

        column.setIsInsert(GenConstants.REQUIRE);

        if (!arraysContains(GenConstants.COLUMNNAME_NOT_EDIT, columnName) && !column.isPk())
        {
            column.setIsEdit(GenConstants.REQUIRE);
        }
        if (!arraysContains(GenConstants.COLUMNNAME_NOT_LIST, columnName) && !column.isPk())
        {
            column.setIsList(GenConstants.REQUIRE);
        }
        if (!arraysContains(GenConstants.COLUMNNAME_NOT_QUERY, columnName) && !column.isPk())
        {
            column.setIsQuery(GenConstants.REQUIRE);
        }

        if (StringUtils.endsWithIgnoreCase(columnName, "name"))
        {
            column.setQueryType(GenConstants.QUERY_LIKE);
        }
        if (StringUtils.endsWithIgnoreCase(columnName, "status"))
        {
            column.setHtmlType(GenConstants.HTML_RADIO);
        }
        else if (StringUtils.endsWithIgnoreCase(columnName, "type")
                || StringUtils.endsWithIgnoreCase(columnName, "sex"))
        {
            column.setHtmlType(GenConstants.HTML_SELECT);
        }
        else if (StringUtils.endsWithIgnoreCase(columnName, "image"))
        {
            column.setHtmlType(GenConstants.HTML_IMAGE_UPLOAD);
        }
        else if (StringUtils.endsWithIgnoreCase(columnName, "file"))
        {
            column.setHtmlType(GenConstants.HTML_FILE_UPLOAD);
        }
        else if (StringUtils.endsWithIgnoreCase(columnName, "content"))
        {
            column.setHtmlType(GenConstants.HTML_EDITOR);
        }
    }


    public static boolean arraysContains(String[] arr, String targetValue)
    {
        return Arrays.asList(arr).contains(targetValue);
    }


    public static String getModuleName(String packageName)
    {
        int lastIndex = packageName.lastIndexOf(".");
        int nameLength = packageName.length();
        return StringUtils.substring(packageName, lastIndex + 1, nameLength);
    }


    public static String getBusinessName(String tableName)
    {
        int lastIndex = tableName.lastIndexOf("_");
        int nameLength = tableName.length();
        return StringUtils.substring(tableName, lastIndex + 1, nameLength);
    }


    public static String convertClassName(String tableName)
    {
        boolean autoRemovePre = GenConfig.getAutoRemovePre();
        String tablePrefix = GenConfig.getTablePrefix();
        if (autoRemovePre && StringUtils.isNotEmpty(tablePrefix))
        {
            String[] searchList = StringUtils.split(tablePrefix, ",");
            tableName = replaceFirst(tableName, searchList);
        }
        return StringUtils.convertToCamelCase(tableName);
    }


    public static String replaceFirst(String replacementm, String[] searchList)
    {
        String text = replacementm;
        for (String searchString : searchList)
        {
            if (replacementm.startsWith(searchString))
            {
                text = replacementm.replaceFirst(searchString, "");
                break;
            }
        }
        return text;
    }


    public static String replaceText(String text)
    {
        return RegExUtils.replaceAll(text, "(?:表|若依)", "");
    }


    public static String getDbType(String columnType)
    {
        if (StringUtils.indexOf(columnType, "(") > 0)
        {
            return StringUtils.substringBefore(columnType, "(");
        }
        else
        {
            return columnType;
        }
    }


    public static Integer getColumnLength(String columnType)
    {
        if (StringUtils.indexOf(columnType, "(") > 0)
        {
            String length = StringUtils.substringBetween(columnType, "(", ")");
            return Integer.valueOf(length);
        }
        else
        {
            return 0;
        }
    }
}
