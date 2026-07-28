(() => {
    const controls = [...document.querySelectorAll("[data-multi-select]")];

    function updateValue(control) {
        const value = control.querySelector("[data-multi-select-value]");
        const selected = [...control.querySelectorAll("input:checked")]
            .map((input) => input.nextElementSibling?.textContent.trim())
            .filter(Boolean);

        if (!value.dataset.emptyLabel) {
            value.dataset.emptyLabel = value.textContent.trim();
        }

        value.textContent = selected.length > 0
            ? selected.join(", ")
            : value.dataset.emptyLabel;
        value.dataset.empty = String(selected.length === 0);
        value.title = selected.join(", ");
    }

    controls.forEach((control) => {
        updateValue(control);
        control.addEventListener("change", () => updateValue(control));
        control.addEventListener("toggle", () => {
            if (!control.open) return;
            controls
                .filter((other) => other !== control)
                .forEach((other) => { other.open = false; });
        });
    });

    document.addEventListener("click", (event) => {
        controls
            .filter((control) => control.open && !control.contains(event.target))
            .forEach((control) => { control.open = false; });
    });

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") return;
        controls
            .filter((control) => control.open)
            .forEach((control) => {
                control.open = false;
                control.querySelector("summary")?.focus();
            });
    });
})();
