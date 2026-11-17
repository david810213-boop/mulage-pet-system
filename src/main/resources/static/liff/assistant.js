/*
 * 慕馬 AI 小助手（LIFF 會員頁共用）。
 * 引用這支檔案的頁面會在登入完成後詢問後端是否開放給這位會員，開放才顯示可拖曳的慕馬按鈕；
 * 點開是對話面板，回答與按鈕由後端產生（按鈕網址只來自後端白名單）。
 * 頁面底部有自己的送出按鈕時，在 script 標籤加 data-bottom="像素" 把慕馬預設位置往上移。
 * 設計見 docs/adr/0010。
 */
(function () {
  var IMG = "/liff/img/muma/";
  var POS_KEY = "muma.pos";
  var HELLO_KEY = "muma.hello";
  var CHAT_KEY = "muma.chat";
  var CHAT_KEEP = 20;
  var EDGE = 6;
  var script = document.currentScript;
  var defaultBottom = Number((script && script.getAttribute("data-bottom")) || 92);
  var page = (location.pathname.split("/").pop() || "index.html").replace(".html", "");

  var CONTACT_TEXT = "想找真人客服的話，點下面的按鈕到官方 LINE 留言，店家會親自回覆你喔！";
  var CONTACT_ACTION = {
    label: "聯絡店家（真人）",
    url: "https://line.me/R/ti/p/@108dbaxm",
    type: "contact",
  };
  var CHIPS = ["營業時間與價目", "還有哪天有空位", "訂金怎麼算", "找真人客服"];

  var state = { remaining: 0, limit: 0, busy: false, open: false };
  var el = {};

  // ---------- 小工具 ----------
  function store(key, value) {
    try {
      if (value === undefined) return JSON.parse(localStorage.getItem(key));
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      return null;
    }
  }

  function session(value) {
    try {
      if (value === undefined) return JSON.parse(sessionStorage.getItem(CHAT_KEY)) || [];
      sessionStorage.setItem(CHAT_KEY, JSON.stringify(value.slice(-CHAT_KEEP)));
    } catch (e) {
      return [];
    }
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/\n/g, "<br>");
  }

  function token() {
    try {
      /* global jwtToken */
      return typeof jwtToken !== "undefined" && jwtToken ? jwtToken : null;
    } catch (e) {
      return null;
    }
  }

  function waitForToken(timeoutMs) {
    return new Promise(function (resolve) {
      var start = Date.now();
      (function poll() {
        var t = token();
        if (t) return resolve(t);
        if (Date.now() - start > timeoutMs) return resolve(null);
        setTimeout(poll, 400);
      })();
    });
  }

  // 後端錯誤可能是純文字，先讀文字再試著解析
  function api(method, url, body) {
    var headers = { Authorization: "Bearer " + token(), "ngrok-skip-browser-warning": "true" };
    if (body) headers["Content-Type"] = "application/json";
    return fetch(url, { method: method, headers: headers, body: body ? JSON.stringify(body) : undefined }).then(
      function (res) {
        return res.text().then(function (text) {
          var data = null;
          try {
            data = text ? JSON.parse(text) : null;
          } catch (e) {
            data = null;
          }
          return { ok: res.ok, data: data, text: text };
        });
      }
    );
  }

  function greeting() {
    var h = new Date().getHours();
    if (h < 11) return "早安";
    if (h < 18) return "午安";
    return "晚安";
  }

  // ---------- 樣式 ----------
  function injectStyle() {
    var css =
      ".mm-fab{position:fixed;z-index:60;width:72px;touch-action:none;user-select:none;-webkit-user-select:none;" +
      "filter:drop-shadow(0 6px 10px rgba(110,82,41,.28));transition:left .25s ease,right .25s ease,opacity .2s,width .2s;cursor:pointer}" +
      ".mm-fab img{width:100%;display:block;pointer-events:none;-webkit-user-drag:none}" +
      ".mm-fab.dragging{transition:none}" +
      ".mm-fab.hello{width:92px;animation:mm-pop .35s ease-out}" +
      "@keyframes mm-pop{from{transform:scale(.7)}to{transform:scale(1)}}" +
      ".mm-mask{position:fixed;inset:0;background:rgba(40,30,20,.35);z-index:9000;opacity:0;transition:opacity .2s}" +
      ".mm-mask.show{opacity:1}" +
      ".mm-sheet{position:fixed;left:0;right:0;bottom:0;height:80vh;height:80dvh;max-width:560px;margin:0 auto;" +
      "background:#FBF8F2;border-radius:22px 22px 0 0;box-shadow:0 -10px 30px rgba(0,0,0,.18);z-index:9001;" +
      "display:flex;flex-direction:column;transform:translateY(100%);transition:transform .25s ease;color:#2c2a28}" +
      ".mm-sheet.show{transform:none}" +
      ".mm-sheet[hidden],.mm-mask[hidden]{display:none}" +
      ".mm-head{display:flex;align-items:center;gap:10px;padding:10px 14px;border-bottom:1px solid #E8E2D5}" +
      ".mm-head img{width:40px}" +
      ".mm-head b{font-size:16px}.mm-head small{display:block;color:#a29a8c;font-size:12px}" +
      ".mm-x{margin-left:auto;width:36px;height:36px;border:0;background:none;font-size:24px;color:#a29a8c}" +
      ".mm-body{flex:1;overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:10px;-webkit-overflow-scrolling:touch}" +
      ".mm-row{display:flex;gap:8px;align-items:flex-end}" +
      ".mm-row img.mm-av{width:30px;flex:none}" +
      ".mm-row img.mm-big{width:84px;flex:none}" +
      ".mm-msg{max-width:76%;padding:9px 12px;border-radius:14px;font-size:14px;line-height:1.6;word-break:break-word}" +
      ".mm-bot{background:#fff;border:1px solid #E8E2D5;border-bottom-left-radius:4px}" +
      ".mm-me{align-self:flex-end;background:#9C846E;color:#fff;border-bottom-right-radius:4px}" +
      ".mm-acts{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}" +
      ".mm-act{border:0;padding:6px 13px;border-radius:999px;background:#D98A6C;color:#fff;font-size:13px;font-family:inherit}" +
      ".mm-act.contact{background:#06C755}" +
      ".mm-welcome{display:flex;flex-direction:column;align-items:center;gap:8px;margin:8px 0;color:#6e5229;" +
      "font-size:14px;text-align:center;line-height:1.6}" +
      ".mm-welcome img{width:140px}" +
      ".mm-wait img{width:104px}" +
      ".mm-chips{display:flex;gap:6px;overflow-x:auto;padding:8px 14px;scrollbar-width:none}" +
      ".mm-chips::-webkit-scrollbar{display:none}" +
      ".mm-chip{flex:none;border:1px solid #DBD3C4;background:#fff;border-radius:999px;padding:6px 11px;" +
      "font-size:12.5px;color:#6e5229;font-family:inherit}" +
      ".mm-foot{border-top:1px solid #E8E2D5;background:#fff;padding:8px 14px calc(12px + env(safe-area-inset-bottom))}" +
      ".mm-input{display:flex;gap:8px;align-items:center}" +
      ".mm-input input{flex:1;min-width:0;border:1px solid #DBD3C4;border-radius:999px;padding:10px 14px;" +
      "font-size:16px;font-family:inherit;background:#fff;color:#2c2a28;outline:none}" +
      ".mm-input input:focus{border-color:#9C846E}" +
      ".mm-send{width:42px;height:42px;flex:none;border:0;border-radius:50%;background:#9C846E;color:#fff;" +
      "display:flex;align-items:center;justify-content:center}" +
      ".mm-send:disabled{background:#cfc5b6}" +
      ".mm-left{font-size:11.5px;color:#a29a8c;margin-top:5px;text-align:center}";
    var style = document.createElement("style");
    style.textContent = css;
    document.head.appendChild(style);
  }

  // ---------- 可拖曳的慕馬按鈕 ----------
  function placeFab(pos) {
    var maxBottom = window.innerHeight - 140;
    var bottom = Math.min(Math.max(pos.bottom, 70), Math.max(70, maxBottom));
    el.fab.style.bottom = bottom + "px";
    el.fab.style.top = "";
    if (pos.side === "left") {
      el.fab.style.left = EDGE + "px";
      el.fab.style.right = "auto";
    } else {
      el.fab.style.right = EDGE + "px";
      el.fab.style.left = "auto";
    }
    state.pos = { side: pos.side, bottom: bottom };
  }

  function buildFab() {
    el.fab = document.createElement("div");
    el.fab.className = "mm-fab";
    el.fab.setAttribute("role", "button");
    el.fab.setAttribute("aria-label", "問慕馬");
    el.fab.innerHTML = '<img src="' + IMG + 'full.webp" alt="慕馬">';
    document.body.appendChild(el.fab);
    placeFab(store(POS_KEY) || { side: "right", bottom: defaultBottom });

    var drag = null;
    el.fab.addEventListener("pointerdown", function (e) {
      var r = el.fab.getBoundingClientRect();
      drag = { id: e.pointerId, x: e.clientX, y: e.clientY, dx: e.clientX - r.left, dy: e.clientY - r.top, moved: false };
      el.fab.setPointerCapture(e.pointerId);
    });
    el.fab.addEventListener("pointermove", function (e) {
      if (!drag || drag.id !== e.pointerId) return;
      if (!drag.moved && Math.abs(e.clientX - drag.x) + Math.abs(e.clientY - drag.y) < 8) return;
      if (!drag.moved) {
        drag.moved = true;
        el.fab.classList.add("dragging");
        hideHello();
      }
      el.fab.style.left = e.clientX - drag.dx + "px";
      el.fab.style.right = "auto";
      el.fab.style.top = e.clientY - drag.dy + "px";
      el.fab.style.bottom = "auto";
    });
    function end(e) {
      if (!drag || drag.id !== e.pointerId) return;
      var wasDrag = drag.moved;
      drag = null;
      el.fab.classList.remove("dragging");
      if (!wasDrag) {
        openSheet();
        return;
      }
      var r = el.fab.getBoundingClientRect();
      var side = r.left + r.width / 2 < window.innerWidth / 2 ? "left" : "right";
      placeFab({ side: side, bottom: window.innerHeight - r.bottom });
      store(POS_KEY, state.pos);
    }
    el.fab.addEventListener("pointerup", end);
    el.fab.addEventListener("pointercancel", end);
    window.addEventListener("resize", function () {
      if (state.pos) placeFab(state.pos);
    });
  }

  // 第一次看到慕馬時換成「有任何問題都可以問我喔」的樣子幾秒鐘
  function showHello() {
    if (store(HELLO_KEY)) return;
    store(HELLO_KEY, 1);
    el.fab.classList.add("hello");
    el.fab.querySelector("img").src = IMG + "hello.webp";
    state.helloTimer = setTimeout(hideHello, 6000);
  }

  function hideHello() {
    if (!el.fab.classList.contains("hello")) return;
    clearTimeout(state.helloTimer);
    el.fab.classList.remove("hello");
    el.fab.querySelector("img").src = IMG + "full.webp";
  }

  // ---------- 對話面板 ----------
  function buildSheet() {
    el.mask = document.createElement("div");
    el.mask.className = "mm-mask";
    el.mask.hidden = true;
    el.mask.addEventListener("click", closeSheet);

    el.sheet = document.createElement("div");
    el.sheet.className = "mm-sheet";
    el.sheet.hidden = true;
    el.sheet.setAttribute("role", "dialog");
    el.sheet.setAttribute("aria-label", "慕馬小助手");
    el.sheet.innerHTML =
      '<div class="mm-head"><img src="' + IMG + 'full.webp" alt="">' +
      "<div><b>慕馬</b><small>預約、價目、訂金與會員問題都可以問我</small></div>" +
      '<button type="button" class="mm-x" aria-label="關閉">×</button></div>' +
      '<div class="mm-body"></div>' +
      '<div class="mm-chips"></div>' +
      '<div class="mm-foot"><form class="mm-input"><input type="text" maxlength="200" enterkeyhint="send" ' +
      'placeholder="輸入你的問題…" aria-label="輸入問題">' +
      '<button type="submit" class="mm-send" aria-label="送出">' +
      '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" ' +
      'stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M13 6l6 6-6 6"/></svg></button></form>' +
      '<div class="mm-left"></div></div>';
    document.body.appendChild(el.mask);
    document.body.appendChild(el.sheet);

    el.body = el.sheet.querySelector(".mm-body");
    el.input = el.sheet.querySelector("input");
    el.send = el.sheet.querySelector(".mm-send");
    el.left = el.sheet.querySelector(".mm-left");
    el.sheet.querySelector(".mm-x").addEventListener("click", closeSheet);
    el.sheet.querySelector("form").addEventListener("submit", function (e) {
      e.preventDefault();
      ask(el.input.value);
    });
    var chips = el.sheet.querySelector(".mm-chips");
    CHIPS.forEach(function (label) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "mm-chip";
      b.textContent = label;
      b.addEventListener("click", function () {
        if (label === "找真人客服") contactLocally(label);
        else ask(label);
      });
      chips.appendChild(b);
    });
    el.body.addEventListener("click", function (e) {
      var btn = e.target.closest(".mm-act");
      if (btn) followAction(JSON.parse(btn.getAttribute("data-action")));
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && state.open) closeSheet();
    });
  }

  function renderHistory() {
    var items = session();
    el.body.innerHTML = "";
    if (!items.length) {
      el.body.innerHTML =
        '<div class="mm-welcome"><img src="' + IMG + 'hello.webp" alt="">' +
        "<div>" + greeting() + "～我是慕馬<br>預約、訂金、儲值的問題都可以問我喔！</div></div>";
      return;
    }
    items.forEach(function (m) {
      if (m.me) appendMe(m.text);
      else appendBot(m.text, m.actions, m.img);
    });
  }

  function remember(entry) {
    var items = session();
    items.push(entry);
    session(items);
  }

  function scrollDown() {
    el.body.scrollTop = el.body.scrollHeight;
  }

  function appendMe(text) {
    var w = el.body.querySelector(".mm-welcome");
    if (w) w.remove();
    var div = document.createElement("div");
    div.className = "mm-msg mm-me";
    div.innerHTML = esc(text);
    el.body.appendChild(div);
    scrollDown();
  }

  function appendBot(text, actions, img) {
    var row = document.createElement("div");
    row.className = "mm-row";
    var avatar = img
      ? '<img class="mm-big" src="' + IMG + img + '.webp" alt="">'
      : '<img class="mm-av" src="' + IMG + 'full.webp" alt="">';
    var acts = (actions || [])
      .map(function (a) {
        return (
          '<button type="button" class="mm-act' + (a.type === "contact" ? " contact" : "") +
          "\" data-action='" + esc(JSON.stringify(a)).replace(/'/g, "&#39;") + "'>" + esc(a.label) + "</button>"
        );
      })
      .join("");
    row.innerHTML =
      avatar + '<div class="mm-msg mm-bot">' + esc(text) + (acts ? '<div class="mm-acts">' + acts + "</div>" : "") + "</div>";
    el.body.appendChild(row);
    scrollDown();
  }

  function setRemaining(n) {
    state.remaining = Math.max(0, n);
    var out = state.remaining <= 0;
    el.input.disabled = out;
    el.send.disabled = out || state.busy;
    el.input.placeholder = out ? "今天的提問次數用完了" : "輸入你的問題…";
    el.left.textContent = out ? "明天就能再問囉，急事請聯絡店家" : "今天還可以問 " + state.remaining + " 題";
  }

  function contactLocally(label) {
    appendMe(label);
    remember({ me: true, text: label });
    appendBot(CONTACT_TEXT, [CONTACT_ACTION]);
    remember({ text: CONTACT_TEXT, actions: [CONTACT_ACTION] });
  }

  function ask(raw) {
    var text = String(raw || "").trim();
    if (!text || state.busy || state.remaining <= 0) return;
    state.busy = true;
    el.send.disabled = true;
    el.input.value = "";
    appendMe(text);
    remember({ me: true, text: text });

    // 等待時先「思考中」，久一點換成「回覆中」
    var wait = document.createElement("div");
    wait.className = "mm-row mm-wait";
    wait.innerHTML = '<img src="' + IMG + 'think.webp" alt="慕馬正在思考">';
    el.body.appendChild(wait);
    scrollDown();
    var switchTimer = setTimeout(function () {
      var img = wait.querySelector("img");
      if (img) img.src = IMG + "reply.webp";
    }, 2500);

    api("POST", "/api/me/assistant/messages", { message: text, page: page })
      .then(function (r) {
        var reply = r.ok && r.data && r.data.text ? r.data : null;
        if (!reply) {
          var msg = (r.data && r.data.message) || "慕馬現在有點忙，請稍後再試，或直接聯絡店家喔！";
          reply = { text: msg, actions: [CONTACT_ACTION], mood: "error", remaining: state.remaining };
        }
        return reply;
      })
      .catch(function () {
        return {
          text: "網路好像不太穩，請稍後再試，或直接聯絡店家喔！",
          actions: [CONTACT_ACTION],
          mood: "error",
          remaining: state.remaining,
        };
      })
      .then(function (reply) {
        clearTimeout(switchTimer);
        wait.remove();
        var img = reply.mood === "unsure" ? "unsure" : null;
        appendBot(reply.text, reply.actions, img);
        remember({ text: reply.text, actions: reply.actions, img: img });
        state.busy = false;
        setRemaining(typeof reply.remaining === "number" ? reply.remaining : state.remaining);
      });
  }

  function followAction(a) {
    if (!a || !a.url) return;
    if (a.type === "contact") {
      var done = "已經幫你打開官方 LINE，直接留言給店家就可以囉！";
      appendBot(done, null, "done");
      remember({ text: done, img: "done" });
      if (window.liff && liff.openWindow) {
        try {
          liff.openWindow({ url: a.url, external: false });
          return;
        } catch (e) {
          /* 退回一般開啟 */
        }
      }
      window.open(a.url, "_blank");
      return;
    }
    location.href = a.url;
  }

  function openSheet() {
    hideHello();
    state.open = true;
    renderHistory();
    el.mask.hidden = false;
    el.sheet.hidden = false;
    el.fab.style.opacity = "0";
    requestAnimationFrame(function () {
      el.mask.classList.add("show");
      el.sheet.classList.add("show");
      scrollDown();
    });
    setRemaining(state.remaining);
  }

  function closeSheet() {
    state.open = false;
    el.input.blur();
    el.mask.classList.remove("show");
    el.sheet.classList.remove("show");
    setTimeout(function () {
      el.mask.hidden = true;
      el.sheet.hidden = true;
    }, 250);
    // 關閉時揮手說掰掰，再變回原本的樣子
    var img = el.fab.querySelector("img");
    img.src = IMG + "bye.webp";
    el.fab.style.opacity = "1";
    setTimeout(function () {
      img.src = IMG + "full.webp";
    }, 1600);
  }

  // ---------- 啟動 ----------
  function start() {
    waitForToken(20000)
      .then(function (t) {
        if (!t) return null;
        return api("GET", "/api/me/assistant/quota");
      })
      .then(function (r) {
        if (!r || !r.ok || !r.data || !r.data.enabled) return;
        state.limit = r.data.dailyLimit;
        injectStyle();
        buildFab();
        buildSheet();
        setRemaining(r.data.remaining);
        new Image().src = IMG + "think.webp";
        setTimeout(showHello, 800);
      })
      .catch(function () {
        /* 慕馬不影響頁面其他功能 */
      });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
