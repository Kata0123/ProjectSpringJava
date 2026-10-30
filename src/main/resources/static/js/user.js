
const page = document.body.dataset.page;

async function render() {
  const box = $('#content');
  const u = getUser();

  if (!u) {
    box.innerHTML = '<p class="lead">Войдите, чтобы увидеть эту страницу.</p><button class="btn" type="button" id="pLogin">Войти</button>';
    return;
  }

  try {
    const d = await loadMovies();
    ALL = [...d.catalog,...d.featured];

    const rt = getRT();
    const wl = getWL();

    const rated = ALL.filter(m=>rt[m.id]);
    const saved = ALL.filter(m=>wl.includes(m.id));

    if (page === 'profile') {
      const avg = rated.length
        ? (rated.reduce((s,m)=>s+Number(rt[m.id]),0)/rated.length).toFixed(1)
        : '—';

      box.innerHTML = `<div class="pro">
        <span class="ava">${USER_ICON}</span>
        <form class="namef" id="nameForm">
          <input name="name" value="${esc(u.name)}" required minlength="2" maxlength="80" aria-label="Имя">
          <button class="btn">Сохранить</button>
        </form>
      </div>
      <div class="stats">
        <div class="stat"><b>${saved.length}</b>в списке</div>
        <div class="stat"><b>${rated.length}</b>оценок</div>
        <div class="stat"><b>${avg}</b>средняя оценка</div>
      </div>
      <div class="sechead">
        <h2>Мой список</h2>
        <a class="chip" href="catalog.html?view=watchlist">Открыть весь</a>
      </div>
      <div class="grid">
        ${saved.slice(0,6).map(cardHTML).join('') || '<p class="empty">Список пуст.</p>'}
      </div>`;
    } else {
      box.innerHTML = rated.length
        ? `<div class="grid">${rated.map(m=>`
            <div>${cardHTML(m)}
              <p class="mine">Ваша оценка: ${rt[m.id]} из 10</p>
            </div>`).join('')}</div>`
        : '<p class="empty">Вы ещё ничего не оценили. Откройте фильм и поставьте звёзды.</p>';
    }
  } catch (error) {
    box.innerHTML = `<p class="empty">${esc(error.message)}</p>`;
  }
}

APP_READY.then(render).catch(error=>{
  $('#content').textContent = error.message;
});

document.addEventListener('kadr:session',render);
document.addEventListener('kadr:ratings',render);
document.addEventListener('kadr:watchlist',render);

$('#content').addEventListener('click',e=>{
  if (e.target.closest('#pLogin')) {
    $('#loginModal').showModal();
    return;
  }

  const card = e.target.closest('.card[data-id]');
  if (card) openMovie(card.dataset.id);
});

$('#content').addEventListener('submit',async e=>{
  if (e.target.id !== 'nameForm') return;

  e.preventDefault();

  const name = new FormData(e.target).get('name').trim();

  try {
    await api('/api/me',{
      method:'PATCH',
      body:JSON.stringify({name})
    });
    await refreshSession();
  } catch (error) {
    alert(error.message);
  }
});
