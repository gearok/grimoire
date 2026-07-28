(() => {
    const root = document.documentElement;
    const toggle = document.querySelector("[data-theme-toggle]");
    const icon = toggle?.querySelector("[data-theme-icon]");

    if (!toggle || !icon) return;

    function render() {
        const dark = root.dataset.theme === "dark";
        icon.textContent = dark ? "dark_mode" : "wb_sunny";
        toggle.setAttribute("aria-pressed", String(dark));
        toggle.setAttribute(
            "aria-label",
            dark ? "Включить светлую тему" : "Включить темную тему",
        );
        toggle.title = dark ? "Темная тема" : "Светлая тема";
    }

    toggle.addEventListener("click", () => {
        const nextTheme = root.dataset.theme === "dark" ? "light" : "dark";
        root.dataset.theme = nextTheme;
        try {
            localStorage.setItem("grimoire-theme", nextTheme);
        } catch (_) {
            // The selected theme still applies for this page when storage is unavailable.
        }
        render();
    });

    render();
})();
