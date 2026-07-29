(() => {
    const controlSelector = "[data-search-suggest], [data-spell-suggest]";
    const descendantSelector = (selector) => `:is(${controlSelector}) ${selector}`;

    function elements(control) {
        return {
            input: control?.querySelector("[role='combobox']"),
            list: control?.querySelector("[role='listbox']"),
        };
    }

    function close(control) {
        const { input, list } = elements(control);
        if (!input || !list) return;

        list.querySelectorAll("[role='option']").forEach((option) => {
            option.setAttribute("aria-selected", "false");
        });
        list.hidden = true;
        input.setAttribute("aria-expanded", "false");
        input.removeAttribute("aria-activedescendant");
    }

    function activeIndex(list) {
        return [...list.querySelectorAll("[role='option']")]
            .findIndex((option) => option.getAttribute("aria-selected") === "true");
    }

    function setActive(control, index) {
        const { input, list } = elements(control);
        const options = [...(list?.querySelectorAll("[role='option']") || [])];
        if (!input || options.length === 0) return;

        const nextIndex = (index + options.length) % options.length;
        options.forEach((option, optionIndex) => {
            option.setAttribute("aria-selected", String(optionIndex === nextIndex));
        });
        input.setAttribute("aria-activedescendant", options[nextIndex].id);
        options[nextIndex].scrollIntoView({ block: "nearest" });
    }

    function select(control, option) {
        const { input } = elements(control);
        if (!input || !option) return;

        input.value = option.dataset.suggestionValue || "";
        close(control);
        input.focus();
        input.form?.requestSubmit();
    }

    document.addEventListener("keydown", (event) => {
        const input = event.target.closest(descendantSelector("[role='combobox']"));
        const control = input?.closest(controlSelector);
        const { list } = elements(control);
        if (!control || !list || (list.hidden && event.key !== "Escape")) return;

        const currentIndex = activeIndex(list);
        if (event.key === "ArrowDown") {
            event.preventDefault();
            setActive(control, currentIndex + 1);
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive(control, currentIndex - 1);
        } else if (event.key === "Enter" && currentIndex >= 0) {
            event.preventDefault();
            select(control, list.querySelectorAll("[role='option']")[currentIndex]);
        } else if (event.key === "Escape") {
            close(control);
        }
    });

    document.addEventListener("pointerdown", (event) => {
        const option = event.target.closest(descendantSelector("[role='option']"));
        if (option) {
            event.preventDefault();
            return;
        }
        document.querySelectorAll(controlSelector).forEach((control) => {
            if (!control.contains(event.target)) close(control);
        });
    });

    document.addEventListener("pointermove", (event) => {
        const option = event.target.closest(descendantSelector("[role='option']"));
        const control = option?.closest(controlSelector);
        if (!control || !option) return;
        const options = [...option.parentElement.querySelectorAll("[role='option']")];
        setActive(control, options.indexOf(option));
    });

    document.addEventListener("click", (event) => {
        const option = event.target.closest(descendantSelector("[role='option']"));
        const control = option?.closest(controlSelector);
        if (control && option) select(control, option);
    });

    document.addEventListener("focusout", (event) => {
        const control = event.target.closest(controlSelector);
        if (control) setTimeout(() => close(control));
    });

    document.addEventListener("htmx:afterSwap", (event) => {
        if (!(event.target instanceof Element) || !event.target.matches("[role='listbox']")) return;
        const control = event.target.closest(controlSelector);
        const { input, list } = elements(control);
        if (!input || !list) return;

        const hasOptions = list.querySelector("[role='option']") !== null;
        list.hidden = !hasOptions;
        input.setAttribute("aria-expanded", String(hasOptions));
        input.removeAttribute("aria-activedescendant");
    });

    document.addEventListener("htmx:responseError", (event) => {
        const control = event.detail.elt?.closest(controlSelector);
        if (control) close(control);
    });
})();
