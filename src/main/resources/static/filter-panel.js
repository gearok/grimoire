(() => {
    const panel = document.querySelector("[data-filters-panel]");
    const toggle = document.querySelector("[data-filters-toggle]");

    if (!panel || !toggle) return;

    function setExpanded(expanded) {
        panel.classList.toggle("filters-expanded", expanded);
        toggle.setAttribute("aria-expanded", String(expanded));
    }

    setExpanded(panel.querySelector(".filter-option:checked") !== null);

    toggle.addEventListener("click", () => {
        setExpanded(toggle.getAttribute("aria-expanded") !== "true");
    });
})();
