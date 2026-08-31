package com.spzx.report.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.report.entity.VOrderInfo;

import java.util.List;
import java.util.Map;

public interface SpzxReportService extends IService<VOrderInfo> {


    List<VOrderInfo> getAllOrderInfo();

    String hello(String question);

    List<Map<String, Object>> getAiReport(String question);
}
