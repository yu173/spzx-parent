package com.spzx.report.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.report.aitools.*;
import com.spzx.report.entity.VOrderInfo;
import com.spzx.report.entity.VOrderInfoJSONObject;
import com.spzx.report.mapper.VOrderInfoMapper;
import com.spzx.report.service.SpzxReportService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SpzxReportServiceImpl extends ServiceImpl<VOrderInfoMapper, VOrderInfo> implements SpzxReportService {

    @Autowired
    VOrderInfoMapper vOrderInfoMapper;


    @Override
    public List<VOrderInfo> getAllOrderInfo() {
        return vOrderInfoMapper.selectList(null);
    }

    @Override
    public String hello(String question) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(SpzxAiConst.DASHSCOPE_API_BASE_URL)
                .apiKey(SpzxAiConst.DASHSCOPE_API_KEY)
//                .modelName("deepseek-v3")
                .modelName(SpzxAiConst.QWEN_PLUS)
                .build();
        //String answer = chatModel.chat("什么是人生，人活着有什么意思？不停抱怨的人性情总是不好该怎么办");

        //创建一个助手代理
        MyAssistAgent agent = AiServices.builder(MyAssistAgent.class)
                .chatModel(chatModel)
                .systemMessageProvider((t) -> "我是人类：")//提示词，问问题的上下文说明
                .build();
        String answer = agent.chat(question);
        return answer;
    }

    @Override
    public List<Map<String, Object>> getAiReport(String question) {
        //1、ai解析提示词->生成json
        SpzxAiQuest spzxAiQuest = new SpzxAiQuest();
        String answerJson = spzxAiQuest.processQuestion(question);
        VOrderInfoJSONObject vOrderInfoJSONObject = JSON.parseObject(answerJson, VOrderInfoJSONObject.class);
        System.out.println("vOrderInfoJSONObject" + vOrderInfoJSONObject);

        //2、TODO
        //根据向量匹配的字段生成SQL语句，根据SQL查询v_order_info
        //变身，数据清洗，将解析语义关键词，到向量数据库中去匹配需要数据(统计数据，分组字段，条件值)，重新安装到javabean中。
        vOrderInfoJSONObject = getVectorData(vOrderInfoJSONObject);

        //生成动态SQL，需要到关系型数据库宽表（v_order_info）中查找需要的统计数据
        QueryWrapper<VOrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper = getMyReportData(queryWrapper, vOrderInfoJSONObject);
        List<Map<String, Object>> maps = baseMapper.selectMaps(queryWrapper);
        return maps;
    }

    //拼接SQL组装到queryWrapper
    private QueryWrapper<VOrderInfo> getMyReportData(QueryWrapper<VOrderInfo> queryWrapper, VOrderInfoJSONObject vOrderInfoJSONObject) {
        //获取统计字段：order_id(统计)  user_id（统计）   order_amount（求和）
        String selectSql = "";
        String countKeyword = vOrderInfoJSONObject.getCountKeyword();
        if ("order_amount".equals(countKeyword)) {
            selectSql += "sum(" + countKeyword + ") as count";
        } else {
            selectSql += "count(" + countKeyword + ") as count";
        }

        //获取分组字段:tm_name  sku_name  province_name  category_keyword  gender_name ...
        String groupKeyword = vOrderInfoJSONObject.getGroupKeyword();
        String groupSql = "";
        if ("create_date".equals(groupKeyword)) {
            queryWrapper.select(selectSql, "DATE_FORMAT(" + groupKeyword + ",'%Y-%m-%d') as groupTag")
                    .groupBy("DATE_FORMAT(" + groupKeyword + ",'%Y-%m-%d')");
        } else {
            queryWrapper.select(selectSql, groupKeyword + " as groupTag")
                    .groupBy(groupKeyword);
        }

        //获取条件查询:
        String provinceName = vOrderInfoJSONObject.getProvinceName();
       /* if (StringUtils.hasText(provinceName)) {
            queryWrapper.eq("province_name", provinceName);// where province_name = ?
        }*/
        queryWrapper.eq(StringUtils.hasText(provinceName), "province_name", provinceName);

        String skuName = vOrderInfoJSONObject.getSkuName();
        /*if (StringUtils.hasText(skuName)) {
            queryWrapper.eq("sku_name", skuName);// where sku_name = ?
        }*/
        queryWrapper.eq(StringUtils.hasText(skuName), "sku_name", skuName);

        String tmName = vOrderInfoJSONObject.getTmName();
        /*if (StringUtils.hasText(tmName)) {
            queryWrapper.eq("tm_name", tmName);// where tm_name = ?
        }*/
        queryWrapper.eq(StringUtils.hasText(tmName), "tm_name", tmName);

        //这里不需要考虑where与groupBy  的拼接顺序，只要把语句写入queryWrapper中，MyBatisPlus会自动按照SQL的顺序去拼接
        /**
         * select后面的字段要求：
         * 1、统计报表，只有两个轴的数据，所以只要两个字段的数据
         * 2、group by分组对select字段是有要求的：只能包含聚合函数（max()/count())  min(())  avg()  sum()  max()  count()）、和分组相关的字段
         */
        return queryWrapper;
    }

    //变身，数据清洗，将解析语义关键词，到向量数据库中去匹配需要数据，重新安装到javabean中。
    private VOrderInfoJSONObject getVectorData(VOrderInfoJSONObject vOrderInfoJSONObject) {
        String countKeyword = vOrderInfoJSONObject.getCountKeyword();
        String countColumn = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(countKeyword, PineconeConst.PCK_INDEX_COUNT_KEY_1).get("column");
        if (StringUtils.isEmpty(countColumn)) {
            countColumn = "order_amount";//4. 如果用户未提及聚合内容，最热门，销量最好等词语也可以做为聚合内容，如果全未提及，则默认为销售金额
        }
        vOrderInfoJSONObject.setCountKeyword(countColumn.trim());

        String groupKeyword = vOrderInfoJSONObject.getGroupKeyword();
        String groupColumn = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(groupKeyword, PineconeConst.PGK_INDEX_GROUP_KEY_1).get("column");
        if (StringUtils.isEmpty(groupColumn)) {
            groupColumn = "create_date";//3. 如果用户未提及分组条件，默认以时间进行分组，将与时间有关的信息写入该字段，如果用户未提及时间默认为最近一周
        }
        vOrderInfoJSONObject.setGroupKeyword(groupColumn.trim());

        //为了简化开发，演示三个字段，其他可以自行补充
        String provinceName = vOrderInfoJSONObject.getProvinceName();
        String tmName = vOrderInfoJSONObject.getTmName();
        String skuName = vOrderInfoJSONObject.getSkuName();

        if (StringUtils.hasText(provinceName)) {
            String provinceNameEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(provinceName, PineconeConst.PSK_INDEX_SELECT_KEY_1).get("word");
            vOrderInfoJSONObject.setProvinceName(provinceNameEmbeddings.trim());// 地区条件关键字
        }


        if (StringUtils.hasText(tmName)) {
            String tmNameEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(tmName, PineconeConst.PSK_INDEX_SELECT_KEY_1).get("word");
            vOrderInfoJSONObject.setTmName(tmNameEmbeddings.trim());// 商标条件关键字
        }


        if (StringUtils.hasText(skuName)) {
            String skuNameEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(skuName, PineconeConst.PSK_INDEX_SELECT_KEY_1).get("word");
            vOrderInfoJSONObject.setSkuName(skuNameEmbeddings.trim());// 商品条件关键字
        }

        return vOrderInfoJSONObject;
    }
}
