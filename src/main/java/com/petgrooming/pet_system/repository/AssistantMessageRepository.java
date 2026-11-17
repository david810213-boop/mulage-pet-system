package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.AssistantMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    /** 某位會員某時間之後、有實際得到回答的提問數（每日題數上限用）。 */
    @Query("""
        select count(a) from AssistantMessage a
        where a.user.id = :userId and a.role = 'ASSISTANT' and a.fallback = false and a.createdAt >= :from
    """)
    long countAnsweredSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("select coalesce(sum(a.costUsd), 0) from AssistantMessage a where a.createdAt >= :from")
    double sumCostSince(@Param("from") LocalDateTime from);

    /** 最近的對話（新的在前），組成下一次提問的前後文。 */
    @Query("""
        select a from AssistantMessage a
        where a.user.id = :userId and a.createdAt >= :from and a.fallback = false
        order by a.createdAt desc, a.id desc
    """)
    List<AssistantMessage> findRecent(@Param("userId") Long userId, @Param("from") LocalDateTime from,
                                      Pageable pageable);

    /** 店家抽查用：最近的對話，連同會員一起查出。 */
    @Query("""
        select a from AssistantMessage a join fetch a.user
        where a.createdAt >= :from
        order by a.createdAt desc, a.id desc
    """)
    List<AssistantMessage> findForReview(@Param("from") LocalDateTime from, Pageable pageable);

    @Query("select count(a) from AssistantMessage a where a.role = 'USER' and a.createdAt >= :from")
    long countQuestionsSince(@Param("from") LocalDateTime from);
}
