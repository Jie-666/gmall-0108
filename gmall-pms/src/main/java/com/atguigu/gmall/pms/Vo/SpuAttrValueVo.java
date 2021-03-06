package com.atguigu.gmall.pms.Vo;

import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Data
public class SpuAttrValueVo extends SpuAttrValueEntity {

    private List<String> valueSelected;

    public void setValueSelected(List<String> valueSelected) {
        if ((CollectionUtils.isEmpty(valueSelected))){
            return;
        }
        this.setAttrValue(StringUtils.join(valueSelected,','));
    }
}
