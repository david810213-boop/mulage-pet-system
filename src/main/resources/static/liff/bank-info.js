/*
 * 匯款資訊（銀行、帳號、戶名）每一列附「複製」按鈕，儲值頁與訂金匯款資訊共用。
 * 用法：容器.innerHTML = MulageBank.rows({ bankName, accountNumber, accountHolder })
 *
 * LINE 內建瀏覽器有時不提供 navigator.clipboard，失敗時改用隱藏 textarea ＋ execCommand；
 * 兩種都不行就提示長按文字自行複製（文字本身設為整段可選取）。
 */
(function () {
  var style = document.createElement("style");
  style.textContent =
    ".bank-rows{display:flex;flex-direction:column;gap:6px;margin:4px 0}" +
    ".bank-row{display:flex;align-items:center;gap:8px;min-height:30px}" +
    ".bank-label{flex:0 0 auto;color:var(--ink-soft,#6b645c)}" +
    ".bank-value{flex:1;min-width:0;color:var(--ink,#2c2a28);font-weight:700;word-break:break-all;-webkit-user-select:all;user-select:all}" +
    ".bank-value.missing{font-weight:400;color:var(--ink-faint,#a29a8c)}" +
    ".bank-copy{flex:0 0 auto;font:inherit;font-size:12.5px;line-height:1;padding:7px 12px;border-radius:999px;" +
    "border:1px solid var(--line,#dbd3c4);background:#fff;color:var(--primary,#3a4d3f);cursor:pointer}" +
    ".bank-copy:active{background:var(--paper-2,#f1ece2)}" +
    ".bank-copy.done{background:var(--done-bg,#e8f0e6);border-color:var(--done-bg,#e8f0e6);color:var(--done-fg,#2d5a27)}";
  document.head.appendChild(style);

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function row(label, value, copyValue) {
    if (!value) {
      return '<div class="bank-row"><span class="bank-label">' + label + '：</span>' +
        '<span class="bank-value missing">尚未設定</span></div>';
    }
    return '<div class="bank-row"><span class="bank-label">' + label + '：</span>' +
      '<span class="bank-value">' + esc(value) + "</span>" +
      '<button type="button" class="bank-copy" data-copy="' + esc(copyValue) + '" aria-label="複製' + label + '">複製</button></div>';
  }

  function fallbackCopy(text) {
    var ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.top = "0";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    ta.setSelectionRange(0, text.length);
    var ok = false;
    try {
      ok = document.execCommand("copy");
    } catch (e) {
      ok = false;
    }
    document.body.removeChild(ta);
    return ok;
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(
        function () { return true; },
        function () { return fallbackCopy(text); }
      );
    }
    return Promise.resolve(fallbackCopy(text));
  }

  document.addEventListener("click", function (e) {
    var btn = e.target.closest ? e.target.closest(".bank-copy") : null;
    if (!btn) return;
    copyText(btn.getAttribute("data-copy") || "").then(function (ok) {
      btn.textContent = ok ? "已複製" : "請長按文字複製";
      btn.classList.toggle("done", ok);
      clearTimeout(btn._reset);
      btn._reset = setTimeout(function () {
        btn.textContent = "複製";
        btn.classList.remove("done");
      }, 1600);
    });
  });

  window.MulageBank = {
    rows: function (info) {
      info = info || {};
      // 帳號只複製數字，貼到網銀 App 不會因為空白或連字號被擋
      var digits = String(info.accountNumber || "").replace(/[\s-]/g, "");
      return '<div class="bank-rows">' +
        row("銀行", info.bankName, info.bankName) +
        row("帳號", info.accountNumber, digits) +
        row("戶名", info.accountHolder, info.accountHolder) +
        "</div>";
    },
  };
})();
