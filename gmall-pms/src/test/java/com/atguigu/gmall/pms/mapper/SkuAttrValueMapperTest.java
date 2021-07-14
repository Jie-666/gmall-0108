package com.atguigu.gmall.pms.mapper;

import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class SkuAttrValueMapperTest {

    @Autowired
    private SkuAttrValueMapper attrValueMapper;
    @Test
    void queryMappingBySpuId() {
        System.out.println(this.attrValueMapper.queryMappingBySpuId(Arrays.asList(31l, 32l, 33l, 34l)));
    }
}