// Full-screen sheets (history, day, settings, welcome). Each one opened adds a browser
// history step, so Android's back gesture closes the sheet instead of leaving the app.
const stack = [];

export function openSheet(sel) {
  const el = document.querySelector(sel);
  if (!el.hidden) return;
  el.hidden = false;
  stack.push(el);
  history.pushState({ sheet: sel }, "");
  el.querySelector("h1, b, button")?.focus({ preventScroll: true });
}

export function closeTop() { if (stack.length) history.back(); }

addEventListener("popstate", () => {
  const el = stack.pop();
  if (el) el.hidden = true;
  el?.dispatchEvent(new Event("closed"));
});

document.addEventListener("click", (e) => { if (e.target.closest("[data-close]")) closeTop(); });
