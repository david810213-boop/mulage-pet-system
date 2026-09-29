/*
 * 核對頁「美容狀況照片」元件（需求，2026-09-29）
 * 網頁版（預約／現場單）與員工手機版的核對頁共用。
 *
 * 用法：頁面放一個容器
 *   <div id="groomingPhotos" data-form="表單 id" data-max="5"
 *        data-initial-urls="網址1|網址2" data-initial-ids="id1|id2"></div>
 * 再呼叫 GroomingPhotos.init(document.getElementById("groomingPhotos"))。
 *
 * 流程：選照片（手機會出現「拍照／照片圖庫」選項）→ 在瀏覽器先縮小壓縮
 * （長邊 1600px、JPEG）→ 逐張上傳到 /api/grooming-photos → 拿到網址後在表單
 * 裡加上隱藏欄位 photoUrls / photoPublicIds，送出核對時一起帶到後端。
 * 還在上傳中就按送出會被擋下來。
 */
(function () {
  var MAX_EDGE = 1600;
  var QUALITY = 0.82;

  function injectStyle() {
    if (document.getElementById("gp-style")) return;
    var css =
      ".gp-grid{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}" +
      ".gp-item{position:relative;width:84px;height:84px;border-radius:10px;overflow:hidden;background:#eef0ec;flex:none}" +
      ".gp-item img{width:100%;height:100%;object-fit:cover;display:block}" +
      ".gp-item .gp-x{position:absolute;top:3px;right:3px;width:24px;height:24px;border-radius:50%;border:0;" +
      "background:rgba(0,0,0,.55);color:#fff;font-size:15px;line-height:24px;padding:0;cursor:pointer}" +
      ".gp-item .gp-busy{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;" +
      "background:rgba(255,255,255,.7);font-size:12px;color:#444}" +
      ".gp-item.gp-err .gp-busy{background:rgba(192,57,43,.85);color:#fff;text-align:center;padding:4px}" +
      ".gp-add{width:84px;height:84px;border-radius:10px;border:1.5px dashed #b9b2a3;background:transparent;" +
      "color:#6b6656;font-size:12px;line-height:1.4;cursor:pointer;flex:none;font-family:inherit}" +
      ".gp-hint{font-size:12px;color:#8a8474;margin-top:6px}";
    var st = document.createElement("style");
    st.id = "gp-style";
    st.textContent = css;
    document.head.appendChild(st);
  }

  // 把照片縮小壓縮成 JPEG Blob；讀不了（例如格式不支援）就退回原檔
  function compress(file) {
    return new Promise(function (resolve) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        var w = img.naturalWidth, h = img.naturalHeight;
        var scale = Math.min(1, MAX_EDGE / Math.max(w, h));
        var cw = Math.round(w * scale), ch = Math.round(h * scale);
        var canvas = document.createElement("canvas");
        canvas.width = cw;
        canvas.height = ch;
        canvas.getContext("2d").drawImage(img, 0, 0, cw, ch);
        URL.revokeObjectURL(url);
        canvas.toBlob(function (blob) {
          resolve(blob || file);
        }, "image/jpeg", QUALITY);
      };
      img.onerror = function () {
        URL.revokeObjectURL(url);
        resolve(file);
      };
      img.src = url;
    });
  }

  async function readJson(res) {
    var text = await res.text();
    try {
      return JSON.parse(text);
    } catch (e) {
      return { message: text };
    }
  }

  function init(root) {
    if (!root || root.dataset.gpReady) return;
    root.dataset.gpReady = "1";
    injectStyle();

    var form = document.getElementById(root.dataset.form);
    var max = parseInt(root.dataset.max || "5", 10);
    var items = []; // { el, url, publicId, pending, failed }

    var grid = document.createElement("div");
    grid.className = "gp-grid";
    var input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.multiple = true;
    input.hidden = true;
    var addBtn = document.createElement("button");
    addBtn.type = "button";
    addBtn.className = "gp-add";
    addBtn.innerHTML = "📷<br>拍照／<br>上傳照片";
    var hint = document.createElement("div");
    hint.className = "gp-hint";
    root.appendChild(grid);
    root.appendChild(input);
    root.appendChild(hint);
    grid.appendChild(addBtn);

    function refresh() {
      var done = items.filter(function (i) { return i.url; }).length;
      var busy = items.some(function (i) { return i.pending; });
      addBtn.hidden = items.length >= max;
      hint.textContent = busy
        ? "照片上傳中，請稍候…"
        : "選填，最多 " + max + " 張（已放 " + done + " 張）。照片會跟備注一起存進毛孩的美容紀錄，家長在 LINE 也看得到";
      syncHidden();
    }

    // 表單裡的隱藏欄位永遠跟畫面上成功上傳的照片一致
    function syncHidden() {
      if (!form) return;
      form.querySelectorAll("input[data-gp]").forEach(function (el) { el.remove(); });
      items.forEach(function (i) {
        if (!i.url) return;
        var u = document.createElement("input");
        u.type = "hidden";
        u.name = "photoUrls";
        u.value = i.url;
        u.setAttribute("data-gp", "1");
        var p = document.createElement("input");
        p.type = "hidden";
        p.name = "photoPublicIds";
        p.value = i.publicId;
        p.setAttribute("data-gp", "1");
        form.appendChild(u);
        form.appendChild(p);
      });
    }

    function addTile(src) {
      var el = document.createElement("div");
      el.className = "gp-item";
      var img = document.createElement("img");
      img.src = src;
      img.alt = "美容狀況照片";
      var x = document.createElement("button");
      x.type = "button";
      x.className = "gp-x";
      x.setAttribute("aria-label", "移除照片");
      x.textContent = "×";
      el.appendChild(img);
      el.appendChild(x);
      grid.insertBefore(el, addBtn);
      return el;
    }

    function removeItem(item) {
      items = items.filter(function (i) { return i !== item; });
      item.el.remove();
      if (item.publicId) {
        // 送出核對前移除的照片，順手從圖床刪掉（失敗也沒關係）
        fetch("/api/grooming-photos?publicId=" + encodeURIComponent(item.publicId), {
          method: "DELETE",
          credentials: "same-origin",
        }).catch(function () {});
      }
      refresh();
    }

    async function upload(file) {
      var previewUrl = URL.createObjectURL(file);
      var item = { pending: true };
      item.el = addTile(previewUrl);
      var busy = document.createElement("div");
      busy.className = "gp-busy";
      busy.textContent = "上傳中…";
      item.el.appendChild(busy);
      item.el.querySelector(".gp-x").addEventListener("click", function () {
        if (!item.pending) removeItem(item);
      });
      items.push(item);
      refresh();
      try {
        var blob = await compress(file);
        var fd = new FormData();
        fd.append("file", blob, "photo.jpg");
        var res = await fetch("/api/grooming-photos", {
          method: "POST",
          body: fd,
          credentials: "same-origin",
        });
        var data = await readJson(res);
        if (!res.ok || !data.url) throw new Error(data.message || "上傳失敗");
        item.url = data.url;
        item.publicId = data.publicId;
        busy.remove();
      } catch (e) {
        item.failed = true;
        item.el.classList.add("gp-err");
        busy.textContent = "失敗，點 × 移除";
      } finally {
        item.pending = false;
        URL.revokeObjectURL(previewUrl);
        refresh();
      }
    }

    addBtn.addEventListener("click", function () { input.click(); });
    input.addEventListener("change", function () {
      var files = Array.prototype.slice.call(input.files || []);
      input.value = "";
      var room = max - items.length;
      if (files.length > room) {
        alert("照片最多 " + max + " 張，這次只會加入前 " + room + " 張");
        files = files.slice(0, room);
      }
      files.forEach(function (f) {
        if (f.type && f.type.indexOf("image/") !== 0) return;
        upload(f);
      });
    });

    // 核對失敗退回頁面時，把已經上傳好的照片放回來
    var initUrls = (root.dataset.initialUrls || "").split("|").filter(Boolean);
    var initIds = (root.dataset.initialIds || "").split("|").filter(Boolean);
    if (initUrls.length && initUrls.length === initIds.length) {
      initUrls.slice(0, max).forEach(function (u, idx) {
        var item = { url: u, publicId: initIds[idx], pending: false };
        item.el = addTile(u);
        item.el.querySelector(".gp-x").addEventListener("click", function () { removeItem(item); });
        items.push(item);
      });
    }

    if (form) {
      form.addEventListener(
        "submit",
        function (e) {
          if (items.some(function (i) { return i.pending; })) {
            e.preventDefault();
            e.stopImmediatePropagation();
            alert("照片還在上傳中，請稍候再送出");
            return;
          }
          syncHidden();
        },
        true, // 搶在頁面自己的送出處理之前檢查
      );
    }
    refresh();
  }

  window.GroomingPhotos = { init: init };
})();
