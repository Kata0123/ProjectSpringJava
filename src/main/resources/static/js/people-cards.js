(function () {
  'use strict';

  const PEOPLE_URL = '/api/people/photos';

  let photoMap = new Map();
  let photosLoaded = false;
  let updateScheduled = false;

  function normalizeName(value) {
    return String(value ?? '')
      .normalize('NFKC')
      .toLocaleLowerCase('ru-RU')
      .replace(/ё/g, 'е')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function validPhotoUrl(value) {
    const url = String(value ?? '').trim();

    return /^\/img\/people\/[a-zA-Z0-9._-]+\.(jpg|jpeg|png|webp)$/i.test(url)
      ? url
      : '';
  }

  function getPersonName(card) {
    const copy = card.cloneNode(true);

    copy.querySelectorAll('.pav').forEach(node => {
      node.remove();
    });

    return copy.textContent
      .replace(/\s+/g, ' ')
      .trim();
  }

  function getInitials(name) {
    const words = String(name)
      .trim()
      .split(/\s+/)
      .filter(Boolean);

    if (!words.length) {
      return '?';
    }

    return words
      .slice(0, 2)
      .map(word => word.charAt(0).toUpperCase())
      .join('');
  }

  function addPersonToMap(person) {
    const url = validPhotoUrl(
      person.photoUrl ?? person.photo_url
    );

    if (!url) {
      return;
    }

    const names = [
      person.name,
      person.fullName,
      person.full_name,
      person.originalName,
      person.original_name
    ];

    for (const name of names) {
      const key = normalizeName(name);

      if (key) {
        photoMap.set(key, url);
      }
    }
  }

  async function loadPeoplePhotos() {
    try {
      const response = await fetch(PEOPLE_URL, {
        credentials: 'same-origin',
        headers: {
          Accept: 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error(
          `Не удалось получить фотографии: HTTP ${response.status}`
        );
      }

      const people = await response.json();

      if (!Array.isArray(people)) {
        throw new Error('Неверный ответ сервера с фотографиями');
      }

      const newMap = new Map();

      photoMap = newMap;

      for (const person of people) {
        addPersonToMap(person);
      }

      photosLoaded = true;

      updateAllPeopleCards();
    } catch (error) {
      console.warn('Кадр: фотографии людей недоступны:', error.message);
    }
  }

  function updatePersonCard(card) {
    const avatar = card.querySelector('.pav');

    if (!avatar) {
      return;
    }

    const name = getPersonName(card);

    if (!name) {
      return;
    }

    const key = normalizeName(name);
    const url = photoMap.get(key);

    if (!url) {
      return;
    }

    if (
      avatar.dataset.peoplePhoto === url &&
      avatar.querySelector('img')
    ) {
      return;
    }

    const existingLetters = avatar.textContent.trim();
    const letters = existingLetters || getInitials(name);

    avatar.dataset.peoplePhoto = url;
    avatar.classList.add('pav-with-photo');

    avatar.replaceChildren();

    const fallback = document.createElement('span');

    fallback.className = 'pav-letters';
    fallback.textContent = letters;

    const image = document.createElement('img');

    image.className = 'pav-photo';
    image.src = url;
    image.alt = name;
    image.loading = 'lazy';
    image.decoding = 'async';

    image.addEventListener('load', () => {
      image.classList.add('pav-photo-loaded');
    });

    image.addEventListener('error', () => {
      image.remove();
      avatar.classList.remove('pav-with-photo');
      avatar.dataset.peoplePhoto = '';
    }, {
      once: true
    });

    avatar.append(fallback, image);
  }

  function updateAllPeopleCards() {
    if (!photosLoaded) {
      return;
    }

    document.querySelectorAll('.people .person').forEach(card => {
      updatePersonCard(card);
    });
  }

  function schedulePeopleUpdate() {
    if (!photosLoaded || updateScheduled) {
      return;
    }

    updateScheduled = true;

    requestAnimationFrame(() => {
      updateScheduled = false;
      updateAllPeopleCards();
    });
  }

  function installStyles() {
    if (document.querySelector('#kadr-people-photo-styles')) {
      return;
    }

    const style = document.createElement('style');

    style.id = 'kadr-people-photo-styles';

    style.textContent = `
      .people .person .pav {
        position: relative;
        width: 64px;
        height: 64px;
        min-width: 64px;
        min-height: 64px;
        border-radius: 50%;
        overflow: hidden;
        display: grid;
        place-items: center;
        isolation: isolate;
      }

      .people .person .pav .pav-letters {
        display: grid;
        place-items: center;
        width: 100%;
        height: 100%;
        color: inherit;
        font: inherit;
      }

      .people .person .pav .pav-photo {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        object-fit: cover;
        object-position: center 20%;
        border-radius: 50%;
        display: block;
        opacity: 0;
        transition: opacity 0.2s ease;
        z-index: 1;
      }

      .people .person .pav .pav-photo-loaded {
        opacity: 1;
      }

      .people .person .pav-with-photo {
        border: 2px solid rgba(255, 190, 65, 0.35);
      }
    `;

    document.head.appendChild(style);
  }

  function startPeoplePhotos() {
    installStyles();
    loadPeoplePhotos();

    const content = document.querySelector('#content');

    if (content) {
      const observer = new MutationObserver(() => {
        schedulePeopleUpdate();
      });

      observer.observe(content, {
        childList: true,
        subtree: true
      });
    }

    document.addEventListener('click', event => {
      if (
        event.target.closest('[role="tab"]') ||
        event.target.closest('.tabbar button')
      ) {
        schedulePeopleUpdate();
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener(
      'DOMContentLoaded',
      startPeoplePhotos,
      { once: true }
    );
  } else {
    startPeoplePhotos();
  }
})();