(() => {
    const root = document.documentElement;
    const toggle = document.querySelector("[data-theme-toggle]");
    const icon = toggle?.querySelector("[data-theme-icon]");
    const label = toggle?.querySelector("[data-theme-label]");

    if (!toggle || !icon || !label) return;

    function render() {
        const dark = root.dataset.theme === "dark";
        icon.textContent = dark ? "☾" : "☀︎";
        label.textContent = dark ? "Темная" : "Светлая";
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
