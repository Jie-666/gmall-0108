package com.atguigu.gmall.scheduled.jobHandler;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MyJobHandler {

    @XxlJob("myJobHandler")
    public ReturnT<String> test(String param){
        System.out.println("这是定时任务================="+ System.currentTimeMillis());
        XxlJobLogger.log("this is log");
        return ReturnT.SUCCESS;

    }
}
