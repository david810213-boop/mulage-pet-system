# 0004 折扣逐項目擇優、不疊加，由 DiscountCalculator 統一計算

## 背景
店家有三種折扣：會員儲值折扣（依卡別）、首次體驗優惠（狗狗洗吹套餐、貓咪洗澡）、貓咪 90 天回洗優惠。店家規定**只能擇一、取對顧客較優惠者，不疊加**，而且是逐項目判斷（加購項目、零售商品不打折）。這套規則曾經在結帳、預覽、明細標籤等地方各寫一份，容易不一致。

## 決策
- 服務促銷實作 `SpecialDiscountPolicy`（資格判斷、適用的積分分類、折扣率）；`DiscountCalculator` 是唯一的擇優規則：
  - 促銷優先順序：狗狗首次體驗 → 貓咪首次體驗 → 回洗（實際上互斥）。
  - 與會員折扣取價格較低者；同價時以促銷計。
- 服務促銷不限付款方式；會員折扣只限儲值金付款，且項目本身可享會員折扣（`GroomingItem.discountEligible`；零售商品、自訂項目不打折）。
- 結帳扣款、結帳頁預覽（`CheckoutPreviewService`）、消費明細標籤都走同一個計算器，保證三者一致。

## 後果
- 新增一種促銷：寫一個 `SpecialDiscountPolicy` 實作，加進 `DiscountCalculator` 的優先順序清單，並在 `DiscountCheckoutTest` 補案例。
