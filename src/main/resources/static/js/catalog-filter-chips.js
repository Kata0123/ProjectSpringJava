(function () {
  'use strict';

  const definitions = [
    {
      selectId: 'fg',
      optionsId: 'genreFilterOptions'
    },
    {
      selectId: 'fy',
      optionsId: 'yearFilterOptions'
    },
    {
      selectId: 'ft',
      optionsId: 'typeFilterOptions'
    }
  ];

  function createChip(option, select) {
    const button = document.createElement('button');

    button.type = 'button';
    button.className = 'catalog-filter-chip';
    button.dataset.value = option.value;
    button.textContent = option.textContent.trim();

    const selected = select.value === option.value;

    button.classList.toggle('active', selected);
    button.setAttribute('aria-pressed', String(selected));

    button.addEventListener('click', () => {
      if (select.value !== option.value) {
        select.value = option.value;

        select.dispatchEvent(
          new Event('input', {
            bubbles: true
          })
        );

        select.dispatchEvent(
          new Event('change', {
            bubbles: true
          })
        );
      }

      syncChips(select);
    });

    return button;
  }

  function syncChips(select) {
    const definition = definitions.find(
      item => item.selectId === select.id
    );

    if (!definition) {
      return;
    }

    const container = document.getElementById(
      definition.optionsId
    );

    if (!container) {
      return;
    }

    const buttons = container.querySelectorAll(
      '.catalog-filter-chip'
    );

    buttons.forEach(button => {
      const selected = button.dataset.value === select.value;

      button.classList.toggle('active', selected);
      button.setAttribute('aria-pressed', String(selected));
    });
  }

  function renderChips(select, container) {
    const fragment = document.createDocumentFragment();

    const options = Array.from(select.options)
      .filter(option => !option.disabled && !option.hidden);

    for (const option of options) {
      fragment.appendChild(createChip(option, select));
    }

    container.replaceChildren(fragment);

    syncChips(select);
  }

  function initFilter(definition) {
    const select = document.getElementById(
      definition.selectId
    );

    const container = document.getElementById(
      definition.optionsId
    );

    if (!select || !container) {
      return;
    }

    select.classList.add('catalog-native-select');

    const filterBar = select.closest('.filters');

    if (filterBar) {
      filterBar.classList.add('catalog-chip-mode');
    }

    let refreshQueued = false;

    const refresh = () => {
      if (refreshQueued) {
        return;
      }

      refreshQueued = true;

      queueMicrotask(() => {
        refreshQueued = false;
        renderChips(select, container);
      });
    };

    select.addEventListener('change', () => {
      syncChips(select);
    });

    select.addEventListener('input', () => {
      syncChips(select);
    });

    const observer = new MutationObserver(refresh);

    observer.observe(select, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['value', 'disabled', 'hidden']
    });

    refresh();
  }

  function initCatalogFilterChips() {
    definitions.forEach(initFilter);
  }

  if (document.readyState === 'loading') {
    document.addEventListener(
      'DOMContentLoaded',
      initCatalogFilterChips,
      { once: true }
    );
  } else {
    initCatalogFilterChips();
  }
})();