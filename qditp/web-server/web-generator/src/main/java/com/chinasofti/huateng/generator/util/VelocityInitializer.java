package com.chinasofti.huateng.generator.util;

import java.util.Properties;
import org.apache.velocity.app.Velocity;
import com.chinasofti.huateng.common.constant.Constants;


public class VelocityInitializer
{

    public static void initVelocity()
    {
        Properties p = new Properties();
        try
        {
            p.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
            p.setProperty(Velocity.INPUT_ENCODING, Constants.UTF8);
            Velocity.init(p);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
