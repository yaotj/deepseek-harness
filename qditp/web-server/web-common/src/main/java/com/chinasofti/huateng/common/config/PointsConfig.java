package com.chinasofti.huateng.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "points")
public class PointsConfig
{
    private String name;

    private String version;

    private static String profile;

    private static boolean addressEnabled;

    private static String captchaType;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public static String getProfile()
    {
        return profile;
    }

    public void setProfile(String profile)
    {
        PointsConfig.profile = profile;
    }

    public static boolean isAddressEnabled()
    {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled)
    {
        PointsConfig.addressEnabled = addressEnabled;
    }

    public static String getCaptchaType()
    {
        return captchaType != null ? captchaType : "char";
    }

    public void setCaptchaType(String captchaType)
    {
        PointsConfig.captchaType = captchaType;
    }


    public static String getImportPath()
    {
        return getProfile() + "/import";
    }


    public static String getAvatarPath()
    {
        return getProfile() + "/avatar";
    }


    public static String getDownloadPath()
    {
        return getProfile() + "/download/";
    }


    public static String getUploadPath()
    {
        return getProfile() + "/upload";
    }
}