/* Shared confirmation and validation popups for consequential hotel actions. */
(function () {
  "use strict";

  function getModal() {
    let dialog = document.getElementById("decision-confirmation-dialog");
    if (dialog) return dialog;

    dialog = document.createElement("dialog");
    dialog.id = "decision-confirmation-dialog";
    dialog.className = "decision-dialog";
    dialog.setAttribute("aria-labelledby", "decision-dialog-title");
    dialog.innerHTML = `
      <div class="decision-dialog-panel">
        <p class="decision-dialog-kicker" id="decision-dialog-kicker">PLEASE CONFIRM</p>
        <h2 id="decision-dialog-title">Are you sure?</h2>
        <p id="decision-dialog-message"></p>
        <div class="decision-dialog-actions">
          <button type="button" class="decision-dialog-cancel" id="decision-dialog-cancel">Go back</button>
          <button type="button" class="decision-dialog-confirm" id="decision-dialog-confirm">Confirm</button>
        </div>
      </div>`;
    document.body.appendChild(dialog);
    return dialog;
  }

  function showPopup(options) {
    const dialog = getModal();
    const title = dialog.querySelector("#decision-dialog-title");
    const kicker = dialog.querySelector("#decision-dialog-kicker");
    const message = dialog.querySelector("#decision-dialog-message");
    const cancel = dialog.querySelector("#decision-dialog-cancel");
    const confirm = dialog.querySelector("#decision-dialog-confirm");
    title.textContent = options.title || "Please confirm";
    kicker.textContent = options.kind === "error" ? "ACTION NOT COMPLETED" : "PLEASE CONFIRM";
    message.textContent = options.message || "";
    cancel.hidden = options.kind === "error";
    confirm.textContent = options.kind === "error" ? "Close" : (options.confirmLabel || "Confirm");
    confirm.className = options.kind === "error" ? "decision-dialog-confirm decision-dialog-close" : "decision-dialog-confirm";
    dialog.showModal();

    return new Promise(function (resolve) {
      function finish(value) {
        dialog.close();
        cancel.removeEventListener("click", onCancel);
        confirm.removeEventListener("click", onConfirm);
        dialog.removeEventListener("cancel", onEscape);
        resolve(value);
      }
      function onCancel() { finish(false); }
      function onConfirm() { finish(true); }
      function onEscape(event) { event.preventDefault(); finish(false); }
      cancel.addEventListener("click", onCancel, { once: true });
      confirm.addEventListener("click", onConfirm, { once: true });
      dialog.addEventListener("cancel", onEscape, { once: true });
      confirm.focus();
    });
  }

  function actionDetails(form, submitter) {
    const action = (form.getAttribute("action") || "").toLowerCase();
    const label = (submitter ? (submitter.innerText || submitter.value || "") : "").trim().toLowerCase();
    const combined = action + " " + label;

    if (/logout|sign out|log out/.test(combined)) {
      return { title: "Sign out?", message: "Are you sure you want to sign out of your account?", confirmLabel: "Sign out" };
    }
    if (/checkout/.test(action) || /pay online|full payment|continue to full payment/.test(label)) {
      return { title: "Continue to payment?", message: "You will be redirected to the payment provider. Your reservation will only be marked as paid after the payment provider confirms the transaction.", confirmLabel: "Continue" };
    }
    if (/no-show/.test(combined)) {
      return { title: "Mark as no-show?", message: "This will record the guest as a no-show. Continue only after verifying the reservation and arrival details.", confirmLabel: "Mark no-show" };
    }
    if (/check-out|check out/.test(combined)) {
      return { title: "Check out this guest?", message: "This will record the guest's checkout and update the reservation status.", confirmLabel: "Check out" };
    }
    if (/check-in|check in/.test(combined)) {
      return { title: "Check in this guest?", message: "Confirm that the guest has arrived and is eligible to check in.", confirmLabel: "Check in" };
    }
    if (/reject/.test(combined)) {
      return { title: "Reject this request?", message: "This decision will be recorded. Please make sure the rejection reason is accurate before continuing.", confirmLabel: "Reject" };
    }
    if (/approve|authorize/.test(combined)) {
      return { title: "Approve this request?", message: "This decision may change the reservation, account, or payment record. Please verify the details before continuing.", confirmLabel: "Approve" };
    }
    if (/cancel/.test(combined)) {
      return { title: "Cancel this reservation or request?", message: "This action may not be reversible. Please review the record and reason before continuing.", confirmLabel: "Confirm cancellation" };
    }
    if (/refund|void/.test(combined)) {
      return { title: "Confirm payment action?", message: "This action affects a financial record. Verify the amount, transaction, and authorization before continuing.", confirmLabel: "Continue" };
    }
    if (/deactivation/.test(action) || /deactivation request/.test(label)) { return { title: "Submit account deactivation request?", message: "Your request will be sent for Super Admin review. Your account remains active until it is approved.", confirmLabel: "Submit request" }; }
    if (/deactivat|suspend|reactivat|activate|status|enable|disable|readiness/.test(combined)) {
      return { title: "Change account or room status?", message: "This will change whether the account or room is active and may affect access or availability.", confirmLabel: "Confirm change" };
    }
    if (/delete|remove/.test(combined)) {
      return { title: "Remove this item?", message: "This action may be permanent. Make sure you selected the correct item.", confirmLabel: "Remove" };
    }
    if (/reservation|booking/.test(combined) && /create|submit|save|continue/.test(combined)) {
      return { title: "Submit reservation changes?", message: "Please review the reservation details, dates, room, guest information, and total before submitting.", confirmLabel: "Submit" };
    }
    if (/save hotel settings|save room|save profile|change password|reset password|save reservation changes|create admin|create booking|upload photo|set as cover|backup/.test(combined)) {
      return { title: "Save these changes?", message: "Please review the information before submitting this change.", confirmLabel: "Save changes" };
    }
    return null;
  }

  document.addEventListener("submit", async function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || form.dataset.confirmed === "true") return;
    if ((form.getAttribute("method") || "get").toLowerCase() !== "post") return;

    const submitter = event.submitter || form.querySelector('button[type="submit"],input[type="submit"]');
    const details = actionDetails(form, submitter);
    if (!details) return;

    event.preventDefault();
    const accepted = await showPopup(details);
    if (!accepted) return;
    form.dataset.confirmed = "true";
    if (typeof form.requestSubmit === "function") {
      if (submitter && submitter.form === form) form.requestSubmit(submitter);
      else form.requestSubmit();
    } else {
      HTMLFormElement.prototype.submit.call(form);
    }
  }, true);

  document.addEventListener("DOMContentLoaded", function () {
    const error = document.querySelector(".alert-error, [data-validation-popup]");
    if (error) {
      const message = (error.getAttribute("data-validation-popup") || error.textContent || "").trim();
      if (message) showPopup({ kind: "error", title: "We couldn't complete that action", message: message + " Please review the information and try again." });
      return;
    }
    const conflict = document.querySelector(".eyebrow");
    if (conflict && conflict.textContent.trim() === "REQUEST NOT COMPLETED") {
      const message = document.querySelector(".portal-hero .intro");
      if (message) showPopup({ kind: "error", title: "This action could not be completed", message: message.textContent.trim() });
    }
  });
})();