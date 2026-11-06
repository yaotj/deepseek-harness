package com.chinasofti.huateng.micro.web.validation;

import com.chinasofti.huateng.micro.web.validation.Checker;
import com.chinasofti.huateng.micro.web.validation.SimpleModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class CheckerTest {

    @Test(expected = Exception.class)
    public void testCheckNo() throws Exception {
        SimpleModel simpleModel = new SimpleModel();
        simpleModel.setName("s(");
        Checker.check(simpleModel);
    }

    @Test
    public void testCheckYes() throws Exception {
        SimpleModel simpleModel = new SimpleModel();
        simpleModel.setName("s");
        Checker.check(simpleModel);
    }
}
