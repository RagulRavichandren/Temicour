(() => {
  const icons = {
    Dashboard: '<rect x="3" y="3" width="8" height="8" rx="1.5"/><rect x="13" y="3" width="8" height="5" rx="1.5"/><rect x="13" y="10" width="8" height="11" rx="1.5"/><rect x="3" y="13" width="8" height="8" rx="1.5"/>',
    Shipments: '<path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="m4.3 7.7 7.7 4.5 7.7-4.5M12 21v-8.8M8 5.3l8 4.5"/>',
    Verification: '<circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/>',
    Customers: '<path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="10" cy="7" r="4"/><path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
    Reports: '<path d="M3 3v18h18"/><path d="M18 17V9M13 17V5M8 17v-3"/>',
    Regions: '<path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z"/><path d="M9 3v15M15 6v15"/>',
    'Admin profile': '<circle cx="12" cy="8" r="4"/><path d="M5 21a7 7 0 0 1 14 0"/>',
    Receive: '<path d="M12 3v12M7 10l5 5 5-5"/><path d="M5 20h14"/>',
    Send: '<path d="M12 21V9M7 14l5-5 5 5"/><path d="M5 4h14"/>',
    Return: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 6 6v5"/>',
    'Send Later': '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'
  };

  function refreshIcons(node) {
    if (!(node instanceof Element)) return;
    const buttons = node.matches('button.nav') ? [node] : [...node.querySelectorAll('button.nav')];

    buttons.forEach(button => {
      const spans = [...button.children].filter(child => child.tagName === 'SPAN');
      const iconContainer = spans[0];
      const label = spans[1]?.textContent.trim();
      if (!iconContainer || !icons[label] || iconContainer.dataset.navIcon === label) return;

      iconContainer.classList.add('nav-icon');
      iconContainer.dataset.navIcon = label;
      iconContainer.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[label]}</svg>`;
      button.setAttribute('aria-label', label);
    });
  }

  refreshIcons(document.documentElement);
  new MutationObserver(records => {
    records.forEach(record => record.addedNodes.forEach(refreshIcons));
  }).observe(document.body, { childList: true, subtree: true });
})();
