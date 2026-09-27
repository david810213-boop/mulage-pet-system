/*
 * 慕沐村 LIFF App ID 集中設定（2026-09-27 改用店家 Provider「慕沐村 Mulage Pet」底下的
 * LINE Login 頻道 2011761209）。
 *
 * 9 個 LIFF 頁面都從這裡讀自己的 LIFF ID，之後如果 LIFF App 重建或搬 Provider，
 * 只要改這一個檔案（外加 application.yml 的 line.liff.bind-url 與 Railway 的 LINE_CHANNEL_ID）。
 * 改完記得把各頁引用這支檔案的 ?v= 版本號加 1，避免 LINE 內建瀏覽器吃到舊快取。
 *
 * 對照 LINE Developers Console → 慕沐村 Mulage Pet → LINE Login 頻道 → LIFF 分頁：
 */
window.MULAGE_LIFF = Object.freeze({
  index: "2011761209-7mlMwId2",          // 首頁            → /liff/index.html
  newCustomer: "2011761209-K1QAmgkg",    // 新客報到        → /liff/new-customer.html
  bindLine: "2011761209-LIdswF56",       // 店員綁定LINE    → /liff/bind-line.html
  myProfile: "2011761209-AmthA3Jh",      // 編輯個人資料    → /liff/my-profile.html
  addPet: "2011761209-kPLBy41P",         // 新增/編輯毛孩   → /liff/add-pet.html
  myPets: "2011761209-VlzcCJu0",         // 我的寵物        → /liff/my-pets.html
  booking: "2011761209-zY3Vv8XX",        // 預約            → /liff/booking.html
  myAppointments: "2011761209-qvAijr91", // 我的預約        → /liff/my-appointments.html
  wallet: "2011761209-skJUHYun",         // 錢包            → /liff/wallet.html
});
