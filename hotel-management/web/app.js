const $ = id => document.getElementById(id);
const money = n => '₹' + Number(n).toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2});
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const iso = d => { const z = new Date(d - d.getTimezoneOffset() * 6e4); return z.toISOString().slice(0, 10); };
const TYPES = {STANDARD: {label: 'Standard', rate: 2500, cap: 2, blurb: 'Comfortable and compact'},
  DELUXE: {label: 'Deluxe', rate: 4500, cap: 3, blurb: 'More space and a city view'},
  SUITE: {label: 'Suite', rate: 9000, cap: 4, blurb: 'Separate living area'}};
let me, selected = null, allBookings = [];

async function api(path, opts) {
  const res = await fetch('/api' + path, opts);
  const data = await res.json();
  if (res.status === 401) { location.href = '/login.html'; throw new Error(data.error); }
  if (!res.ok) throw new Error(data.error || 'Something went wrong');
  return data;
}
const post = (p, body) => api(p, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body || {})});
function toast(msg, type) {
  const t = document.createElement('div'); t.className = 'toast ' + (type || ''); t.textContent = msg;
  $('toasts').append(t); setTimeout(() => t.remove(), 4500);
}
document.querySelectorAll('[data-close]').forEach(b => b.onclick = () => b.closest('dialog').close());

// ---- start ----
(async function init() {
  me = await api('/me');
  $('who').textContent = me.fullName;
  $('avatar').textContent = me.fullName[0].toUpperCase();
  $('phone').value = me.phone;
  if (me.role === 'ADMIN') {
    $('dashTab').hidden = false;
    $('bookingsTab').textContent = 'All bookings';
    $('bookingsTitle').textContent = 'All bookings';
    $('bookingsSub').textContent = 'Every reservation in the hotel.';
  } else $('name').value = me.fullName;
  const today = new Date();
  $('checkIn').min = iso(today); $('checkIn').value = iso(today);
  $('checkOut').min = iso(new Date(+today + 864e5)); $('checkOut').value = iso(new Date(+today + 864e5));
  search();
})();
$('logoutBtn').onclick = async () => { await fetch('/api/logout', {method: 'POST'}); location.href = '/login.html'; };

// ---- tabs ----
function openTab(name) {
  document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
  document.querySelectorAll('.panel').forEach(p => p.hidden = p.id !== name);
  if (name === 'bookings') loadBookings();
  if (name === 'rooms') loadRooms();
  if (name === 'dashboard') loadStats();
}
document.querySelectorAll('.tab').forEach(b => b.onclick = () => openTab(b.dataset.tab));

// ---- step 1 & 2: dates and rooms ----
$('checkIn').onchange = () => {
  const next = new Date(new Date($('checkIn').value).getTime() + 864e5);
  $('checkOut').min = iso(next);
  if ($('checkOut').value <= $('checkIn').value) $('checkOut').value = iso(next);
  search();
};
$('checkOut').onchange = search;

async function search() {
  const inD = $('checkIn').value, outD = $('checkOut').value;
  const n = Math.round((new Date(outD) - new Date(inD)) / 864e5);
  $('nights').textContent = n > 0 ? `${n} night${n > 1 ? 's' : ''}` : '';
  try {
    const list = await api(`/availability?checkIn=${inD}&checkOut=${outD}`);
    const count = {STANDARD: 0, DELUXE: 0, SUITE: 0};
    list.forEach(r => count[r.type]++);
    if (selected && !count[selected]) selected = null;
    $('options').innerHTML = Object.entries(TYPES).map(([k, t]) => `
      <button class="option" data-type="${k}" aria-pressed="${selected === k}" ${count[k] ? '' : 'disabled'}>
        <span class="info"><span class="name">${t.label}</span><br><span class="muted small">${t.blurb} · sleeps ${t.cap}</span></span>
        <span class="pill ${count[k] ? 'ok' : 'bad'}">${count[k] ? count[k] + ' left' : 'Sold out'}</span>
        <span class="price">${money(t.rate)}<small>per night</small></span>
      </button>`).join('');
    document.querySelectorAll('.option').forEach(o => o.onclick = () => { selected = o.dataset.type; search(); });
    showDetails();
  } catch (e) { $('options').innerHTML = `<div class="error">${esc(e.message)}</div>`; selected = null; showDetails(); }
}

// ---- step 3: details + live quote ----
function showDetails() {
  $('detailsEmpty').hidden = !!selected; $('detailsForm').hidden = !selected;
  if (!selected) return;
  const cap = TYPES[selected].cap, keep = Math.min(+$('guests').value || 1, cap);
  $('guests').innerHTML = Array.from({length: cap}, (_, i) => `<option>${i + 1}</option>`).join('');
  $('guests').value = keep;
  refreshQuote();
}
function billRows(b) {
  return `<table class="rows">
    <tr><td>${b.nights} night${b.nights > 1 ? 's' : ''} × ${money(b.rate)}</td><td>${money(b.roomCharge)}</td></tr>
    ${b.breakfast > 0 ? `<tr><td>Breakfast, ${b.guests} guest${b.guests > 1 ? 's' : ''}</td><td>${money(b.breakfast)}</td></tr>` : ''}
    ${b.longStayDiscount > 0 ? `<tr><td>Long-stay discount (10%)</td><td>− ${money(b.longStayDiscount)}</td></tr>` : ''}
    ${b.promoDiscount > 0 ? `<tr><td>Promo ${esc(b.promoCode)}</td><td>− ${money(b.promoDiscount)}</td></tr>` : ''}
    <tr><td>GST (12%)</td><td>${money(b.gst)}</td></tr>
    <tr class="total"><td>Total</td><td>${money(b.total)}</td></tr></table>`;
}
async function refreshQuote() {
  if (!selected) return;
  const q = `type=${selected}&checkIn=${$('checkIn').value}&checkOut=${$('checkOut').value}&guests=${$('guests').value}` +
    `&breakfast=${$('breakfast').checked}&promo=${encodeURIComponent($('promo').value)}`;
  try { $('quote').innerHTML = billRows(await api('/quote?' + q)); $('reserveBtn').disabled = false; }
  catch (e) { $('quote').innerHTML = `<div class="error">${esc(e.message)}</div>`; $('reserveBtn').disabled = true; }
}
$('guests').onchange = $('breakfast').onchange = refreshQuote;
let promoTimer; $('promo').oninput = () => { clearTimeout(promoTimer); promoTimer = setTimeout(refreshQuote, 350); };

$('reserveBtn').onclick = async () => {
  $('reserveBtn').disabled = true;
  try {
    const b = await post('/bookings', {customerName: $('name').value, phone: $('phone').value, type: selected,
      checkIn: $('checkIn').value, checkOut: $('checkOut').value, guests: $('guests').value,
      breakfast: $('breakfast').checked, promo: $('promo').value});
    $('okBody').innerHTML = `<h2>Booking confirmed</h2><p class="muted">Reference #${b.id}</p>
      <table class="rows" style="margin-top:14px"><tr><td>Room</td><td>${b.room} · ${b.typeLabel}</td></tr>
      <tr><td>Guest</td><td>${esc(b.customerName)}</td></tr><tr><td>Check-in</td><td>${b.checkIn}</td></tr>
      <tr><td>Check-out</td><td>${b.checkOut}</td></tr></table>`;
    $('okDialog').showModal();
    selected = null; $('promo').value = ''; $('breakfast').checked = false;
  } catch (e) { toast(e.message, 'err'); }
  await search();
};
$('viewBookings').onclick = () => { $('okDialog').close(); openTab('bookings'); };

// ---- bookings ----
async function loadBookings() { allBookings = await api('/bookings'); renderBookings(); }
function renderBookings() {
  const t = $('filterText').value.trim().toLowerCase(), s = $('filterStatus').value, admin = me.role === 'ADMIN';
  const list = allBookings.filter(b => (!s || b.status === s) && (!t || `${b.id} ${b.customerName} ${b.room} ${b.owner}`.toLowerCase().includes(t)));
  $('bookingTable').innerHTML = list.length
    ? `<thead><tr><th>Ref</th><th>Guest</th><th>Room</th><th>Stay</th><th>Guests</th><th>Status</th><th></th></tr></thead><tbody>` +
      list.map(b => `<tr><td>#${b.id}</td><td>${esc(b.customerName)}${admin ? `<br><span class="muted small">@${esc(b.owner)}</span>` : ''}</td>
      <td>${b.room} · ${b.typeLabel}</td><td>${b.checkIn} to ${b.checkOut}</td>
      <td>${b.guests}${b.breakfast ? '<br><span class="muted small">with breakfast</span>' : ''}</td>
      <td><span class="pill ${b.status === 'CONFIRMED' ? 'ok' : 'bad'}">${b.status === 'CONFIRMED' ? 'Confirmed' : 'Cancelled'}</span>
        ${b.cancellationFee > 0 ? `<br><span class="muted small">Fee ${money(b.cancellationFee)}</span>` : ''}</td>
      <td><div class="actions">${b.status === 'CONFIRMED' ? `<button class="btn sm" data-bill="${b.id}">View bill</button><button class="btn sm danger" data-cancel="${b.id}">Cancel</button>` : ''}</div></td></tr>`).join('') + '</tbody>'
    : `<tbody><tr><td><div class="empty"><b>No bookings found</b>${allBookings.length ? 'Try a different search.' : 'Book a room to get started.'}</div></td></tr></tbody>`;
  document.querySelectorAll('[data-cancel]').forEach(b => b.onclick = async () => {
    if (!confirm(`Cancel booking #${b.dataset.cancel}? A fee applies if check-in is within 2 days.`)) return;
    try { await post(`/bookings/${b.dataset.cancel}/cancel`); toast('Booking cancelled'); loadBookings(); } catch (e) { toast(e.message, 'err'); }
  });
  document.querySelectorAll('[data-bill]').forEach(b => b.onclick = async () => {
    try {
      const x = await api(`/bookings/${b.dataset.bill}/bill`);
      $('billBody').innerHTML = `<h2>Bill #${x.bookingId}</h2><p class="muted" style="margin-bottom:14px">${esc(x.customerName)} · Room ${x.room} (${x.typeLabel})</p>` + billRows(x);
      $('billDialog').showModal();
    } catch (e) { toast(e.message, 'err'); }
  });
}
$('filterText').oninput = $('filterStatus').onchange = renderBookings;
$('printBtn').onclick = () => window.print();

// ---- rooms ----
async function loadRooms() {
  const rooms = await api('/rooms'), admin = me.role === 'ADMIN';
  const label = {VACANT: ['ok', 'Vacant'], OCCUPIED: ['bad', 'Occupied'], MAINTENANCE: ['', 'Maintenance']};
  $('roomGrid').innerHTML = rooms.map(r => `<div class="room"><b>${r.number}</b>
    <span class="muted small">${r.typeLabel} · Floor ${r.floor}</span><span><span class="pill ${label[r.status][0]}">${label[r.status][1]}</span></span>
    ${admin ? `<button class="btn sm" data-maint="${r.number}" data-on="${r.status !== 'MAINTENANCE'}">${r.status === 'MAINTENANCE' ? 'Return to service' : 'Set to maintenance'}</button>` : ''}</div>`).join('');
  document.querySelectorAll('[data-maint]').forEach(b => b.onclick = async () => {
    try { await post(`/rooms/${b.dataset.maint}/maintenance`, {on: b.dataset.on}); loadRooms(); } catch (e) { toast(e.message, 'err'); }
  });
}

// ---- dashboard ----
async function loadStats() {
  const s = await api('/stats');
  const c = (l, v) => `<div class="stat"><b>${v}</b><span class="muted small">${l}</span></div>`;
  $('stats').innerHTML = c('Occupancy tonight', s.occupancyPct + '%') + c('Rooms occupied', `${s.occupiedTonight} of ${s.totalRooms}`) +
    c('Active bookings', s.activeBookings) + c('Cancelled bookings', s.cancelledBookings) +
    c('Rooms in maintenance', s.maintenanceRooms) + c('Booked revenue', money(s.revenue));
  $('byType').innerHTML = s.byType.map(t => `<div class="row"><span>${t.label}</span> <span class="muted small">${t.occupied} of ${t.rooms} occupied</span>
    <div class="bar"><i style="width:${t.rooms ? t.occupied * 100 / t.rooms : 0}%"></i></div></div>`).join('');
}
