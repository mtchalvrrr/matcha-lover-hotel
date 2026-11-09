(function () {
    // Keep authenticated customers on the portal; signing out is the intentional exit.
    history.pushState({ customerDashboard: true }, "", location.href);

    window.addEventListener("popstate", function () {
        history.pushState({ customerDashboard: true }, "", location.href);
    });

    document.addEventListener("keydown", function (event) {
        const target = event.target;
        const editing = target && (target.isContentEditable ||
            ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName));

        if (!editing && (event.key === "Backspace" ||
            (event.altKey && event.key === "ArrowLeft"))) {
            event.preventDefault();
        }
    });
})();
