package com.atguigu.gmall.pms.controller;

import java.util.List;

import com.atguigu.gmall.pms.Vo.SpuVo;
import com.atguigu.gmall.pms.entity.SpuDescEntity;
import com.atguigu.gmall.pms.entity.SpuEntity;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.atguigu.gmall.pms.service.SpuService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.PageParamVo;

/**
 * spu信息
 *
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-06-22 18:47:27
 */
@Api(tags = "spu信息 管理")
@RestController
@RequestMapping("pms/spu")
public class SpuController {

    @Autowired
    private SpuService spuService;
    @Autowired
    private RabbitTemplate rabbitTemplate;


    @ApiOperation("根据检索条件分页查询商品列表")
    @GetMapping("category/{categoryId}")
    public ResponseVo<PageResultVo> querySpuByCidAndPage(PageParamVo paramVo, @PathVariable("categoryId") Long categoryId) {
        PageResultVo pageResultVo = spuService.querySpuByCidAndPage(paramVo, categoryId);

        return ResponseVo.ok(pageResultVo);
    }

    /**
     * 列表
     */
    @GetMapping
    @ApiOperation("分页查询")
    public ResponseVo<PageResultVo> querySpuByPage(PageParamVo paramVo) {
        PageResultVo pageResultVo = spuService.queryPage(paramVo);

        return ResponseVo.ok(pageResultVo);
    }

    @PostMapping("page")
    @ApiOperation("搜索功能远程调用")
    public ResponseVo<List<SpuEntity>> querySpuByPageJson(@RequestBody PageParamVo paramVo) {
        PageResultVo pageResultVo = spuService.queryPage(paramVo);

        return ResponseVo.ok((List<SpuEntity>) pageResultVo.getList());
    }


    /**
     * 信息
     */
    @GetMapping("{id}")
    @ApiOperation("详情查询")
    public ResponseVo<SpuEntity> querySpuById(@PathVariable("id") Long id) {
        SpuEntity spu = spuService.getById(id);

        return ResponseVo.ok(spu);
    }

    /**
     * 保存
     */
    @PostMapping
    @ApiOperation("保存")
    public ResponseVo<Object> save(@RequestBody SpuVo spu) {
        spuService.bigSave(spu);

        return ResponseVo.ok();
    }

    /**
     * 修改
     */
    @PostMapping("/update")
    @ApiOperation("修改")
    public ResponseVo update(@RequestBody SpuEntity spu) {
        spuService.updateById(spu);

        //当商品价格发生修改时，设置实时价格，通过MQ
        //this.redisTemplate.convertAndSend("PMS_ITEM_EXCHANGE","item.update",spu.getId());
        //交换机、路由、消息内容
        this.rabbitTemplate.convertAndSend("PMS_ITEM_EXCHANGE", "item.update", spu.getId());

        return ResponseVo.ok();
    }

    /**
     * 删除
     */
    @PostMapping("/delete")
    @ApiOperation("删除")
    public ResponseVo delete(@RequestBody List<Long> ids) {
        spuService.removeByIds(ids);

        return ResponseVo.ok();
    }

}
