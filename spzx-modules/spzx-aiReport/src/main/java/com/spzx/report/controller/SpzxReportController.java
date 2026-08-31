package com.spzx.report.controller;

import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.report.entity.VOrderInfo;
import com.spzx.report.service.SpzxReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "智能统计模块")
@RestController
//@RequestMapping("/report")
@RequestMapping
public class SpzxReportController extends BaseController {

    @Autowired
    SpzxReportService spzxReportService;

    @GetMapping("/getAllOrderInfo")
    public AjaxResult getAllOrderInfo(){
        List<VOrderInfo> orderInfoList =  spzxReportService.getAllOrderInfo();
        return success(orderInfoList);
    }

    //测试LLM调用
    @GetMapping("/hello/{question}")
    public AjaxResult hello(@PathVariable("question") String question){
        String answer =  spzxReportService.hello(question);
        return success(answer);
    }

    /**
     * 获取智能报表
     * @param question
     * @return
     */
    @Operation(summary = "智能报表统计")
    @GetMapping("getAiReport/{question}")
    public AjaxResult getAiReport(@PathVariable String question){
        Map<String,Object> map = new HashMap<>();
        List<String> xList = new ArrayList<>();// X轴数据
        List<Object> yList = new ArrayList<>();// Y轴数据       整数值（销售数量）       浮点值（总金额）
        List<Map<String,Object>> list =  spzxReportService.getAiReport(question);
        for(Map<String,Object> mapObj:list){
            String groupTag = mapObj.get("groupTag").toString();
            String count = mapObj.get("count").toString();
            xList.add(groupTag);
            yList.add(count);
        }
        map.put("xList",xList);
        map.put("yList",yList);
        return success(map);
    }
}
