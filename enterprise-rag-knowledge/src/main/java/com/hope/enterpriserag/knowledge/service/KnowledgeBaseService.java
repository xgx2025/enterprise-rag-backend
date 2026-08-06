package com.hope.enterpriserag.knowledge.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.KnowledgeBaseRequest;
import com.hope.enterpriserag.knowledge.dto.KnowledgeBaseResponse;
import com.hope.enterpriserag.knowledge.entity.KnowledgeBase;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeBaseMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库应用服务，负责租户隔离、名称唯一性校验和知识库生命周期管理。
 */
@Slf4j
@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper documentMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
    }

    /**
     * 查询租户知识库。
     *
     * @param tenantId       当前租户 ID
     * @param includeDisabled 是否包含已停用知识库
     * @return 按更新时间倒序排列的知识库列表
     */
    public List<KnowledgeBaseResponse> list(Long tenantId, boolean includeDisabled) {
        LambdaQueryWrapper<KnowledgeBase> query = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .orderByDesc(KnowledgeBase::getUpdatedAt);
        if (!includeDisabled) {
            query.eq(KnowledgeBase::getStatus, "ACTIVE");
        }
        return knowledgeBaseMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    /** 创建租户内名称唯一的知识库。 */
    @Transactional
    public KnowledgeBaseResponse create(Long tenantId, Long userId, KnowledgeBaseRequest request) {
        ensureUniqueName(tenantId, request.name().trim(), null);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(IdUtil.getSnowflakeNextId());
        knowledgeBase.setTenantId(tenantId);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setDepartment(trimToNull(request.department()));
        knowledgeBase.setSecurityLevel(request.securityLevel() == null ? 1 : request.securityLevel());
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBase.setCreatedBy(userId);
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseMapper.insert(knowledgeBase);
        log.info("知识库创建成功: tenantId={}, userId={}, knowledgeBaseId={}, securityLevel={}",
                tenantId, userId, knowledgeBase.getId(), knowledgeBase.getSecurityLevel());
        return toResponse(knowledgeBase);
    }

    /** 更新属于当前租户的知识库基础信息。 */
    @Transactional
    public KnowledgeBaseResponse update(Long tenantId, Long id, KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = requireOwned(tenantId, id);
        ensureUniqueName(tenantId, request.name().trim(), id);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setDepartment(trimToNull(request.department()));
        knowledgeBase.setSecurityLevel(request.securityLevel() == null ? 1 : request.securityLevel());
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
        log.info("知识库更新成功: tenantId={}, knowledgeBaseId={}, securityLevel={}",
                tenantId, id, knowledgeBase.getSecurityLevel());
        return toResponse(knowledgeBase);
    }

    /** 更新知识库状态；仍有生效文档时不允许停用。 */
    @Transactional
    public void updateStatus(Long tenantId, Long id, String status) {
        KnowledgeBase knowledgeBase = requireOwned(tenantId, id);
        if ("DISABLED".equals(status)) {
            long activeDocuments = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getTenantId, tenantId)
                    .eq(KnowledgeDocument::getKnowledgeBaseId, id)
                    .eq(KnowledgeDocument::getDeleted, 0)
                    .eq(KnowledgeDocument::getStatus, "ACTIVE"));
            if (activeDocuments > 0) {
                log.warn("知识库停用被拒绝-仍有生效文档: tenantId={}, knowledgeBaseId={}, activeDocuments={}",
                        tenantId, id, activeDocuments);
                throw new BusinessException("知识库仍包含已生效文档，请先将文档设为失效");
            }
        }
        String previousStatus = knowledgeBase.getStatus();
        knowledgeBase.setStatus(status);
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
        log.info("知识库状态更新成功: tenantId={}, knowledgeBaseId={}, previousStatus={}, targetStatus={}",
                tenantId, id, previousStatus, status);
    }

    /**
     * 获取当前租户内可用的知识库。
     *
     * @throws BusinessException 知识库不存在或已停用
     */
    public KnowledgeBase requireActive(Long tenantId, Long id) {
        KnowledgeBase knowledgeBase = requireOwned(tenantId, id);
        if (!"ACTIVE".equals(knowledgeBase.getStatus())) {
            throw new BusinessException("目标知识库已停用");
        }
        return knowledgeBase;
    }

    /**
     * 获取属于当前租户的知识库，避免跨租户访问。
     *
     * @throws BusinessException 知识库不存在或不属于当前租户
     */
    public KnowledgeBase requireOwned(Long tenantId, Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getTenantId, tenantId));
        if (knowledgeBase == null) {
            log.warn("知识库访问失败-资源不存在或不属于当前租户: tenantId={}, knowledgeBaseId={}", tenantId, id);
            throw new BusinessException(404, "知识库不存在");
        }
        return knowledgeBase;
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase) {
        long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, knowledgeBase.getTenantId())
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBase.getId())
                .eq(KnowledgeDocument::getDeleted, 0));
        return new KnowledgeBaseResponse(
                String.valueOf(knowledgeBase.getId()), knowledgeBase.getName(), documentCount,
                knowledgeBase.getDescription(), knowledgeBase.getDepartment(),
                knowledgeBase.getSecurityLevel(), knowledgeBase.getStatus(),
                knowledgeBase.getCreatedAt(), knowledgeBase.getUpdatedAt());
    }

    private void ensureUniqueName(Long tenantId, String name, Long excludedId) {
        LambdaQueryWrapper<KnowledgeBase> query = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .eq(KnowledgeBase::getName, name);
        if (excludedId != null) {
            query.ne(KnowledgeBase::getId, excludedId);
        }
        if (knowledgeBaseMapper.selectCount(query) > 0) {
            log.warn("知识库名称冲突: tenantId={}, excludedKnowledgeBaseId={}", tenantId, excludedId);
            throw new BusinessException("同一企业下已存在同名知识库");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
