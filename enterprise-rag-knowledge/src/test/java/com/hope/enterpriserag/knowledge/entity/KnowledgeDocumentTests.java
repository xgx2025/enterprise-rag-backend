package com.hope.enterpriserag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeDocumentTests {

    @Test
    void alwaysPersistsNullWhenClearingFailureFields() throws NoSuchFieldException {
        assertAlwaysUpdateStrategy("failureStage");
        assertAlwaysUpdateStrategy("failureMessage");
    }

    private void assertAlwaysUpdateStrategy(String fieldName) throws NoSuchFieldException {
        Field field = KnowledgeDocument.class.getDeclaredField(fieldName);
        TableField tableField = field.getAnnotation(TableField.class);

        assertNotNull(tableField);
        assertEquals(FieldStrategy.ALWAYS, tableField.updateStrategy());
    }
}
