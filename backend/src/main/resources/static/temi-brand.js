(() => {
  function applyTemiLogos(root) {
    const elements = [];
    if (root instanceof Element && root.matches('.logo, .introLogo')) elements.push(root);
    if (root.querySelectorAll) elements.push(...root.querySelectorAll('.logo, .introLogo'));

    elements.forEach(element => {
      if (element.dataset.temiLogoApplied) return;
      const image = document.createElement('img');
      image.src = '/temi-logo.png';
      image.alt = 'Temi';
      element.replaceChildren(image);
      element.dataset.temiLogoApplied = 'true';
    });
  }

  applyTemiLogos(document.documentElement);
  new MutationObserver(records => {
    records.forEach(record => record.addedNodes.forEach(node => {
      if (node instanceof Element) applyTemiLogos(node);
    }));
  }).observe(document.body, { childList: true, subtree: true });
})();
