const ini = n => String(n || '')
  .trim()
  .split(/\s+/)
  .slice(0, 2)
  .map(w => w[0] || '')
  .join('')
  .toUpperCase();

const normalizePersonName = value => String(value || '')
  .normalize('NFKC')
  .toLocaleLowerCase('ru-RU')
  .replace(/ё/g, 'е')
  .replace(/[^\p{L}\p{N}]+/gu, ' ')
  .trim();

let peoplePhotos = new Map();

function validPersonPhoto(value) {
  const url = String(value || '').trim();

  return /^\/img\/people\/[a-zA-Z0-9._-]+\.(jpg|jpeg|png|webp)$/i.test(url)
    ? url
    : '';
}

async function loadPeoplePhotos() {
  try {
    const response = await fetch('/api/people/photos', {
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json'
      }
    });

    if (!response.ok) {
      throw new Error(`Ошибка загрузки фотографий: ${response.status}`);
    }

    const data = await response.json();

    if (!Array.isArray(data)) {
      throw new Error('Некорректный ответ API фотографий');
    }

    const photos = new Map();

    for (const person of data) {
      const photo = validPersonPhoto(
        person.photoUrl ?? person.photo_url
      );

      if (!photo) {
        continue;
      }

      const names = [
        person.name,
        person.fullName,
        person.full_name,
        person.originalName,
        person.original_name
      ];

      for (const name of names) {
        const key = normalizePersonName(name);

        if (key) {
          photos.set(key, photo);
        }
      }
    }

    peoplePhotos = photos;
  } catch (error) {
    console.warn('Не удалось загрузить фотографии актёров:', error);
    peoplePhotos = new Map();
  }
}

function personName(person) {
  if (typeof person === 'string') {
    return person;
  }

  return String(
    person?.name ??
    person?.fullName ??
    person?.full_name ??
    ''
  );
}

function personPhoto(person) {
  if (person && typeof person === 'object') {
    const ownPhoto = validPersonPhoto(
      person.photoUrl ??
      person.photo_url
    );

    if (ownPhoto) {
      return ownPhoto;
    }
  }

  const name = personName(person);

  return peoplePhotos.get(normalizePersonName(name)) || '';
}

function people(list) {
  if (!Array.isArray(list) || !list.length) {
    return '<p class="empty">Данные пока отсутствуют.</p>';
  }

  return `
    <div class="people">
      ${list.map(person => {
        const name = personName(person);
        const photo = personPhoto(person);
        const initials = ini(name) || '?';

        return `
          <div class="person">
            <span
              class="pav"
              style="--art:${art(name)}"
            >
              <span class="pav-letters">
                ${esc(initials)}
              </span>
              ${photo ? `
                <img
                  class="pav-photo"
                  src="${esc(photo)}"
                  alt="${esc(name)}"
                  loading="lazy"
                  decoding="async"
                >
              ` : ''}
            </span>
            ${esc(name)}
          </div>
        `;
      }).join('')}
    </div>
  `;
}

function initPersonPhotoStyles() {
  if (document.getElementById('movie-person-photos-style')) {
    return;
  }

  const style = document.createElement('style');

  style.id = 'movie-person-photos-style';

  style.textContent = `
    .people .person .pav {
      position: relative;
      display: grid;
      place-items: center;
      width: 64px;
      height: 64px;
      min-width: 64px;
      min-height: 64px;
      overflow: hidden;
      border-radius: 50%;
      isolation: isolate;
    }

    .people .person .pav-letters {
      display: grid;
      place-items: center;
      width: 100%;
      height: 100%;
      color: inherit;
      font: inherit;
    }

    .people .person .pav-photo {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: center 20%;
      border-radius: 50%;
      z-index: 1;
      display: block;
    }

    .people .person .pav-photo:not(.loaded) {
      opacity: 0;
    }

    .people .person .pav-photo.loaded {
      opacity: 1;
    }
  `;

  document.head.appendChild(style);
}

function initPersonPhotoFallbacks(root) {
  root.querySelectorAll('.pav-photo').forEach(image => {
    image.addEventListener('load', () => {
      image.classList.add('loaded');
    });

    image.addEventListener('error', () => {
      image.remove();
    });

    if (image.complete) {
      if (image.naturalWidth > 0) {
        image.classList.add('loaded');
      } else {
        image.remove();
      }
    }
  });
}

(async () => {
  const id = Number(new URLSearchParams(location.search).get('id'));
  const box = $('#content');

  try {
    await APP_READY;

    const d = await loadMovies();

    ALL = [...d.catalog, ...d.featured];

    const m = await loadMovie(id);

    document.title = `${m.title} — Кадр`;

    await loadPeoplePhotos();

    initPersonPhotoStyles();

    const mine = Number(getRT()[m.id] || 0);
    const dirs = m.directors || [];
    const cast = m.cast || [];

    const similar = ALL.filter(x => x.id !== m.id)
      .map(x => ({
        x,
        s: x.genres.filter(g => m.genres.includes(g)).length
          + x.tags.filter(t => m.tags.includes(t)).length * 2
      }))
      .filter(o => o.s > 0)
      .sort((a, b) => b.s - a.s)
      .slice(0, 6)
      .map(o => o.x);

    const more = dirs.length
      ? []
      : [];

    const facts = [
      ['Страна', m.country],
      ['Студия', m.studio],
      ['Режиссёр', dirs.map(personName).join(', ')],
      ['Жанры', m.genres.join(', ')],
      ['Премьера', m.premiere],
      ['Длительность', m.duration],
      ['Бюджет', m.budget],
      ['Возраст', m.age]
    ];

    box.innerHTML = `
      <div class="mv">
        <div class="mv-bg" style="--art:${art(m.title)}"></div>

        <div class="mv-main">
          <div class="card" style="--art:${art(m.title)}">
            ${img(m)}
            <b>${m.poster ? '' : esc(m.title)}</b>
          </div>

          <div>
            <p class="meta">${esc(m.type)}, ${m.year}</p>

            <h1>${esc(m.title)}</h1>

            <p class="meta">Рейтинг ${m.rating ?? '—'}</p>

            <div class="tags">
              ${m.genres.map(g => `
                <span>${esc(g)}</span>
              `).join('')}

              ${m.tags.map(t => `
                <a href="catalog.html?tag=${encodeURIComponent(t)}">
                  ${esc(t)}
                </a>
              `).join('')}
            </div>

            <p class="meta">Ваша оценка</p>

            <div class="stars" data-id="${m.id}">
              ${Array.from(
                { length: 10 },
                (_, i) => i + 1
              ).map(n => `
                <button
                  type="button"
                  data-v="${n}"
                  class="${n <= mine ? 'on' : ''}"
                  aria-label="Оценка ${n} из 10"
                >
                  ★
                </button>
              `).join('')}
            </div>

            <button
              class="btn"
              type="button"
              id="wl"
              data-id="${m.id}"
            >
              ${inWL(m.id) ? 'В списке' : 'Смотреть позже'}
            </button>
          </div>
        </div>

        <div class="tabs">
          <div class="tabbar" role="tablist">
            <button
              class="on"
              role="tab"
              type="button"
              data-tab="info"
              aria-selected="true"
            >
              Информация
            </button>

            <button
              role="tab"
              type="button"
              data-tab="cast"
              aria-selected="false"
            >
              Актёры
            </button>

            <button
              role="tab"
              type="button"
              data-tab="dirs"
              aria-selected="false"
            >
              Режиссёры
            </button>
          </div>

          <div class="panel" data-panel="info">
            <div>
              <h2>О фильме</h2>

              <p class="ov">
                ${esc(m.overview || 'Описание пока отсутствует.')}
              </p>
            </div>

            <dl class="facts">
              ${facts.map(([k, v]) => `
                <dt>${esc(k)}</dt>
                <dd>${esc(v || '—')}</dd>
              `).join('')}
            </dl>
          </div>

          <div class="panel one" data-panel="cast" hidden>
            ${people(cast)}
          </div>

          <div class="panel one" data-panel="dirs" hidden>
            ${people(dirs)}

            ${more.length ? `
              <h3 class="sub">Ещё в каталоге</h3>

              <div class="grid">
                ${more.map(cardHTML).join('')}
              </div>
            ` : ''}
          </div>
        </div>
      </div>

      ${similar.length ? `
        <div class="sechead">
          <h2>Похожее</h2>
        </div>

        <div class="grid">
          ${similar.map(cardHTML).join('')}
        </div>
      ` : ''}
    `;

    initPersonPhotoFallbacks(box);

    startTheme(m, box.querySelector('.mv'));

  } catch (error) {
    box.innerHTML = `
      <p class="lead">${esc(error.message)}</p>
      <a class="btn" href="catalog.html">В каталог</a>
    `;
  }
})();

$('#content').addEventListener('click', e => {
  const button = e.target.closest('[data-tab]');

  if (button) {
    document.querySelectorAll('[data-tab]').forEach(x => {
      const selected = x === button;

      x.classList.toggle('on', selected);
      x.setAttribute('aria-selected', String(selected));
    });

    document.querySelectorAll('[data-panel]').forEach(panel => {
      panel.hidden = panel.dataset.panel !== button.dataset.tab;
    });

    return;
  }

  const card = e.target.closest('.grid .card[data-id]');

  if (card) {
    openMovie(card.dataset.id);
  }
});