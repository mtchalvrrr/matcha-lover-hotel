// Keep the customer area in this tab; browser Back returns to the customer area,
// not to the portal-selection homepage.
history.pushState({ customerArea: true }, "", location.href);

window.addEventListener("popstate", function () {
    window.location.replace("/customer");
});
