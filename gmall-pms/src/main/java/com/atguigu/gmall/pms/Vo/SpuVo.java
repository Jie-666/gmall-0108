package com.atguigu.gmall.pms.Vo;

import com.atguigu.gmall.pms.entity.SpuEntity;
import lombok.Data;

import java.util.List;

/**
 * SpuEntity扩展对象
 * 包含：SpuEntity的基本属性，spuInages图片信息，baseAttrs基本属性信息，skus信息
 */
@Data
public class SpuVo extends SpuEntity {

    private List<String> spuImages;

    private List<SpuAttrValueVo> baseAttrs;

    private List<SkuVo> skus;
}
