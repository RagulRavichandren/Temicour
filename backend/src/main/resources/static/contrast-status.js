(() => {
  const statusClasses = {
    WAITING_FOR_VERIFICATION: 'status-waiting-verification',
    TO_PAY_WAITING_FOR_VERIFICATION: 'status-waiting-verification',
    VERIFIED: 'status-verified',
    IN_TRANSIT: 'status-in-transit',
    OUT_FOR_DELIVERY: 'status-out-for-delivery',
    RECEIVED: 'status-in-transit',
    DELIVERED: 'status-delivered',
    CANCELLED: 'status-cancelled',
    CANCELED: 'status-cancelled'
  };
  const classNames = Object.values(statusClasses);

  function applyStatusColor(node) {
    if (!(node instanceof Element)) return;
    const badges = node.matches('.badge') ? [node] : [...node.querySelectorAll('.badge')];
    for (const badge of badges) {
      const tracking = badge.closest('.row')?.querySelector('b')?.textContent.trim();
      const shipment = tracking ? shipments.find(item => item.trackingNumber === tracking) : null;
      const status = String(shipment?.status || badge.textContent).trim().toUpperCase().replace(/[\s-]+/g, '_');
      badge.classList.remove(...classNames);
      if (statusClasses[status]) badge.classList.add(statusClasses[status]);
    }
  }

  applyStatusColor(document.documentElement);
  new MutationObserver(records => {
    records.forEach(record => record.addedNodes.forEach(applyStatusColor));
  }).observe(document.body, { childList: true, subtree: true });
})();
