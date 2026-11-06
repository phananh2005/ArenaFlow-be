package com.phananh.arenaflow.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.hibernate.annotations.UuidGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mọi entity có id UUID do Hibernate sinh phải dùng UUID v7 (ADR-0008). */
class EntityIdGenerationTest {

    private static final String ENTITY_PACKAGE = "com.phananh.arenaflow.domain.entity";

    @Test
    void everyGeneratedUuidIdUsesVersion7() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<BeanDefinition> entities = scanner.findCandidateComponents(ENTITY_PACKAGE);

        List<String> generatedIds = new ArrayList<>();
        List<String> notVersion7 = new ArrayList<>();
        for (BeanDefinition definition : entities) {
            Class<?> entity = Class.forName(definition.getBeanClassName());
            for (Field field : idFields(entity)) {
                UuidGenerator generator = field.getAnnotation(UuidGenerator.class);
                // @UuidGenerator tự là id generator (@IdGeneratorType), có thể dùng không kèm @GeneratedValue.
                if (field.getType() != UUID.class || (!field.isAnnotationPresent(GeneratedValue.class) && generator == null)) {
                    continue;
                }
                generatedIds.add(entity.getSimpleName());
                if (generator == null || generator.style() != UuidGenerator.Style.VERSION_7) {
                    notVersion7.add(entity.getSimpleName() + "=" + (generator == null ? "no @UuidGenerator" : generator.style()));
                }
            }
        }

        assertThat(generatedIds).as("entity có id UUID tự sinh").isNotEmpty();
        assertThat(notVersion7).as("entity chưa dùng VERSION_7").isEmpty();
    }

    /** Field `@Id` của entity, kể cả khai báo ở lớp cha (`@MappedSuperclass`). */
    private static List<Field> idFields(Class<?> entity) {
        List<Field> ids = new ArrayList<>();
        for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    ids.add(field);
                }
            }
        }
        return ids;
    }
}
