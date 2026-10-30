
async function loadMovies() {
  return await api('/api/catalog');
}

async function loadRandomMovies(n = 50) {
  return await api('/api/media/random?limit=' + encodeURIComponent(n));
}

async function loadMovie(id) {
  return await api('/api/media/' + encodeURIComponent(id));
}
