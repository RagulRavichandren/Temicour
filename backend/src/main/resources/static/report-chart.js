(() => {
  function addPieRow(card, summaryLabel, rowClass, title, received, sent, formatValue) {
    if (card.querySelector(`.${rowClass}`)) return;
    const total = received + sent;
    const row = document.createElement('div');
    row.className = `report-pie-row ${rowClass}`;

    const pie = document.createElement('div');
    pie.className = `report-pie${total ? '' : ' is-empty'}`;
    pie.setAttribute('role', 'img');
    pie.setAttribute('aria-label', `${title}: ${formatValue(received)} received and ${formatValue(sent)} sent`);
    pie.style.setProperty('--received-share', `${total ? received / total * 100 : 0}%`);

    const legend = document.createElement('div');
    legend.className = 'report-pie-legend';
    legend.innerHTML = `<strong class="report-pie-title">${title}</strong><span><i class="receive"></i>Receive <b>${formatValue(received)}</b></span><span><i class="send"></i>Send <b>${formatValue(sent)}</b></span>`;

    row.append(pie, legend);
    summaryLabel.after(row);
  }

  function addReportPies(card) {
    if (card.querySelector('.report-pie-count')) return;
    const metrics = [...card.querySelectorAll('.reportMetric')];
    const readValue = label => {
      const metric = metrics.find(item => item.querySelector('span')?.textContent.trim().toLowerCase().startsWith(label));
      const rawValue = metric?.querySelector('b')?.textContent.replace(/[^\d.-]/g, '') || '0';
      const value = Number(rawValue);
      return Number.isFinite(value) ? value : 0;
    };
    const summaryLabel = [...card.querySelectorAll('.eyebrow')].find(item => item.textContent.includes('SUMMARY'));
    if (!summaryLabel) return;

    const currency = value => new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2
    }).format(value);

    addPieRow(card, summaryLabel, 'report-pie-count', 'Shipment count',
      readValue('total received shipments'), readValue('total sent shipments'), String);
    addPieRow(card, summaryLabel.nextElementSibling, 'report-pie-amount', 'Shipment amount',
      readValue('received amount'), readValue('sent amount'), currency);
  }

  function inspect(node) {
    if (!(node instanceof Element)) return;
    if (node.matches('.reportGrid .reportCard')) addReportPies(node);
    node.querySelectorAll('.reportGrid .reportCard').forEach(addReportPies);
  }

  inspect(document.documentElement);
  new MutationObserver(records => {
    records.forEach(record => record.addedNodes.forEach(inspect));
  }).observe(document.body, { childList: true, subtree: true });
})();
