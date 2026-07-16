package com.cobloom.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cobloom.dto.KnowledgeGraphDTO;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import com.cobloom.service.knowledge.KnowledgeGraphQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("图谱服务单元测试")
class GraphServiceTest {

    @Mock
    private KnowledgeGraphQueryService knowledgeGraphQueryService;

    @InjectMocks
    private GraphService graphService;

    @Nested
    @DisplayName("图谱获取测试")
    class GraphNetworkTests {

        @Test
        @DisplayName("✅ 成功获取用户图谱数据")
        void shouldReturnGraphData() {

            // Arrange
            Long testUserId = 123L;

            KnowledgeGraphDTO mockGraph = new KnowledgeGraphDTO(
                    List.of(),
                    List.of());

            when(knowledgeGraphQueryService.graph(testUserId))
                    .thenReturn(mockGraph);

            // Act
            KnowledgeGraphDTO result = graphService.graph(testUserId);

            // Assert
            assertNotNull(result, "图谱数据不应为空");

            assertNotNull(result.nodes,
                    "节点列表不应为空");

            assertNotNull(result.edges,
                    "边列表不应为空");

            verify(knowledgeGraphQueryService, times(1))
                    .graph(testUserId);
        }
    }
}