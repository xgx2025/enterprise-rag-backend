package com.hope.enterpriserag.server;

import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalCommand;
import com.hope.enterpriserag.knowledge.service.RetrievalService;
import com.hope.enterpriserag.server.controller.knowledge.RetrievalController;
import com.hope.enterpriserag.server.dto.knowledge.RetrievalRequest;
import com.hope.enterpriserag.system.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalControllerTests {
    @Test
    void derivesTenantRolesAndPublicSecurityLevelOnlyFromAuthentication() {
        RetrievalService service = mock(RetrievalService.class);
        when(service.retrieve(any(), any())).thenReturn(null);
        RetrievalController controller = new RetrievalController(service);
        User user = new User();
        user.setId(99L);
        user.setTenantId(10L);
        var authentication = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RetrievalRequest request = new RetrievalRequest("深圳住宿标准", List.of("20"), null, 5, 5000);

        controller.retrieve(user, authentication, request);

        ArgumentCaptor<RetrievalAccessContext> accessCaptor = ArgumentCaptor.forClass(RetrievalAccessContext.class);
        ArgumentCaptor<RetrievalCommand> commandCaptor = ArgumentCaptor.forClass(RetrievalCommand.class);
        verify(service).retrieve(accessCaptor.capture(), commandCaptor.capture());
        assertEquals(10L, accessCaptor.getValue().tenantId());
        assertEquals(99L, accessCaptor.getValue().userId());
        assertEquals(1, accessCaptor.getValue().maximumSecurityLevel());
        assertTrue(accessCaptor.getValue().roles().contains("ROLE_USER"));
        assertTrue(accessCaptor.getValue().roles().contains("USER"));
        assertEquals(List.of(20L), commandCaptor.getValue().knowledgeBaseIds());
        assertTrue(commandCaptor.getValue().denseEnabled());
        assertTrue(commandCaptor.getValue().sparseEnabled());
        assertTrue(commandCaptor.getValue().rerankEnabled());
    }
}
