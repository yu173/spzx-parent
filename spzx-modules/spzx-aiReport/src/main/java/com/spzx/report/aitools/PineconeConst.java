package com.spzx.report.aitools;

// 向量数据库的常量
public class PineconeConst {
    // pinecone-key
    public static final String PK1 = "pcsk_72enrk_R2ygteRd6DpDVMWpqnEUmWzX9izw3uDVaQ4M6Hr9T3PfdEraNrafzfZpbYXKoJa";

    // pinecone-db group-key 上传upsert
    public static final String PGK_INDEX_UPSERT_URL = "https://spzx-report-group-key-1-hnztjk2.svc.aped-4627-b74a.pinecone.io/vectors/upsert";
    // pinecone-db count-key 上传upsert
    public static final String PCK_INDEX_UPSERT_URL = "https://spzx-report-count-key-1-hnztjk2.svc.aped-4627-b74a.pinecone.io/vectors/upsert";
    // pinecone-db select-key 上传upsert
    public static final String PSK_INDEX_UPSERT_URL = "https://spzx-report-select-key-1-hnztjk2.svc.aped-4627-b74a.pinecone.io/vectors/upsert";

    // pinecone-db group-key 查询query
    public static final String PGK_INDEX_QUERY_URL = "https://spzx-report-group-key-1-hnztjk2.svc.aped-4627-b74a.pinecone.io/query";

    // pinecone-db count-key 查询query
    public static final String PCK_INDEX_QUERY_URL = "https://spzx-report-count-key-1-hnztjk2.svc.aped-4627-b74a.pinecone.io/query";

    // pinecone-db select-key 查询query
    public static final String PSK_INDEX_QUERY_URL = "https://spzx-report-select-key-1-hnztjk2.svc.aped-4627-b74a.pinecone.io/query";

    public static final String PGK_INDEX_GROUP_KEY_1 = "spzx-report-group-key-1";
    public static final String PCK_INDEX_COUNT_KEY_1 = "spzx-report-count-key-1";
    public static final String PSK_INDEX_SELECT_KEY_1 = "spzx-report-select-key-1";
}