const $ = id => document.getElementById(id);
const form = $('authForm'), mode = form.dataset.mode;

function showError(msg) { const e = $('error'); e.textContent = msg; e.hidden = !msg; }

async function submit(ev) {
  ev.preventDefault();
  showError('');
  const body = {username: $('username').value, password: $('password').value};
  if (mode === 'register') {
    body.fullName = $('fullName').value;
    body.phone = $('phone').value;
    if ($('password').value !== $('confirm').value) return showError('Passwords do not match.');
  }
  $('submit').disabled = true;
  try {
    const res = await fetch('/api/' + mode, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
    const data = await res.json();
    if (!res.ok) throw new Error(data.error);
    location.href = '/';
  } catch (e) { showError(e.message); }
  finally { $('submit').disabled = false; }
}
form.addEventListener('submit', submit);
fetch('/api/me').then(r => { if (r.ok) location.href = '/'; }); // already signed in
