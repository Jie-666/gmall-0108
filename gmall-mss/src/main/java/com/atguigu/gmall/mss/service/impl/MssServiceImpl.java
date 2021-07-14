package com.atguigu.gmall.mss.service.impl;

import com.aliyun.oss.ClientException;
import com.atguigu.gmall.common.utils.HttpUtils;
import com.atguigu.gmall.mss.service.MssService;
import com.atguigu.gmall.mss.utils.MssProperties;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MssServiceImpl implements MssService {
    @Autowired
    private MssProperties mssProperties;

    @Override
    public void send(String mobile, String checkCode) throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + mssProperties.appCode);
        Map<String, String> querys = new HashMap<String, String>();
        querys.put("mobile", mobile);
        querys.put("param", "code:" + checkCode);
        querys.put("tpl_id", mssProperties.templateId);
        Map<String, String> bodys = new HashMap<String, String>();

        HttpResponse response = HttpUtils.doPost(mssProperties.host, mssProperties.path, mssProperties.method, headers, querys, bodys);
        System.out.println(response.toString());
        String data = EntityUtils.toString(response.getEntity(), "UTF-8");
        Gson gson = new Gson();
        HashMap<String, String> map = gson.fromJson(data, HashMap.class);
        String code = map.get("return_code");

        if ("10001".equals(code)) {
            log.error("手机号码格式错误");
            throw new RuntimeException("手机号码格式错误");
        }

        if (!"00000".equals(code)) {
            log.error("短信发送失败 " + " - code: " + code);
            throw new RuntimeException("短信发送失败");
        }
    }
}