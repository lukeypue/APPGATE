(() => {
  if (window.__aiBrowserSiteBrainBridge) return;
  window.__aiBrowserSiteBrainBridge = true;

  const clean = (v) => (v || "").replace(/\s+/g, " ").trim();
  const visible = (el) => {
    if (!el) return false;
    const s = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    return s.display !== "none" && s.visibility !== "hidden" && r.width > 0 && r.height > 0;
  };
  const label = (el) => clean(
    el.getAttribute("aria-label") ||
    el.innerText ||
    el.textContent ||
    el.getAttribute("title") ||
    el.getAttribute("placeholder") ||
    el.getAttribute("name") ||
    ""
  ).slice(0, 180);

  function snapshot() {
    const selectors = 'a[href],button,input,select,textarea,[role="button"],[role="link"],[role="tab"],[role="menuitem"],[role="combobox"],[role="listbox"],[role="option"],[aria-haspopup="listbox"],[aria-expanded],[aria-label]';
    const elements = [];
    Array.from(document.querySelectorAll(selectors)).slice(0, 800).forEach((el, i) => {
      if (!visible(el)) return;
      const type = (el.getAttribute("type") || "").toLowerCase();
      if (type === "password" || type === "hidden" || type === "file") return;
      elements.push({
        id: "g" + i,
        tag: (el.tagName || "").toLowerCase(),
        role: el.getAttribute("role") || null,
        label: label(el),
        href: el.href || null,
        inputType: type || null,
        selected: !!(el.checked || el.selected || el.getAttribute("aria-selected") === "true" || el.getAttribute("aria-pressed") === "true"),
        disabled: !!(el.disabled || el.getAttribute("aria-disabled") === "true"),
        locator: el.id ? ("#" + CSS.escape(el.id)) : null
      });
    });
    return {
      type: "SNAPSHOT",
      url: location.href,
      host: location.host.toLowerCase(),
      title: document.title || "",
      readyState: document.readyState || "",
      textLength: ((document.body && document.body.innerText) || "").length,
      scrollHeight: document.body ? document.body.scrollHeight : 0,
      controls: elements
    };
  }

  function findTarget(command) {
    const id = command && command.elementId;
    if (!id || !id.startsWith("g")) return null;
    const index = Number(id.substring(1));
    const nodes = Array.from(document.querySelectorAll(
      'a[href],button,input,select,textarea,[role="button"],[role="link"],[role="tab"],[role="menuitem"],[role="combobox"],[role="listbox"],[role="option"],[aria-haspopup="listbox"],[aria-expanded],[aria-label]'
    ));
    return Number.isInteger(index) ? nodes[index] : null;
  }

  function execute(command) {
    const el = findTarget(command);
    if (!el || !visible(el)) return {type:"ACTION_RESULT", ok:false, reason:"TARGET_MISSING"};
    const action = String(command.action || "").toUpperCase();
    if (action === "CLICK") {
      el.click();
      return {type:"ACTION_RESULT", ok:true, result:"CLICKED"};
    }
    if (action === "FILL") {
      const value = String(command.value || "");
      if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement)) {
        return {type:"ACTION_RESULT", ok:false, reason:"NOT_EDITABLE"};
      }
      el.focus();
      const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, "value")?.set;
      if (setter) setter.call(el, value); else el.value = value;
      el.dispatchEvent(new Event("input", {bubbles:true}));
      el.dispatchEvent(new Event("change", {bubbles:true}));
      return {type:"ACTION_RESULT", ok:true, result:"FILLED"};
    }
    return {type:"ACTION_RESULT", ok:false, reason:"UNSUPPORTED_ACTION"};
  }

  const port = browser.runtime.connectNative("aibrowser");
  port.onMessage.addListener((message) => {
    try {
      if (!message || !message.type) return;
      if (message.type === "SNAPSHOT_REQUEST") port.postMessage(snapshot());
      else if (message.type === "ACTION") port.postMessage(execute(message));
      else if (message.type === "PING") port.postMessage({type:"PONG", url:location.href});
    } catch (e) {
      port.postMessage({type:"BRIDGE_ERROR", message:String(e)});
    }
  });

  port.postMessage({type:"BRIDGE_READY", url:location.href});
})();
