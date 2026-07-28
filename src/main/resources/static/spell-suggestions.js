(() => {
    const control = document.querySelector("[data-search-suggest], [data-spell-suggest]");
    const input = control?.querySelector("input");
    const list = control?.querySelector("[role='listbox']");

    if (!control || !input || !list) return;

    let suggestions = [];
    let activeIndex = -1;
    let request;
    let timer;

    function close() {
        suggestions = [];
        activeIndex = -1;
        list.replaceChildren();
        list.hidden = true;
        input.setAttribute("aria-expanded", "false");
        input.removeAttribute("aria-activedescendant");
    }

    function setActive(index) {
        const options = [...list.querySelectorAll("[role='option']")];
        if (options.length === 0) return;

        activeIndex = (index + options.length) % options.length;
        options.forEach((option, optionIndex) => {
            option.setAttribute("aria-selected", String(optionIndex === activeIndex));
        });
        input.setAttribute("aria-activedescendant", options[activeIndex].id);
        options[activeIndex].scrollIntoView({ block: "nearest" });
    }

    function select(index) {
        const suggestion = suggestions[index];
        if (!suggestion) return;

        input.value = suggestion.nameRu;
        close();
        input.focus();
        input.dispatchEvent(new Event("input", { bubbles: true }));
    }

    function render(items) {
        suggestions = items;
        activeIndex = -1;
        list.replaceChildren();

        items.forEach((suggestion, index) => {
            const option = document.createElement("li");
            const russianName = document.createElement("span");
            const englishName = document.createElement("span");

            option.id = `${control.dataset.suggestionPrefix || "spell"}-suggestion-${index}`;
            option.className = "spell-suggestion";
            option.setAttribute("role", "option");
            option.setAttribute("aria-selected", "false");
            russianName.className = "spell-suggestion-name";
            russianName.textContent = suggestion.nameRu;
            englishName.className = "spell-suggestion-english";
            englishName.textContent = suggestion.nameEn;
            option.append(russianName, englishName);
            option.addEventListener("pointerdown", (event) => event.preventDefault());
            option.addEventListener("click", () => select(index));
            option.addEventListener("pointermove", () => setActive(index));
            list.append(option);
        });

        list.hidden = items.length === 0;
        input.setAttribute("aria-expanded", String(items.length > 0));
    }

    async function loadSuggestions() {
        const query = input.value.trim();
        if (!query) {
            close();
            return;
        }

        request?.abort();
        request = new AbortController();

        try {
            const response = await fetch(
                `${control.dataset.suggestionsUrl || "/api/spells/suggestions"}?q=${encodeURIComponent(query)}`,
                { signal: request.signal, headers: { Accept: "application/json" } },
            );
            if (!response.ok) throw new Error(`Suggestion request failed: ${response.status}`);
            render(await response.json());
        } catch (error) {
            if (error.name !== "AbortError") close();
        }
    }

    input.addEventListener("input", () => {
        clearTimeout(timer);
        request?.abort();
        timer = setTimeout(loadSuggestions, 150);
    });

    input.addEventListener("keydown", (event) => {
        if (list.hidden && event.key !== "Escape") return;

        if (event.key === "ArrowDown") {
            event.preventDefault();
            setActive(activeIndex + 1);
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive(activeIndex - 1);
        } else if (event.key === "Enter" && activeIndex >= 0) {
            event.preventDefault();
            select(activeIndex);
        } else if (event.key === "Escape") {
            close();
        }
    });

    input.addEventListener("blur", () => setTimeout(close));
    document.addEventListener("pointerdown", (event) => {
        if (!control.contains(event.target)) close();
    });
})();
