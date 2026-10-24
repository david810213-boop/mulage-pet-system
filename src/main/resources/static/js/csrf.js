/*
 * 員工頁面共用：所有寫入請求自動帶上 CSRF token（見後端 CsrfInterceptor）。
 * token 取自登入時寫入的可讀 Cookie XSRF-TOKEN：
 *   - 表單（method=post）送出前補上隱藏欄位 _csrf，包含用 JS 呼叫 form.submit() 的情況
 *   - fetch / XMLHttpRequest 的 POST、PUT、PATCH、DELETE 自動加上 X-CSRF-Token 標頭
 * 只對同網域的請求加，不會把 token 送到外部網站。這支檔案必須在 <head> 載入，
 * 頁面上的程式才會用到包裝過的 fetch。
 */
(function () {
  "use strict";

  function readToken() {
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  function isUnsafe(method) {
    var m = String(method || "GET").toUpperCase();
    return m !== "GET" && m !== "HEAD" && m !== "OPTIONS";
  }

  function isSameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (e) {
      return false;
    }
  }

  // ── fetch ──
  var originalFetch = window.fetch;
  if (typeof originalFetch === "function") {
    window.fetch = function (input, init) {
      var isRequest = typeof Request !== "undefined" && input instanceof Request;
      var method = (init && init.method) || (isRequest ? input.method : "GET");
      var url = isRequest ? input.url : String(input);
      var token = readToken();
      if (token && isUnsafe(method) && isSameOrigin(url)) {
        var headers = new Headers((init && init.headers) || (isRequest ? input.headers : undefined));
        if (!headers.has("X-CSRF-Token")) {
          headers.set("X-CSRF-Token", token);
        }
        init = Object.assign({}, init, { headers: headers });
      }
      return originalFetch.call(this, input, init);
    };
  }

  // ── XMLHttpRequest ──
  if (typeof XMLHttpRequest !== "undefined") {
    var originalOpen = XMLHttpRequest.prototype.open;
    var originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__csrfMethod = method;
      this.__csrfUrl = url;
      return originalOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var token = readToken();
      if (token && isUnsafe(this.__csrfMethod) && isSameOrigin(this.__csrfUrl)) {
        try {
          this.setRequestHeader("X-CSRF-Token", token);
        } catch (e) {
          /* 已經送出或狀態不允許設定標頭時忽略 */
        }
      }
      return originalSend.apply(this, arguments);
    };
  }

  // ── 表單 ──
  function ensureFormToken(form) {
    if (!form || String(form.method || "").toLowerCase() !== "post") return;
    if (!isSameOrigin(form.action || window.location.href)) return;
    var token = readToken();
    if (!token) return;
    var field = form.querySelector('input[name="_csrf"]');
    if (!field) {
      field = document.createElement("input");
      field.type = "hidden";
      field.name = "_csrf";
      form.appendChild(field);
    }
    field.value = token;
  }

  document.addEventListener(
    "submit",
    function (event) {
      ensureFormToken(event.target);
    },
    true
  );

  if (typeof HTMLFormElement !== "undefined") {
    var originalSubmit = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () {
      ensureFormToken(this);
      return originalSubmit.apply(this, arguments);
    };
  }
})();
