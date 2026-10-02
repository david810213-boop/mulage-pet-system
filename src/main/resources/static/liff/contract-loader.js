/*
 * 需求（追加，2026-10-02）：定型化契約全文只有一份（/liff/contract-body.html），
 * 預約頁、代客預約頁、公開契約頁、我的預約「查看契約」都用這支載入，避免各頁條文不一致。
 * 用法：MulageContract.load(容器元素) → Promise（成功時回傳契約版本字串）
 */
(function () {
  var cache = null;
  function fetchBody() {
    if (!cache) {
      cache = fetch("/liff/contract-body.html?v=20261002", { cache: "no-cache" }).then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.text();
      });
    }
    return cache;
  }
  window.MulageContract = {
    load: function (el) {
      if (!el) return Promise.resolve(null);
      el.innerHTML = '<p style="color:#a29a8c">契約載入中…</p>';
      return fetchBody()
        .then(function (html) {
          el.innerHTML = html;
          var box = el.querySelector("[data-contract-version]");
          return box ? box.getAttribute("data-contract-version") : null;
        })
        .catch(function () {
          cache = null;
          el.innerHTML =
            '<p style="color:#B3261E">契約載入失敗，請重新整理頁面；也可到 https://mulage-pet.com/liff/contract.html 查看全文。</p>';
          return null;
        });
    },
  };
})();
