package com.atguigu.gmall.pms.api;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface GmallPmsApi {
    @GetMapping("pms/spu/page")
    @ApiOperation("搜索功能远程调用")
    public ResponseVo<List<SpuEntity>> querySpuByPageJson(@RequestBody PageParamVo paramVo);

    @ApiOperation("搜索功能远程调用spu下的sku集合")
    @GetMapping("pms/sku/spu/{spuId}")
    public ResponseVo<List<SkuEntity>> querySkusBySpuId(@PathVariable("spuId") Long spuId);

    @GetMapping("pms/brand/{id}")
    @ApiOperation("搜索功能远程调用根据品牌id查询品牌详情")
    public ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

    @GetMapping("pms/category/{id}")
    @ApiOperation("搜索功能远程调用根据分类id查询分类详情")
    public ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);

    @GetMapping("pms/skuattrvalue/search/{cid}")
    public ResponseVo<List<SkuAttrValueEntity>> querySearchAttrValuesBySkuId(
            @PathVariable("cid")Long cid,
            @RequestParam("skuId")Long skuId
    );
    @GetMapping("pms/spuattrvalue/search/{cid}")
    public ResponseVo<List<SpuAttrValueEntity>> querySearchAttrValuesBySpuId(
            @PathVariable("cid") Long cid,
            @RequestParam("spuId") Long spuId
    );
}
