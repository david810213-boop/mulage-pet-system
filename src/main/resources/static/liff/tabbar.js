/*
 * 慕沐村 LIFF 底部導覽列（首頁、毛孩檔案、預約、我的預約、會員）。
 * 頁面只要引用這支檔案就會在最下方加上導覽列，並依網址標示目前所在的分頁；
 * 預約頁與新增毛孩頁底部有自己的送出按鈕，不引用這支檔案，改用單獨的首頁按鈕。
 * 配色依設計定案（慕沐村會員首頁改版提案 · 定案色票與字體）。
 */
(function () {
  var HERO = "#9c846e";

  var ICONS = {
    home: '<path d="M4 11l8-6.5 8 6.5V20h-5.5v-5h-5v5H4z" />',
    pets:
      '<ellipse cx="12" cy="16.2" rx="4.6" ry="3.6" /><circle cx="6.3" cy="9.6" r="1.7" />' +
      '<circle cx="10.6" cy="6.4" r="1.7" /><circle cx="15" cy="6.4" r="1.7" /><circle cx="18.2" cy="9.6" r="1.7" />',
    book: '<rect x="4" y="5.5" width="16" height="14.5" rx="3" /><path d="M4 10h16M8.2 3.5v3.4M15.8 3.5v3.4M12 12.5v5M9.5 15h5" />',
    appts: '<rect x="4" y="5.5" width="16" height="14.5" rx="3" /><path d="M4 10h16M8.2 3.5v3.4M15.8 3.5v3.4M9 15l2 2 4-4" />',
    member: '<circle cx="12" cy="8.5" r="3.8" /><path d="M4.5 20c1.4-3.6 4.2-5.2 7.5-5.2s6.1 1.6 7.5 5.2" />',
  };

  // pages：哪些頁面算在這個分頁底下（錢包也是會員相關功能）
  var TABS = [
    { key: "home", label: "首頁", href: "/liff/index.html", pages: ["index.html", ""] },
    { key: "pets", label: "毛孩檔案", href: "/liff/my-pets.html", pages: ["my-pets.html"] },
    { key: "book", label: "立即預約", href: "/liff/booking.html", pages: [], center: true },
    { key: "appts", label: "我的預約", href: "/liff/my-appointments.html", pages: ["my-appointments.html"] },
    { key: "member", label: "會員", href: "/liff/my-profile.html", pages: ["my-profile.html", "wallet.html"] },
  ];

  var CSS =
    ".mp-tabbar{position:fixed;left:0;right:0;bottom:0;z-index:400;background:#fff;border-top:1px solid #e3d6bf;" +
    "padding-bottom:env(safe-area-inset-bottom);font-family:'Noto Sans TC','PingFang TC','Microsoft JhengHei',sans-serif}" +
    ".mp-tabbar-in{max-width:480px;margin:0 auto;height:74px;display:grid;grid-template-columns:repeat(5,minmax(0,1fr));" +
    "align-items:center;padding:0 6px;box-sizing:border-box}" +
    ".mp-tab{display:flex;flex-direction:column;align-items:center;gap:4px;color:#5e5a52;font-size:11px;padding:8px 0;" +
    "text-decoration:none;-webkit-tap-highlight-color:transparent}" +
    ".mp-tab.active{color:" + HERO + ";font-weight:700}" +
    ".mp-tab-book{justify-self:center;width:60px;height:60px;margin-top:-30px;border-radius:50%;background:" + HERO + ";" +
    "border:3px solid #faf7f2;box-shadow:0 6px 14px rgba(120,98,78,.35);display:flex;align-items:center;" +
    "justify-content:center;color:#fff;box-sizing:border-box}" +
    ".mp-tab-book:active,.mp-tab:active{opacity:.85}" +
    "body.mp-has-tabbar{padding-bottom:calc(96px + env(safe-area-inset-bottom)) !important}";

  function icon(name, size, width) {
    return (
      '<svg width="' + size + '" height="' + size + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="' + width + '" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      ICONS[name] + "</svg>"
    );
  }

  function mount() {
    if (document.querySelector(".mp-tabbar")) return;
    var page = location.pathname.split("/").pop();

    var style = document.createElement("style");
    style.textContent = CSS;
    document.head.appendChild(style);

    var html = TABS.map(function (t) {
      if (t.center) {
        return '<a class="mp-tab-book" href="' + t.href + '" aria-label="' + t.label + '">' + icon(t.key, 26, 1.6) + "</a>";
      }
      var active = t.pages.indexOf(page) !== -1;
      return (
        '<a class="mp-tab' + (active ? " active" : "") + '" href="' + t.href + '"' +
        (active ? ' aria-current="page"' : "") + ">" + icon(t.key, 22, active ? 1.7 : 1.5) + t.label + "</a>"
      );
    }).join("");

    var nav = document.createElement("nav");
    nav.className = "mp-tabbar";
    nav.setAttribute("aria-label", "主要功能");
    nav.innerHTML = '<div class="mp-tabbar-in">' + html + "</div>";
    document.body.appendChild(nav);
    document.body.classList.add("mp-has-tabbar");
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }
})();
