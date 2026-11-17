package com.chinasofti.huateng.framework.web.domain.server;

import java.lang.management.ManagementFactory;
import com.chinasofti.huateng.common.utils.Arith;
import com.chinasofti.huateng.common.utils.DateUtils;


public class Jvm
{

    private double total;


    private double max;


    private double free;


    private String version;


    private String home;

    public double getTotal()
    {
        return Arith.div(total, (1024 * 1024), 2);
    }

    public void setTotal(double total)
    {
        this.total = total;
    }

    public double getMax()
    {
        return Arith.div(max, (1024 * 1024), 2);
    }

    public void setMax(double max)
    {
        this.max = max;
    }

    public double getFree()
    {
        return Arith.div(free, (1024 * 1024), 2);
    }

    public void setFree(double free)
    {
        this.free = free;
    }

    public double getUsed()
    {
        return Arith.div(total - free, (1024 * 1024), 2);
    }

    public double getUsage()
    {
        return Arith.mul(Arith.div(total - free, total, 4), 100);
    }


    public String getName()
    {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getHome()
    {
        return home;
    }

    public void setHome(String home)
    {
        this.home = home;
    }


    public String getStartTime()
    {
        return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, DateUtils.getServerStartDate());
    }


    public String getRunTime()
    {
        return DateUtils.timeDistance(DateUtils.getNowDate(), DateUtils.getServerStartDate());
    }


    public String getInputArgs()
    {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
    }
}
