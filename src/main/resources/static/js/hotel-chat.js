(() => {
  const widget = document.getElementById("hotelChatWidget");
  if (!widget) return;

  const toggle = document.getElementById("chatToggle");
  const panel = document.getElementById("chatPanel");
  const close = document.getElementById("chatClose");
  const form = document.getElementById("chatForm");
  const input = document.getElementById("chatInput");
  const messages = document.getElementById("chatMessages");
  const send = form?.querySelector('button[type="submit"]');
  const csrf = form?.querySelector('input[name="_csrf"]')?.value;

  function setOpen(open) {
    panel.hidden = !open;
    toggle.setAttribute("aria-expanded", String(open));
    if (open) input.focus();
  }

  function addMessage(text, kind) {
    const message = document.createElement("p");
    message.className = "chat-message " + (kind === "user" ? "user-message" : "assistant-message");
    message.textContent = text;
    messages.appendChild(message);
    messages.scrollTop = messages.scrollHeight;
    return message;
  }

  toggle?.addEventListener("click", () => setOpen(panel.hidden));
  close?.addEventListener("click", () => setOpen(false));

  form?.addEventListener("submit", async event => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message || send.disabled) return;

    addMessage(message, "user");
    input.value = "";
    input.disabled = true;
    send.disabled = true;
    const pending = addMessage("Thinking…", "assistant");

    try {
      const response = await fetch("/api/chat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": csrf
        },
        body: JSON.stringify({ message })
      });
      const data = await response.json();
      pending.textContent = data.reply || "I could not prepare a response. Please try again.";
      if (data.redirectUrl) {
        window.location.assign(data.redirectUrl);
        return;
      }
      if (!response.ok && response.status !== 429) {
        pending.textContent = data.reply || "The assistant is temporarily unavailable.";
      }
    } catch (error) {
      pending.textContent = "I could not connect just now. Please try again.";
    } finally {
      input.disabled = false;
      send.disabled = false;
      input.focus();
    }
  });
})();
