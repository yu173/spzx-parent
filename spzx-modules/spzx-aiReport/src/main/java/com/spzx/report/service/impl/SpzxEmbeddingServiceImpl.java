package com.spzx.report.service.impl;

import com.spzx.report.aitools.PineconeConst;
import com.spzx.report.aitools.PineconeUploadUtil;
import com.spzx.report.mapper.VectorCountKeyMapper;
import com.spzx.report.mapper.VectorGroupKeyMapper;
import com.spzx.report.mapper.VectorSelectKeyMapper;
import com.spzx.report.service.SpzxEmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/*
从关系型数据库中查询列表数据，保存到向量数据库中去。
 */
@Service
public class SpzxEmbeddingServiceImpl implements SpzxEmbeddingService {

    @Autowired
    VectorCountKeyMapper vectorCountKeyMapper;

    @Autowired
    VectorSelectKeyMapper vectorSelectKeyMapper;

    @Autowired
    VectorGroupKeyMapper vectorGroupKeyMapper;

    @Override
    public void embeddingGroupKeyToPinecone() {
        // 1 查询需要向量化的输入
        List<Map<String, Object>> list = vectorGroupKeyMapper.selectMaps(null);
        // 2 存入pinecone
        PineconeUploadUtil.uploadListToPinecone(list, PineconeConst.PGK_INDEX_UPSERT_URL, PineconeConst.PK1);
    }

    @Override
    public void embeddingCountKeyToPinecone() {
        // 1 查询需要向量化的输入
        List<Map<String, Object>> list = vectorCountKeyMapper.selectMaps(null);
        // 2 存入pinecone
        PineconeUploadUtil.uploadListToPinecone(list, PineconeConst.PCK_INDEX_UPSERT_URL, PineconeConst.PK1);
    }

    @Override
    public void embeddingSelectKeyToPinecone() {
        // 1 查询需要向量化的输入
        List<Map<String, Object>> list = vectorSelectKeyMapper.selectMaps(null);
        // 2 存入pinecone
        PineconeUploadUtil.uploadListToPinecone(list, PineconeConst.PSK_INDEX_UPSERT_URL, PineconeConst.PK1);
    }

}