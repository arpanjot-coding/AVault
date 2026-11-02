const toggle = document.querySelector('.menu-toggle');
const navigation = document.querySelector('#navigation-links');
function closeMenu() {
  toggle?.setAttribute('aria-expanded', 'false');
  navigation?.classList.remove('is-open');
}
toggle?.addEventListener('click', () => {
  const open = toggle.getAttribute('aria-expanded') !== 'true';
  toggle.setAttribute('aria-expanded', String(open));
  navigation.classList.toggle('is-open', open);
});
navigation?.addEventListener('click', event => {
  if (event.target.closest('a')) closeMenu();
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && toggle?.getAttribute('aria-expanded') === 'true') {
    closeMenu(); toggle.focus();
  }
});
document.addEventListener('click', event => {
  if (!event.target.closest('.navigation')) closeMenu();
});

// Public, unauthenticated GitHub data. Keep the dated snapshot on offline/rate-limit errors.
fetch('https://api.github.com/repos/arpanjot-coding/AVault', {headers: {Accept: 'application/vnd.github+json'}})
  .then(response => { if (!response.ok) throw new Error('GitHub unavailable'); return response.json(); })
  .then(repository => {
    if (!Number.isSafeInteger(repository.stargazers_count) || repository.stargazers_count < 0) return;
    document.querySelectorAll('[data-github-stars]').forEach(element => {
      element.textContent = new Intl.NumberFormat(document.documentElement.lang).format(repository.stargazers_count);
      element.title = 'GitHub · ' + new Date().toLocaleDateString(document.documentElement.lang);
    });
  }).catch(() => {});

const languagePicker = document.querySelector('.language-picker');
document.addEventListener('click', event => {
  if (!event.target.closest('.language-picker')) languagePicker?.removeAttribute('open');
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && languagePicker?.open) {
    languagePicker.removeAttribute('open');
    languagePicker.querySelector('summary').focus();
  }
});
