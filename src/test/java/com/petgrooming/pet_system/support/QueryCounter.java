package com.petgrooming.pet_system.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

/**
 * 計算一段程式實際送出幾條 SQL（Hibernate 統計，測試設定檔已開啟 generate_statistics）。
 * 用來驗證 N+1 查詢：資料筆數增加時，查詢次數不應跟著增加。
 */
@Component
public class QueryCounter {

    private final Statistics statistics;

    public QueryCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @FunctionalInterface
    public interface Work {
        void run() throws Exception;
    }

    public long count(Work work) throws Exception {
        statistics.clear();
        work.run();
        return statistics.getPrepareStatementCount();
    }
}
