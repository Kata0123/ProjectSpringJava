
(() => {
  'use strict';

  const TMDB_HOST = 'image.tmdb.org';
  const LOCAL_PATH = '/api/posters/';
  const POSTER_NAME = /^[a-zA-Z0-9_-]+\.(jpg|jpeg|png|webp)$/i;

  function posterLocalUrl(url) {
    if (!url) return '';

    const source = String(url).trim();

    if (!source) return '';

    if (source.startsWith(LOCAL_PATH)) {
      return source;
    }

    try {
      const parsed = new URL(source, window.location.origin);

      if (
        parsed.protocol !== 'https:' ||
        parsed.hostname !== TMDB_HOST
      ) {
        return source;
      }

      const parts = parsed.pathname.split('/').filter(Boolean);

      if (
        parts.length !== 3 ||
        parts[0] !== 't' ||
        parts[1] !== 'p'
      ) {
        return source;
      }

      const fileName = parts[2];

      if (!POSTER_NAME.test(fileName)) {
        return source;
      }

      return LOCAL_PATH + fileName;
    } catch {
      return source;
    }
  }

  function updateImage(image) {
    if (!(image instanceof HTMLImageElement)) {
      return;
    }

    const source = image.getAttribute('src');

    if (source) {
      const local = posterLocalUrl(source);

      if (local !== source) {
        image.setAttribute('src', local);
      }
    }

    const lazySource = image.getAttribute('data-src');

    if (lazySource) {
      const local = posterLocalUrl(lazySource);

      if (local !== lazySource) {
        image.setAttribute('data-src', local);
      }
    }
  }

  function updateImages(root = document) {
    if (root instanceof HTMLImageElement) {
      updateImage(root);
      return;
    }

    if (!root.querySelectorAll) {
      return;
    }

    root.querySelectorAll('img').forEach(updateImage);
  }

  function start() {
    updateImages();

    const observer = new MutationObserver(mutations => {
      for (const mutation of mutations) {
        if (mutation.type === 'attributes') {
          updateImage(mutation.target);
          continue;
        }

        for (const node of mutation.addedNodes) {
          if (node.nodeType === Node.ELEMENT_NODE) {
            updateImages(node);
          }
        }
      }
    });

    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['src', 'data-src']
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, {
      once: true
    });
  } else {
    start();
  }

  window.posterLocalUrl = posterLocalUrl;
})();
