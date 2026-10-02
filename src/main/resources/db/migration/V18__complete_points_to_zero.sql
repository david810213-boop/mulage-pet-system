-- 需求（2026-10-02 店家確認 1-1-C）：「完成」改成不計分、只計次數，歷史紀錄一併歸零。
-- 已經結算過的月份（monthly_performance）也同步扣掉「完成」積分並重算獎勵金。
-- 順序很重要：先用還沒歸零的紀錄算出要扣多少，最後才把紀錄歸零。

-- 1. 已結算月份：主要積分扣掉當月「完成」積分
UPDATE monthly_performance mp
JOIN (
    SELECT staff_id,
           DATE_FORMAT(service_date, '%Y-%m-01') AS ym,
           SUM(points) AS pts
    FROM performance_records
    WHERE category = 'COMPLETE' AND points > 0
    GROUP BY staff_id, DATE_FORMAT(service_date, '%Y-%m-01')
) c ON c.staff_id = mp.staff_id AND c.ym = DATE_FORMAT(mp.perf_month, '%Y-%m-01')
SET mp.total_points = GREATEST(mp.total_points - c.pts, 0);

-- 2. 受影響的已結算月份：依新的主要積分重查獎勵級距
UPDATE monthly_performance mp
SET mp.bonus_amount = COALESCE((
        SELECT bt.bonus_amount FROM bonus_tiers bt
        WHERE FLOOR(mp.total_points) BETWEEN bt.min_points AND bt.max_points
        ORDER BY bt.min_points LIMIT 1), 0)
WHERE EXISTS (
    SELECT 1 FROM performance_records pr
    WHERE pr.staff_id = mp.staff_id
      AND pr.category = 'COMPLETE' AND pr.points > 0
      AND DATE_FORMAT(pr.service_date, '%Y-%m-01') = DATE_FORMAT(mp.perf_month, '%Y-%m-01'));

-- 3. 所有「完成」紀錄歸零（紀錄保留，用來計算次數）
UPDATE performance_records SET points = 0 WHERE category = 'COMPLETE';
