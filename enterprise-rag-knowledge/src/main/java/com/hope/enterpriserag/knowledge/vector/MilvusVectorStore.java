package com.hope.enterpriserag.knowledge.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hope.enterpriserag.knowledge.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus 2.6 向量存储实现。
 * Collection 使用子块 ID 作为主键并建立租户、知识库、文档状态、安全等级和有效期过滤索引。
 */
@Slf4j
@RequiredArgsConstructor
public class MilvusVectorStore implements VectorStore {
    private static final String CHUNK_ID = "chunk_id";
    private static final String VECTOR = "embedding";
    private static final String TENANT_ID = "tenant_id";
    private static final String KNOWLEDGE_BASE_ID = "knowledge_base_id";
    private static final String DOCUMENT_ID = "document_id";
    private static final String DOCUMENT_STATUS = "document_status";
    private static final String SECURITY_LEVEL = "security_level";
    private static final String EFFECTIVE_FROM = "effective_from";
    private static final String EFFECTIVE_TO = "effective_to";

    private final MilvusClientV2 client;
    private final MilvusProperties properties;
    private final Gson gson = new Gson();
    private volatile boolean collectionReady;
    private volatile int configuredDimensions;

    @Override
    public void ensureReady(int dimensions) {
        if (collectionReady) {
            verifyConfiguredDimension(dimensions);
            return;
        }
        synchronized (this) {
            if (collectionReady) {
                verifyConfiguredDimension(dimensions);
                return;
            }
            Boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .databaseName(properties.getDatabaseName())
                    .collectionName(properties.getCollectionName())
                    .build());
            if (!Boolean.TRUE.equals(exists)) {
                createCollection(dimensions);
                log.info("Milvus Collection 创建成功: database={}, collection={}, dimensions={}",
                        properties.getDatabaseName(), properties.getCollectionName(), dimensions);
            } else {
                validateExistingCollection(dimensions);
                log.info("复用已有 Milvus Collection: database={}, collection={}",
                        properties.getDatabaseName(), properties.getCollectionName());
            }
            configuredDimensions = dimensions;
            collectionReady = true;
        }
    }

    @Override
    public void deleteDocument(Long tenantId, Long documentId) {
        requireReady();
        client.delete(DeleteReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .filter(TENANT_ID + " == {tenantId} && " + DOCUMENT_ID + " == {documentId}")
                .filterTemplateValues(Map.of("tenantId", tenantId, "documentId", documentId))
                .build());
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        requireReady();
        List<JsonObject> rows = records.stream().map(this::toRow).toList();
        client.upsert(UpsertReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .data(rows)
                .build());
    }

    @Override
    public void updateMetadata(List<VectorMetadata> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        requireReady();
        List<JsonObject> rows = metadata.stream().map(item -> {
            JsonObject row = new JsonObject();
            row.addProperty(CHUNK_ID, item.chunkId());
            row.addProperty(DOCUMENT_STATUS, item.documentStatus());
            row.addProperty(EFFECTIVE_FROM, epochDay(item.effectiveFrom()));
            row.addProperty(EFFECTIVE_TO, epochDay(item.effectiveTo()));
            return row;
        }).toList();
        client.upsert(UpsertReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .data(rows)
                .partialUpdate(true)
                .build());
    }

    private void createCollection(int dimensions) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.setEnableDynamicField(false);
        schema.addField(field(CHUNK_ID, DataType.Int64, true, null));
        schema.addField(field(TENANT_ID, DataType.Int64, false, null));
        schema.addField(field(KNOWLEDGE_BASE_ID, DataType.Int64, false, null));
        schema.addField(field(DOCUMENT_ID, DataType.Int64, false, null));
        schema.addField(field("parent_chunk_id", DataType.Int64, false, null));
        schema.addField(field("chunk_index", DataType.Int64, false, null));
        schema.addField(field("version", DataType.VarChar, false, 128));
        schema.addField(field(DOCUMENT_STATUS, DataType.VarChar, false, 32));
        schema.addField(field("department", DataType.VarChar, false, 128));
        schema.addField(field(SECURITY_LEVEL, DataType.Int64, false, null));
        schema.addField(field("authority_level", DataType.Int64, false, null));
        schema.addField(field(EFFECTIVE_FROM, DataType.Int64, false, null));
        schema.addField(field(EFFECTIVE_TO, DataType.Int64, false, null));
        schema.addField(field("allowed_roles", DataType.VarChar, false, 4096));
        schema.addField(field("section_path", DataType.VarChar, false, 1024));
        schema.addField(field("page_number", DataType.Int64, false, null));
        schema.addField(AddFieldReq.builder()
                .fieldName(VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(dimensions)
                .build());

        List<IndexParam> indexes = new ArrayList<>();
        indexes.add(IndexParam.builder()
                .fieldName(VECTOR)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build());
        for (String fieldName : List.of(TENANT_ID, KNOWLEDGE_BASE_ID, DOCUMENT_ID, DOCUMENT_STATUS,
                SECURITY_LEVEL, EFFECTIVE_FROM, EFFECTIVE_TO)) {
            indexes.add(IndexParam.builder()
                    .fieldName(fieldName)
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .build());
        }

        client.createCollection(CreateCollectionReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .description("Enterprise RAG child chunk embeddings and authorization metadata")
                .collectionSchema(schema)
                .indexParams(indexes)
                .build());
    }

    private void validateExistingCollection(int dimensions) {
        var response = client.describeCollection(DescribeCollectionReq.builder()
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .build());
        var schema = response.getCollectionSchema();
        if (schema == null || schema.getField(VECTOR) == null) {
            throw new IllegalStateException("已有 Milvus Collection 缺少 embedding 字段");
        }
        Integer actualDimensions = schema.getField(VECTOR).getDimension();
        if (!Integer.valueOf(dimensions).equals(actualDimensions)) {
            throw new IllegalStateException("已有 Milvus Collection 向量维度为 " + actualDimensions
                    + "，与配置维度 " + dimensions + " 不一致");
        }
        for (String fieldName : List.of(CHUNK_ID, TENANT_ID, KNOWLEDGE_BASE_ID, DOCUMENT_ID,
                DOCUMENT_STATUS, SECURITY_LEVEL, EFFECTIVE_FROM, EFFECTIVE_TO)) {
            if (schema.getField(fieldName) == null) {
                throw new IllegalStateException("已有 Milvus Collection 缺少字段: " + fieldName);
            }
        }
    }

    private AddFieldReq field(String name, DataType type, boolean primary, Integer maxLength) {
        var builder = AddFieldReq.builder()
                .fieldName(name)
                .dataType(type);
        if (primary) {
            builder.isPrimaryKey(true).autoID(false);
        }
        if (maxLength != null) {
            builder.maxLength(maxLength);
        }
        return builder.build();
    }

    private JsonObject toRow(VectorRecord record) {
        validate(record);
        JsonObject row = new JsonObject();
        row.addProperty(CHUNK_ID, record.chunkId());
        row.addProperty(TENANT_ID, record.tenantId());
        row.addProperty(KNOWLEDGE_BASE_ID, record.knowledgeBaseId());
        row.addProperty(DOCUMENT_ID, record.documentId());
        row.addProperty("parent_chunk_id", record.parentChunkId());
        row.addProperty("chunk_index", record.chunkIndex().longValue());
        row.addProperty("version", value(record.version()));
        row.addProperty(DOCUMENT_STATUS, value(record.documentStatus()));
        row.addProperty("department", value(record.department()));
        row.addProperty(SECURITY_LEVEL, record.securityLevel().longValue());
        row.addProperty("authority_level", record.authorityLevel().longValue());
        row.addProperty(EFFECTIVE_FROM, epochDay(record.effectiveFrom()));
        row.addProperty(EFFECTIVE_TO, epochDay(record.effectiveTo()));
        row.addProperty("allowed_roles", value(record.allowedRoles()));
        row.addProperty("section_path", value(record.sectionPath()));
        row.addProperty("page_number", record.pageNumber() == null ? -1L : record.pageNumber().longValue());
        row.add(VECTOR, gson.toJsonTree(record.embedding()));
        return row;
    }

    private void validate(VectorRecord record) {
        if (record.chunkId() == null || record.tenantId() == null || record.knowledgeBaseId() == null
                || record.documentId() == null || record.parentChunkId() == null || record.chunkIndex() == null
                || record.securityLevel() == null || record.authorityLevel() == null || record.embedding() == null) {
            throw new IllegalArgumentException("Milvus 向量记录缺少必填字段");
        }
        if (record.embedding().size() != configuredDimensions) {
            throw new IllegalArgumentException("Milvus 向量维度与 Collection 配置不一致");
        }
        if (value(record.allowedRoles()).length() > 4096) {
            throw new IllegalArgumentException("文档访问角色元数据超过 Milvus 字段限制");
        }
    }

    private long epochDay(LocalDate date) {
        return date == null ? 0L : date.toEpochDay();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void requireReady() {
        if (!collectionReady) {
            throw new IllegalStateException("Milvus Collection 尚未初始化");
        }
    }

    private void verifyConfiguredDimension(int dimensions) {
        if (configuredDimensions != dimensions) {
            throw new IllegalStateException("同一进程内请求了不同的 Milvus 向量维度");
        }
    }
}
