/*
 * 需求（追加，2026-10-02 店家確認 7）：掃碼槍掃商品條碼。
 *
 * USB／藍牙掃碼槍等同鍵盤：把條碼打進輸入框，最後按 Enter。這支程式接住 Enter，
 * 到後端查條碼是哪一個商品，再依輸入框的 data-scan 決定要做什麼：
 *
 *   data-scan="add-retail" data-form="#表單選擇器"
 *     → 掃到零售商品：把該表單的 retailProductId 設成這個商品、數量 1，直接送出（加進訂單）
 *   data-scan="find"
 *     → 在頁面上找 data-scan-target="RETAIL-商品id" / "SUPPLY-洗劑id" 的列，捲過去並標示；
 *       那一列裡有 data-scan-default 的按鈕就自動按下（店用洗劑＝預設「領用 1」，仍需按確認）
 *
 * 訊息顯示在同一個 [data-scan-box] 裡的 [data-scan-msg]。
 */
(function () {
  function msg(input, text, ok) {
    var box = input.closest("[data-scan-box]");
    var el = box ? box.querySelector("[data-scan-msg]") : null;
    // 手機版的訊息列放在掃碼框的下一個元素（修正 2026-10-02：原本找不到，錯誤會跳 alert）
    if (!el && box && box.nextElementSibling && box.nextElementSibling.hasAttribute("data-scan-msg")) {
      el = box.nextElementSibling;
    }
    if (el) {
      el.textContent = text;
      el.style.color = ok ? "#2D5A27" : "#B3261E";
    } else if (!ok) {
      window.alert(text);
    }
  }

  function lookup(code) {
    return fetch("/api/admin/inventory/barcode?code=" + encodeURIComponent(code), {
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    }).then(function (res) {
      if (!res.ok) return { found: false, message: "查詢失敗（" + res.status + "），請重新整理後再試" };
      return res.json();
    });
  }

  function typeLabel(t) {
    return t === "RETAIL" ? "零售商品" : "店用洗劑";
  }

  function handle(input, code, r) {
    if (!r || !r.found) {
      msg(input, (r && r.message) || "查無條碼 " + code + "，請確認商品已建檔", false);
      return;
    }
    if (r.deleted) {
      msg(input, "「" + r.name + "」已下架", false);
      return;
    }
    var mode = input.getAttribute("data-scan");
    if (mode === "add-retail") {
      if (r.type !== "RETAIL") {
        msg(input, "「" + r.name + "」是店用洗劑，不能加進訂單", false);
        return;
      }
      var form = document.querySelector(input.getAttribute("data-form"));
      var sel = form ? form.querySelector("[name=retailProductId]") : null;
      if (!sel) {
        msg(input, "目前沒有可加購的商品", false);
        return;
      }
      if (sel.tagName === "SELECT" &&
          !Array.prototype.some.call(sel.options, function (o) { return o.value === String(r.id); })) {
        msg(input, "「" + r.name + "」目前不能加購（可能沒有庫存）", false);
        return;
      }
      sel.value = String(r.id);
      var qty = form.querySelector("[name=quantity]");
      if (qty) qty.value = 1;
      msg(input, "已掃到「" + r.name + "」，加入中…", true);
      input.disabled = true;
      form.submit();
      return;
    }
    if (mode === "find") {
      var target = document.querySelector('[data-scan-target="' + r.type + "-" + r.id + '"]');
      if (!target) {
        msg(input, "「" + r.name + "」是" + typeLabel(r.type) + "，不在這個清單", false);
        return;
      }
      target.scrollIntoView({ behavior: "smooth", block: "center" });
      target.style.outline = "3px solid #e2b04a";
      target.style.outlineOffset = "2px";
      setTimeout(function () { target.style.outline = ""; }, 2500);
      msg(input, "找到「" + r.name + "」，目前庫存 " + r.stock, true);
      var dft = target.querySelector("[data-scan-default]");
      if (dft) setTimeout(function () { dft.click(); }, 350);
    }
  }

  document.querySelectorAll("input[data-scan]").forEach(function (input) {
    input.addEventListener("keydown", function (e) {
      if (e.key !== "Enter") return;
      e.preventDefault();
      var code = input.value.trim();
      input.value = "";
      if (!code) return;
      msg(input, "查詢中…", true);
      lookup(code)
        .then(function (r) { handle(input, code, r); })
        .catch(function () { msg(input, "網路錯誤，請再掃一次", false); });
    });
  });
})();
