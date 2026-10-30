let tmdbOptions = [];
let tmdbCursor = 0;
let tmdbSaved = 0;
let tmdbSkipped = 0;
let tmdbProcessed = 0;
let tmdbFinished = false;
let tmdbRunning = false;

const tmdbSearchButton = $('#tmdbSearch');
const tmdbSearchMessage = $('#tmdbSearchMessage');
const tmdbResults = $('#tmdbResults');
const tmdbChosen = $('#tmdbChosen');
const tmdbBatchButton = $('#tmdbBatchButton');
const tmdbBatchStatus = $('#tmdbBatchStatus');

tmdbBatchButton.textContent = 'Загрузить все постеры';

function tmdbMessage(message, error = false) {
  tmdbSearchMessage.textContent = message;
  tmdbSearchMessage.classList.toggle('error', error);
}

function tmdbClear() {
  tmdbOptions = [];
  tmdbResults.innerHTML = '';
  tmdbChosen.hidden = true;
  tmdbChosen.innerHTML = '';
  tmdbMessage('');
}

function tmdbRenderResults() {
  if (!tmdbOptions.length) {
    tmdbResults.innerHTML = `
      <p class="tmdb-empty">
        Подходящих постеров не найдено.
        Попробуйте другое название.
      </p>
    `;
    return;
  }

  tmdbResults.innerHTML = tmdbOptions.map((item, index) => `
    <article class="tmdb-result">
      <img
        src="${esc(item.posterUrl)}"
        alt="Постер: ${esc(item.title)}"
        loading="lazy"
      >
      <div class="tmdb-result-info">
        <strong>${esc(item.title)}</strong>
        <span>${esc(item.year ?? 'Год неизвестен')}</span>
        <button
          type="button"
          class="tmdb-select"
          data-tmdb-index="${index}"
        >
          Скачать и выбрать
        </button>
      </div>
    </article>
  `).join('');
}

tmdbSearchButton.addEventListener('click', async () => {
  const form = $('#titleForm');

  const title = form.elements.namedItem('title').value.trim();
  const type = form.elements.namedItem('mediaType').value;
  const year = form.elements.namedItem('releaseYear').value;

  if (!title) {
    tmdbMessage(
      'Сначала введи название фильма или сериала.',
      true
    );
    return;
  }

  tmdbSearchButton.disabled = true;
  tmdbClear();
  tmdbMessage('Ищу постеры в TMDB...');

  const params = new URLSearchParams({
    title,
    type
  });

  if (year) {
    params.set('year', year);
  }

  try {
    await APP_READY;

    tmdbOptions = await api(
      '/api/admin/tmdb/search?' + params.toString()
    );

    tmdbRenderResults();

    tmdbMessage(
      tmdbOptions.length
        ? `Найдено вариантов: ${tmdbOptions.length}. Нажми «Скачать и выбрать».`
        : 'Постеры не найдены.'
    );

  } catch (error) {
    tmdbMessage(error.message, true);
  } finally {
    tmdbSearchButton.disabled = false;
  }
});

tmdbResults.addEventListener('click', async event => {
  const button = event.target.closest('[data-tmdb-index]');

  if (!button || button.disabled) {
    return;
  }

  const selected = tmdbOptions[
    Number(button.dataset.tmdbIndex)
  ];

  if (!selected) {
    return;
  }

  button.disabled = true;
  tmdbMessage('Скачиваю постер в img/posters...');

  try {
    await APP_READY;

    const result = await api(
      '/api/admin/tmdb/download',
      {
        method: 'POST',
        body: JSON.stringify({
          posterUrl: selected.posterUrl
        })
      }
    );

    const form = $('#titleForm');
    const posterField = form.elements.namedItem('posterUrl');

    posterField.value = result.localPath;

    posterField.dispatchEvent(
      new Event('input', { bubbles: true })
    );

    tmdbChosen.hidden = false;

    tmdbChosen.innerHTML = `
      <img
        src="${esc(result.localPath)}"
        alt="Выбранный постер"
      >
      <div>
        <strong>Выбран: ${esc(selected.title)}</strong>
        <p>${esc(selected.year ?? '')}</p>
        <span>
          Файл сохранён: ${esc(result.localPath)}.
          Нажми «Сохранить» в форме фильма.
        </span>
      </div>
    `;

    tmdbMessage(
      'Постер скачан на сервер. Теперь сохрани фильм или сериал.'
    );

  } catch (error) {
    tmdbMessage(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#resetTitle').addEventListener('click', tmdbClear);

$('#adminTitleList').addEventListener('click', event => {
  if (event.target.closest('[data-title-edit]')) {
    tmdbClear();
  }
});

$('#titleForm').addEventListener('submit', () => {
  tmdbMessage('Сохраняю форму фильма...');
});

function tmdbBatchMessage(message, error = false) {
  tmdbBatchStatus.textContent = message;
  tmdbBatchStatus.classList.toggle('error', error);
}

function tmdbUpdateProgress() {
  tmdbBatchMessage(
    `Обработано: ${tmdbProcessed}. ` +
    `Локально сохранено: ${tmdbSaved}. ` +
    `Пропущено: ${tmdbSkipped}. ` +
    (
      tmdbFinished
        ? 'Загрузка завершена.'
        : 'Загрузка продолжается...'
    )
  );
}

function tmdbPause(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function tmdbFillAll() {
  if (tmdbRunning) {
    return;
  }

  if (tmdbFinished) {
    tmdbCursor = 0;
    tmdbSaved = 0;
    tmdbSkipped = 0;
    tmdbProcessed = 0;
    tmdbFinished = false;
  }

  tmdbRunning = true;
  tmdbBatchButton.disabled = true;
  tmdbBatchButton.textContent = 'Загрузка постеров...';

  try {
    await APP_READY;

    while (!tmdbFinished) {
      tmdbUpdateProgress();

      const params = new URLSearchParams({
        afterId: String(tmdbCursor),
        limit: '5'
      });

      const result = await api(
        '/api/admin/tmdb/autofill?' + params.toString(),
        {
          method: 'POST'
        }
      );

      if (
        result.nextAfterId === tmdbCursor &&
        result.processed > 0
      ) {
        throw new Error(
          'Сервер не изменил позицию загрузки. Проверь журналы Spring Boot.'
        );
      }

      tmdbCursor = result.nextAfterId;
      tmdbProcessed += result.processed;
      tmdbSaved += result.saved;
      tmdbSkipped += result.skipped;

      tmdbFinished = !result.hasMore || result.processed === 0;

      tmdbUpdateProgress();

      if (!tmdbFinished) {
        await tmdbPause(500);
      }
    }

    tmdbBatchMessage(
      `Готово! Обработано: ${tmdbProcessed}. ` +
      `Локально сохранено: ${tmdbSaved}. ` +
      `Пропущено: ${tmdbSkipped}.`
    );

    tmdbBatchButton.textContent = 'Проверить снова';

    await loadAdminTitles();

  } catch (error) {
    tmdbBatchMessage(
      `Загрузка прервана. ` +
      `Обработано: ${tmdbProcessed}. ` +
      `Сохранено: ${tmdbSaved}. ` +
      `Ошибка: ${error.message}`,
      true
    );

    tmdbBatchButton.textContent = 'Продолжить загрузку';

  } finally {
    tmdbRunning = false;
    tmdbBatchButton.disabled = false;
  }
}

tmdbBatchButton.addEventListener('click', tmdbFillAll);