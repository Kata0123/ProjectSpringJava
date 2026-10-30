
let adminTitles=[];
let entityEditingKey=null;
let toastTimer=null;

const ENTITY_DEFINITIONS={
  media:{
    label:'Основные записи каталога',
    keys:['id'],
    fields:['title','original_title','media_type','release_year','external_rating','overview','age_rating','premiere_date','theme_key','theme_glow','published']
  },
  films:{
    label:'Данные фильмов',
    keys:['media_id'],
    fields:['media_id','runtime_minutes','budget_usd']
  },
  series:{
    label:'Данные сериалов',
    keys:['media_id'],
    fields:['media_id','seasons_count','episodes_count','episode_runtime_minutes','end_year']
  },
  genres:{
    label:'Жанры',
    keys:['id'],
    fields:['name']
  },
  media_genres:{
    label:'Жанры фильмов',
    keys:['media_id','genre_id'],
    fields:['media_id','genre_id']
  },
  tags:{
    label:'Теги',
    keys:['id'],
    fields:['name']
  },
  media_tags:{
    label:'Теги фильмов',
    keys:['media_id','tag_id'],
    fields:['media_id','tag_id']
  },
  people:{
    label:'Актёры и режиссёры',
    keys:['id'],
    fields:['full_name','original_name','photo_url']
  },
  credit_roles:{
    label:'Типы участия',
    keys:['id'],
    fields:['code','name']
  },
  media_credits:{
    label:'Актёрский состав и режиссёры',
    keys:['id'],
    fields:['media_id','person_id','role_id','character_name','billing_order']
  },
  countries:{
    label:'Страны',
    keys:['id'],
    fields:['name']
  },
  media_countries:{
    label:'Страны производства',
    keys:['media_id','country_id'],
    fields:['media_id','country_id']
  },
  studios:{
    label:'Студии',
    keys:['id'],
    fields:['name']
  },
  media_studios:{
    label:'Студии фильмов',
    keys:['media_id','studio_id'],
    fields:['media_id','studio_id']
  },
  featured_media:{
    label:'Выбор редакции',
    keys:['media_id'],
    fields:['media_id','position','active']
  },
  media_posters:{
    label:'Постеры',
    keys:['id'],
    fields:['media_id','provider','poster_url','local_path','language_code','is_primary']
  },
  media_external_ids:{
    label:'Внешние API и идентификаторы',
    keys:['media_id','provider'],
    fields:['media_id','provider','external_id']
  },
  user_ratings:{
    label:'Оценки пользователей',
    keys:['user_id','media_id'],
    fields:['user_id','media_id','rating']
  },
  watchlist:{
    label:'Watchlist пользователей',
    keys:['user_id','media_id'],
    fields:['user_id','media_id']
  }
};

const FIELD_LABELS={
  id:'ID',
  media_id:'ID фильма или сериала',
  user_id:'ID пользователя',
  person_id:'ID человека',
  role_id:'ID роли',
  genre_id:'ID жанра',
  tag_id:'ID тега',
  country_id:'ID страны',
  studio_id:'ID студии',
  title:'Название',
  original_title:'Оригинальное название',
  media_type:'Тип',
  release_year:'Год выхода',
  external_rating:'Рейтинг',
  overview:'Описание',
  age_rating:'Возраст',
  premiere_date:'Дата премьеры',
  theme_key:'Тема',
  theme_glow:'Цвет',
  published:'Опубликовано',
  runtime_minutes:'Длительность, минуты',
  budget_usd:'Бюджет, USD',
  seasons_count:'Количество сезонов',
  episodes_count:'Количество серий',
  episode_runtime_minutes:'Длительность серии',
  end_year:'Год окончания',
  name:'Название',
  full_name:'Полное имя',
  original_name:'Оригинальное имя',
  photo_url:'Фотография',
  code:'Код',
  character_name:'Имя персонажа',
  billing_order:'Порядок отображения',
  position:'Позиция',
  active:'Активно',
  provider:'Источник',
  poster_url:'Ссылка на постер',
  local_path:'Локальный путь',
  language_code:'Язык',
  is_primary:'Основной постер',
  external_id:'Идентификатор во внешнем API',
  rating:'Оценка'
};

const NUMBER_FIELDS=new Set([
  'id','media_id','user_id','person_id','role_id',
  'genre_id','tag_id','country_id','studio_id',
  'release_year','external_rating','runtime_minutes',
  'budget_usd','seasons_count','episodes_count',
  'episode_runtime_minutes','end_year','billing_order',
  'position','rating'
]);

const BOOLEAN_FIELDS=new Set([
  'published','active','is_primary'
]);

const DATE_FIELDS=new Set([
  'premiere_date'
]);

const REQUIRED_FIELDS={
  media:['title','media_type','release_year'],
  films:['media_id'],
  series:['media_id'],
  genres:['name'],
  media_genres:['media_id','genre_id'],
  tags:['name'],
  media_tags:['media_id','tag_id'],
  people:['full_name'],
  credit_roles:['code','name'],
  media_credits:['media_id','person_id','role_id'],
  countries:['name'],
  media_countries:['media_id','country_id'],
  studios:['name'],
  media_studios:['media_id','studio_id'],
  featured_media:['media_id','position'],
  media_posters:['media_id','provider'],
  media_external_ids:['media_id','provider','external_id'],
  user_ratings:['user_id','media_id','rating'],
  watchlist:['user_id','media_id']
};

function toast(message,error=false) {
  const box=$('#adminToast');

  box.textContent=message;
  box.classList.toggle('error',error);
  box.hidden=false;

  clearTimeout(toastTimer);
  toastTimer=setTimeout(()=>box.hidden=true,5000);
}

function splitNames(value) {
  return [...new Set(
    String(value || '')
      .split(/[;\n]/)
      .map(s=>s.trim())
      .filter(Boolean)
  )];
}

function textValue(form,name) {
  return form.elements.namedItem(name).value.trim();
}

function optionalValue(form,name) {
  const value=textValue(form,name);
  return value===''?null:value;
}

function optionalNumber(form,name) {
  const value=textValue(form,name);
  return value===''?null:Number(value);
}

function titleData() {
  const form=$('#titleForm');

  return {
    title:textValue(form,'title'),
    originalTitle:optionalValue(form,'originalTitle'),
    mediaType:textValue(form,'mediaType'),
    releaseYear:Number(textValue(form,'releaseYear')),
    externalRating:optionalNumber(form,'externalRating'),
    overview:optionalValue(form,'overview'),
    ageRating:optionalValue(form,'ageRating'),
    premiereDate:optionalValue(form,'premiereDate'),
    themeKey:optionalValue(form,'themeKey'),
    themeGlow:optionalValue(form,'themeGlow'),
    runtimeMinutes:optionalNumber(form,'runtimeMinutes'),
    budgetUsd:optionalNumber(form,'budgetUsd'),
    seasonsCount:optionalNumber(form,'seasonsCount'),
    episodesCount:optionalNumber(form,'episodesCount'),
    episodeRuntimeMinutes:optionalNumber(form,'episodeRuntimeMinutes'),
    endYear:optionalNumber(form,'endYear'),
    genres:splitNames(textValue(form,'genres')),
    tags:splitNames(textValue(form,'tags')),
    countries:splitNames(textValue(form,'countries')),
    studios:splitNames(textValue(form,'studios')),
    actors:splitNames(textValue(form,'actors')),
    directors:splitNames(textValue(form,'directors')),
    posterUrl:optionalValue(form,'posterUrl'),
    featured:form.elements.namedItem('featured').checked
  };
}

function updateTitleType() {
  const series=$('#titleMediaType').value==='SERIES';

  document.querySelectorAll('.for-movie').forEach(el=>{
    el.hidden=series;
  });

  document.querySelectorAll('.for-series').forEach(el=>{
    el.hidden=!series;
  });
}

function resetTitleForm() {
  const form=$('#titleForm');

  form.reset();
  $('#titleId').value='';
  $('#titleFormHeading').textContent='Добавить фильм или сериал';
  $('#saveTitle').textContent='Сохранить';

  updateTitleType();
}

function fillTitleForm(m) {
  const form=$('#titleForm');

  form.reset();
  $('#titleId').value=m.id;

  const fields=[
    'title','originalTitle','mediaType','releaseYear',
    'externalRating','overview','ageRating','premiereDate',
    'themeKey','themeGlow','runtimeMinutes','budgetUsd',
    'seasonsCount','episodesCount','episodeRuntimeMinutes',
    'endYear','posterUrl'
  ];

  for(const field of fields) {
    const input=form.elements.namedItem(field);
    if(input) input.value=m[field] ?? '';
  }

  const lists=['genres','tags','countries','studios','actors','directors'];

  for(const field of lists) {
    const input=form.elements.namedItem(field);
    input.value=(m[field] || []).join('; ');
  }

  form.elements.namedItem('featured').checked=!!m.featured;

  $('#titleFormHeading').textContent='Редактировать: '+m.title;
  $('#saveTitle').textContent='Сохранить изменения';

  updateTitleType();

  $('#titleForm').scrollIntoView({
    behavior:'smooth',
    block:'start'
  });
}

async function loadAdminTitles() {
  const data=await api('/api/catalog');

  adminTitles=[...data.catalog,...data.featured]
    .sort((a,b)=>a.title.localeCompare(b.title,'ru'));

  $('#statTotal').textContent=adminTitles.length;
  $('#statMovies').textContent=adminTitles.filter(m=>m.type==='Фильм').length;
  $('#statSeries').textContent=adminTitles.filter(m=>m.type==='Сериал').length;
  $('#statFeatured').textContent=data.featured.length;

  renderTitleList();
}

function renderTitleList() {
  const search=$('#adminTitleSearch').value.trim().toLowerCase();

  const list=adminTitles.filter(m=>
    m.title.toLowerCase().includes(search)
  );

  $('#adminTitleList').innerHTML=list.length
    ?list.map(m=>`
      <div class="admin-item">
        <div class="admin-item-poster" style="--art:${art(m.title)}">
          ${img(m)}
        </div>
        <div class="admin-item-info">
          <h3>${esc(m.title)}</h3>
          <p>${esc(m.type)} · ${m.year} · ★ ${m.rating ?? '—'} · ID ${m.id}</p>
        </div>
        <div class="admin-item-actions">
          <button type="button" class="admin-small-btn" data-title-edit="${m.id}">Изменить</button>
          <button type="button" class="admin-small-btn danger" data-title-delete="${m.id}">Удалить</button>
        </div>
      </div>
    `).join('')
    :'<p class="admin-empty">По вашему запросу ничего не найдено.</p>';
}

$('#titleMediaType').addEventListener('change',updateTitleType);
$('#resetTitle').addEventListener('click',resetTitleForm);
$('#adminTitleSearch').addEventListener('input',renderTitleList);

$('#titleForm').addEventListener('submit',async e=>{
  e.preventDefault();

  const id=$('#titleId').value;
  const button=$('#saveTitle');

  button.disabled=true;

  try {
    const result=await api(
      id?'/api/admin/titles/'+id:'/api/admin/titles',
      {
        method:id?'PUT':'POST',
        body:JSON.stringify(titleData())
      }
    );

    toast(id?'Информация обновлена':'Новый фильм или сериал добавлен');
    resetTitleForm();
    await loadAdminTitles();
  } catch(error) {
    toast(error.message,true);
  } finally {
    button.disabled=false;
  }
});

$('#adminTitleList').addEventListener('click',async e=>{
  const edit=e.target.closest('[data-title-edit]');
  const remove=e.target.closest('[data-title-delete]');

  if(edit) {
    try {
      const id=Number(edit.dataset.titleEdit);
      const data=await api('/api/admin/titles/'+id);
      fillTitleForm(data);
    } catch(error) {
      toast(error.message,true);
    }
    return;
  }

  if(remove) {
    const id=Number(remove.dataset.titleDelete);
    const title=adminTitles.find(m=>m.id===id)?.title || 'эту запись';

    if(!confirm(`Удалить «${title}» из каталога? Это действие нельзя отменить.`)) {
      return;
    }

    try {
      await api('/api/admin/titles/'+id,{method:'DELETE'});
      toast('Запись удалена');
      resetTitleForm();
      await loadAdminTitles();
    } catch(error) {
      toast(error.message,true);
    }
  }
});

function fieldInput(field,table,editing=false) {
  const label=FIELD_LABELS[field] || field;
  const required=(REQUIRED_FIELDS[table] || []).includes(field);
  const isKey=ENTITY_DEFINITIONS[table].keys.includes(field);
  const disabled=editing && isKey;

  if(BOOLEAN_FIELDS.has(field)) {
    return `<label class="admin-checkbox">
      <input type="checkbox" name="${field}" ${disabled?'disabled':''}
        ${!editing?'checked':''}>
      <span>${esc(label)}</span>
    </label>`;
  }

  if(field==='media_type') {
    return `<label>${esc(label)}
      <select name="${field}" ${disabled?'disabled':''} ${required?'required':''}>
        <option value="MOVIE">MOVIE — фильм</option>
        <option value="SERIES">SERIES — сериал</option>
      </select>
    </label>`;
  }

  if(field==='code' && table==='credit_roles') {
    return `<label>${esc(label)}
      <select name="code" ${disabled?'disabled':''} required>
        <option value="ACTOR">ACTOR</option>
        <option value="DIRECTOR">DIRECTOR</option>
      </select>
    </label>`;
  }

  if(field==='overview') {
    return `<label class="admin-wide">${esc(label)}
      <textarea name="${field}" rows="4" ${disabled?'disabled':''}></textarea>
    </label>`;
  }

  let type='text';

  if(NUMBER_FIELDS.has(field)) type='number';
  if(DATE_FIELDS.has(field)) type='date';

  const step=field==='external_rating'?'step="0.1"':'';
  const min=NUMBER_FIELDS.has(field)?'min="0"':'';

  return `<label>${esc(label)}
    <input name="${field}" type="${type}" ${step} ${min}
      ${required?'required':''} ${disabled?'disabled':''}>
  </label>`;
}

function selectedTable() {
  return $('#entitySelect').value;
}

function renderEntityForm() {
  const table=selectedTable();
  const definition=ENTITY_DEFINITIONS[table];

  $('#entityFormHeading').textContent=entityEditingKey
    ?'Изменение записи'
    :'Новая запись';

  $('#entityFields').innerHTML=definition.fields
    .map(field=>fieldInput(field,table,!!entityEditingKey))
    .join('');
}

function entityKey(table,row) {
  return ENTITY_DEFINITIONS[table].keys
    .map(key=>String(row[key]))
    .join(':');
}

function displayCell(value) {
  if(value===null || value===undefined) return '—';
  if(typeof value==='object') return JSON.stringify(value);
  return String(value);
}

async function loadEntityRows() {
  const table=selectedTable();
  const def=ENTITY_DEFINITIONS[table];

  const rows=await api(
    '/api/admin/'+encodeURIComponent(table)+'?limit=100&offset=0'
  );

  const columns=[
    ...def.keys,
    ...def.fields.filter(f=>!def.keys.includes(f))
  ].slice(0,5);

  $('#entityHead').innerHTML=`<tr>
    ${columns.map(c=>`<th>${esc(FIELD_LABELS[c] || c)}</th>`).join('')}
    <th>Действия</th>
  </tr>`;

  $('#entityRows').innerHTML=rows.length
    ?rows.map(row=>{
      const key=entityKey(table,row);

      return `<tr>
        ${columns.map(c=>`<td>${esc(displayCell(row[c]))}</td>`).join('')}
        <td>
          <button type="button" class="admin-small-btn" data-entity-edit="${esc(key)}">Изменить</button>
          <button type="button" class="admin-small-btn danger" data-entity-delete="${esc(key)}">Удалить</button>
        </td>
      </tr>`;
    }).join('')
    :'<tr><td colspan="6">Записей пока нет.</td></tr>';
}

function resetEntityForm() {
  entityEditingKey=null;
  renderEntityForm();
  $('#entityForm').reset();
}

function entityPayload() {
  const form=$('#entityForm');
  const table=selectedTable();
  const fields=ENTITY_DEFINITIONS[table].fields;
  const keys=ENTITY_DEFINITIONS[table].keys;
  const result={};

  for(const field of fields) {
    if(entityEditingKey && keys.includes(field)) continue;

    const input=form.elements.namedItem(field);
    if(!input) continue;

    if(BOOLEAN_FIELDS.has(field)) {
      result[field]=input.checked;
      continue;
    }

    const value=input.value.trim();

    if(value==='') continue;

    result[field]=NUMBER_FIELDS.has(field)
      ?Number(value)
      :value;
  }

  return result;
}

async function editEntity(key) {
  const table=selectedTable();

  const row=await api(
    '/api/admin/'+encodeURIComponent(table)+'/'+encodeURIComponent(key)
  );

  entityEditingKey=key;
  renderEntityForm();

  for(const field of ENTITY_DEFINITIONS[table].fields) {
    const input=$('#entityForm').elements.namedItem(field);
    if(!input) continue;

    if(BOOLEAN_FIELDS.has(field)) {
      input.checked=!!row[field];
    } else {
      input.value=row[field] ?? '';
    }
  }

  $('#entityForm').scrollIntoView({
    behavior:'smooth',
    block:'start'
  });
}

$('#entitySelect').addEventListener('change',async()=>{
  resetEntityForm();

  try {
    await loadEntityRows();
  } catch(error) {
    toast(error.message,true);
  }
});

$('#entityReset').addEventListener('click',resetEntityForm);

$('#entityForm').addEventListener('submit',async e=>{
  e.preventDefault();

  const table=selectedTable();
  const data=entityPayload();
  const key=entityEditingKey;

  try {
    await api(
      '/api/admin/'+encodeURIComponent(table)
        +(key?'/'+encodeURIComponent(key):''),
      {
        method:key?'PUT':'POST',
        body:JSON.stringify(data)
      }
    );

    toast(key?'Запись обновлена':'Запись добавлена');
    resetEntityForm();
    await loadEntityRows();
    await loadAdminTitles();
  } catch(error) {
    toast(error.message,true);
  }
});

$('#entityRows').addEventListener('click',async e=>{
  const edit=e.target.closest('[data-entity-edit]');
  const remove=e.target.closest('[data-entity-delete]');

  if(edit) {
    try {
      await editEntity(edit.dataset.entityEdit);
    } catch(error) {
      toast(error.message,true);
    }
    return;
  }

  if(remove) {
    if(!confirm('Удалить эту запись из базы данных?')) return;

    try {
      const table=selectedTable();

      await api(
        '/api/admin/'+encodeURIComponent(table)
          +'/'+encodeURIComponent(remove.dataset.entityDelete),
        {method:'DELETE'}
      );

      toast('Запись удалена');
      resetEntityForm();
      await loadEntityRows();
      await loadAdminTitles();
    } catch(error) {
      toast(error.message,true);
    }
  }
});

async function loadAdminUsers() {
  const users=await api('/api/admin/users');
  const me=getUser();

  $('#adminUsersList').innerHTML=users.map(u=>{
    const own=Number(u.id)===Number(me.id);
    const date=u.created_at
      ?new Date(u.created_at).toLocaleDateString('ru-RU')
      :'—';

    const nextRole=u.role==='ADMIN'?'USER':'ADMIN';

    return `<tr>
      <td>${u.id}</td>
      <td>${esc(u.username)}</td>
      <td><span class="admin-user-role ${u.role==='ADMIN'?'admin':''}">${esc(u.role)}</span></td>
      <td>${esc(date)}</td>
      <td>
        ${own
          ?'<span class="admin-hint">Ваш аккаунт</span>'
          :`<button type="button" class="admin-small-btn" data-user-role="${u.id}" data-next-role="${nextRole}">${nextRole==='ADMIN'?'Сделать ADMIN':'Сделать USER'}</button>
             <button type="button" class="admin-small-btn danger" data-user-delete="${u.id}">Удалить</button>`
        }
      </td>
    </tr>`;
  }).join('');
}

$('#createUserForm').addEventListener('submit',async e=>{
  e.preventDefault();

  const form=e.target;
  const data={
    username:form.elements.namedItem('username').value.trim(),
    password:form.elements.namedItem('password').value,
    role:form.elements.namedItem('role').value
  };

  try {
    await api('/api/admin/users',{
      method:'POST',
      body:JSON.stringify(data)
    });

    form.reset();
    toast('Пользователь создан');
    await loadAdminUsers();
  } catch(error) {
    toast(error.message,true);
  }
});

$('#adminUsersList').addEventListener('click',async e=>{
  const change=e.target.closest('[data-user-role]');
  const remove=e.target.closest('[data-user-delete]');

  if(change) {
    const id=change.dataset.userRole;
    const role=change.dataset.nextRole;

    if(!confirm(`Установить пользователю роль ${role}?`)) return;

    try {
      await api('/api/admin/users/'+id+'/role',{
        method:'PATCH',
        body:JSON.stringify({role})
      });

      toast('Роль изменена');
      await loadAdminUsers();
    } catch(error) {
      toast(error.message,true);
    }
  }

  if(remove) {
    const id=remove.dataset.userDelete;

    if(!confirm('Удалить пользователя вместе с его оценками и watchlist?')) {
      return;
    }

    try {
      await api('/api/admin/users/'+id,{method:'DELETE'});
      toast('Пользователь удалён');
      await loadAdminUsers();
    } catch(error) {
      toast(error.message,true);
    }
  }
});

document.querySelectorAll('.admin-tab').forEach(button=>{
  button.addEventListener('click',async()=>{
    const section=button.dataset.section;

    document.querySelectorAll('.admin-tab').forEach(b=>{
      b.classList.toggle('on',b===button);
    });

    document.querySelectorAll('.admin-section').forEach(s=>{
      s.hidden=s.id!=='section-'+section;
    });

    try {
      if(section==='tables') await loadEntityRows();
      if(section==='users') await loadAdminUsers();
    } catch(error) {
      toast(error.message,true);
    }
  });
});

async function initAdmin() {
  await APP_READY;

  if(!isAdmin()) {
    location.href='access-denied.html';
    return;
  }

  const select=$('#entitySelect');

  select.innerHTML=Object.entries(ENTITY_DEFINITIONS)
    .map(([key,value])=>
      `<option value="${key}">${esc(value.label)}</option>`
    ).join('');

  updateTitleType();
  renderEntityForm();

  await loadAdminTitles();
}

initAdmin().catch(error=>{
  toast(error.message,true);
});

let peopleAvatarRunning = false;
let peopleAvatarStopRequested = false;
let peopleAvatarCursor = 0;
let peopleAvatarProcessed = 0;
let peopleAvatarSaved = 0;
let peopleAvatarSkipped = 0;

function createPeopleAvatarPanel() {
  if (document.querySelector('#peopleAvatarButton')) return;

  const panel = document.createElement('section');
  panel.className = 'admin-avatar-section';

  panel.innerHTML = `
    <div class="admin-avatar-info">
      <h2>Аватарки актёров и режиссёров</h2>
      <p>Загрузить фотографии людей из TMDB в локальную папку img/people. Уже сохранённые фотографии не изменяются.</p>
      <div id="peopleAvatarStatus" role="status" aria-live="polite">Нажми кнопку, чтобы начать загрузку.</div>
      <div class="admin-avatar-progress"><div id="peopleAvatarProgress"></div></div>
    </div>
    <div class="admin-avatar-actions">
      <button type="button" id="peopleAvatarButton" class="admin-avatar-button">Загрузить аватарки актёров и режиссёров</button>
      <button type="button" id="peopleAvatarStop" class="admin-avatar-stop" hidden>Остановить</button>
    </div>
  `;

  const style = document.createElement('style');

  style.textContent = `
    .admin-avatar-section {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 24px;
      margin: 24px 0;
      padding: 28px;
      background: #15161d;
      border: 1px solid #46402f;
      border-radius: 22px;
    }

    .admin-avatar-info {
      flex: 1 1 360px;
      min-width: 0;
    }

    .admin-avatar-info h2 {
      margin: 0 0 12px;
      color: #fff;
      font-size: 23px;
      font-weight: 800;
    }

    .admin-avatar-info p {
      max-width: 650px;
      margin: 0 0 18px;
      color: #a2abc0;
      font-size: 14px;
      line-height: 1.7;
    }

    #peopleAvatarStatus {
      color: #a2abc0;
      font-size: 14px;
      line-height: 1.6;
    }

    .admin-avatar-progress {
      width: 100%;
      max-width: 650px;
      height: 8px;
      margin-top: 14px;
      overflow: hidden;
      background: #292b35;
      border-radius: 50px;
    }

    #peopleAvatarProgress {
      width: 0;
      height: 100%;
      background: linear-gradient(90deg, #d78b19, #ffbe3b);
      border-radius: 50px;
      transition: width .25s;
    }

    .admin-avatar-actions {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
    }

    .admin-avatar-button {
      padding: 17px 22px;
      border: 0;
      border-radius: 12px;
      background: #ffbd3c;
      color: #17120a;
      font-size: 14px;
      font-weight: 800;
      cursor: pointer;
    }

    .admin-avatar-button:hover {
      background: #ffd06d;
    }

    .admin-avatar-button:disabled {
      opacity: .7;
      cursor: not-allowed;
    }

    .admin-avatar-stop {
      padding: 16px 22px;
      border: 1px solid #78483c;
      border-radius: 12px;
      background: #412822;
      color: #ffd5cc;
      font-weight: 700;
      cursor: pointer;
    }

    .admin-avatar-stop[hidden] {
      display: none;
    }
  `;

  document.head.append(style);

  const target =
    document.querySelector('#tmdbBatchButton')?.closest(
      'section, .admin-panel, .admin-card'
    ) ||
    document.querySelector('#section-titles') ||
    document.querySelector('.admin-section') ||
    document.querySelector('main');

  if (target) {
    target.insertAdjacentElement(
      target.id === 'section-titles' ? 'afterbegin' : 'afterend',
      panel
    );
  } else {
    document.body.append(panel);
  }

  panel.querySelector('#peopleAvatarButton')
    .addEventListener('click', peopleAvatarLoadAll);

  panel.querySelector('#peopleAvatarStop')
    .addEventListener('click', () => {
      peopleAvatarStopRequested = true;
      panel.querySelector('#peopleAvatarStop').disabled = true;
      peopleAvatarSetStatus(
        'Остановка после завершения текущей группы...'
      );
    });
}

function peopleAvatarSetStatus(message) {
  const status = document.querySelector('#peopleAvatarStatus');

  if (status) {
    status.textContent =
      `Обработано: ${peopleAvatarProcessed}. ` +
      `Сохранено: ${peopleAvatarSaved}. ` +
      `Пропущено: ${peopleAvatarSkipped}. ${message}`;
  }
}

async function peopleAvatarLoadAll() {
  if (peopleAvatarRunning) return;

  const button = document.querySelector('#peopleAvatarButton');
  const stop = document.querySelector('#peopleAvatarStop');
  const progress = document.querySelector('#peopleAvatarProgress');

  if (!button || !stop || !progress) return;

  if (peopleAvatarCursor === 0) {
    peopleAvatarProcessed = 0;
    peopleAvatarSaved = 0;
    peopleAvatarSkipped = 0;
  }

  peopleAvatarRunning = true;
  peopleAvatarStopRequested = false;

  button.disabled = true;
  button.textContent = 'Загружаю фотографии...';

  stop.disabled = false;
  stop.hidden = false;

  progress.style.width = '10%';
  peopleAvatarSetStatus('Загрузка началась.');

  let completed = false;

  try {
    await APP_READY;

    while (!peopleAvatarStopRequested) {
      const params = new URLSearchParams({
        afterId: String(peopleAvatarCursor),
        limit: '10'
      });

      const result = await api(
        '/api/admin/tmdb/people/autofill?' + params,
        {method: 'POST'}
      );

      const processed = Number(result.processed || 0);
      const next = Number(result.nextAfterId || 0);

      if (
        processed > 0 &&
        next <= peopleAvatarCursor
      ) {
        throw new Error(
          'Сервер не изменил позицию загрузки'
        );
      }

      peopleAvatarCursor = next;
      peopleAvatarProcessed += processed;
      peopleAvatarSaved += Number(result.saved || 0);
      peopleAvatarSkipped += Number(result.skipped || 0);

      progress.style.width =
        `${15 + (peopleAvatarProcessed % 16) * 5}%`;

      peopleAvatarSetStatus(
        'Проверяю следующие записи...'
      );

      if (!result.hasMore || processed === 0) {
        completed = true;
        peopleAvatarCursor = 0;

        progress.style.width = '100%';

        peopleAvatarSetStatus(
          'Готово. Все подходящие записи проверены.'
        );

        button.textContent = 'Проверить снова';

        if (
          typeof selectedTable === 'function' &&
          selectedTable() === 'people'
        ) {
          try {
            await loadEntityRows();
          } catch (_) {
          }
        }

        break;
      }

      await new Promise(resolve => setTimeout(resolve, 600));
    }

    if (!completed) {
      peopleAvatarSetStatus(
        'Остановлено. Можно продолжить.'
      );

      button.textContent = 'Продолжить загрузку';
    }
  } catch (error) {
    peopleAvatarSetStatus(`Ошибка: ${error.message}`);
    button.textContent = 'Продолжить загрузку';
  } finally {
    peopleAvatarRunning = false;
    button.disabled = false;
    stop.hidden = true;
  }
}

if (document.readyState === 'loading') {
  document.addEventListener(
    'DOMContentLoaded',
    createPeopleAvatarPanel,
    {once: true}
  );
} else {
  createPeopleAvatarPanel();
}
