package org.example.APIGatewaySvc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티의 기본 승계 클래스
 * JPA 엔티티에서 공통으로 사용되는 기본 필드와 엠디팅 기능을 제공
 * 
 * 주요 기능:
 * - 공통 기본 필드 제공 (ID, 생성일자, 수정일자, 삭제 여부)
 * - JPA Auditing을 통한 생성/수정 일시 자동 관리
 * - Soft Delete 패턴 지원 (삭제 플래그 관리)
 * - 엔티티 기본 라이프사이클 관리
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** 엔티티 기본 키 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 엔티티 생성 일시 (JPA Auditing을 통해 자동 설정) */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 엔티티 마지막 수정 일시 (JPA Auditing을 통해 자동 업데이트) */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    /** Soft Delete를 위한 삭제 플래그 (기본값: false) */
    @Column(nullable = false)
    private Boolean deleted = false;
}