(() => {
  function isCurrentRegion(shipment) {
    const currentRegion = String(userRegion || '').trim().toLowerCase();
    if (shipment.returnReason && !shipment.returnOfTrackingNumber) {
      return [shipment.sourceRegion, shipment.destinationRegion]
        .some(region => String(region || '').trim().toLowerCase() === currentRegion);
    }
    const returnRegion = shipment.returnOfTrackingNumber
      ? shipment.sourceRegion
      : shipment.destinationRegion;
    return String(returnRegion || '').trim().toLowerCase() === currentRegion;
  }

  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString();
  }

  function returnTimestamp(shipment) {
    return shipment.returnOfTrackingNumber
      ? shipment.shipmentDate || shipment.createdAt
      : shipment.updatedAt || shipment.createdAt;
  }

  function createCell(row, value, strong = false) {
    const cell = document.createElement('td');
    if (strong) {
      const text = document.createElement('strong');
      text.textContent = value || '-';
      cell.append(text);
    } else {
      cell.textContent = value || '-';
    }
    row.append(cell);
    return cell;
  }

  function renderHistory() {
    const existing = document.querySelector('.return-history-panel');
    if (current !== 'Return' || userRole !== 'REGION_ADMIN') {
      existing?.remove();
      return;
    }

    const returnPanel = [...document.querySelectorAll('.main > .panel')]
      .find(panel => panel.querySelector('h3')?.textContent.trim() === 'Return shipment');
    if (!returnPanel) return;

    const returnedShipments = shipments
      .filter(shipment => (
        shipment.returnOfTrackingNumber
        || shipment.returnReason
      ) && isCurrentRegion(shipment))
      .sort((first, second) => new Date(returnTimestamp(second) || 0)
        - new Date(returnTimestamp(first) || 0));
    const signature = returnedShipments.map(shipment => [
      shipment.trackingNumber,
      shipment.returnOfTrackingNumber || '',
      shipment.returnReason,
      shipment.status,
      shipment.updatedAt
    ].join(':')).join('|');
    if (existing?.dataset.historySignature === signature) return;
    existing?.remove();

    const section = document.createElement('section');
    section.className = 'panel return-history-panel';
    section.dataset.historySignature = signature;

    const header = document.createElement('div');
    header.className = 'panelHead return-history-head';
    const heading = document.createElement('div');
    const eyebrow = document.createElement('span');
    eyebrow.className = 'eyebrow';
    eyebrow.textContent = 'RETURN OPERATIONS';
    const title = document.createElement('h3');
    title.textContent = 'Returned shipment history';
    const description = document.createElement('p');
    description.textContent = `Saved return shipments for ${userRegion}.`;
    heading.append(eyebrow, title, description);
    const count = document.createElement('span');
    count.className = 'return-history-count';
    count.textContent = String(returnedShipments.length);
    header.append(heading, count);
    section.append(header);

    if (!returnedShipments.length) {
      const empty = document.createElement('div');
      empty.className = 'empty return-history-empty';
      empty.textContent = 'No returned shipments yet.';
      section.append(empty);
    } else {
      const wrapper = document.createElement('div');
      wrapper.className = 'return-history-table-wrap';
      const table = document.createElement('table');
      table.className = 'return-history-table';
      const tableHead = document.createElement('thead');
      const headerRow = document.createElement('tr');
      ['Shipment tracking', 'Return link', 'From region', 'To region', 'Reason', 'Status', 'Returned at']
        .forEach(label => {
          const cell = document.createElement('th');
          cell.scope = 'col';
          cell.textContent = label;
          headerRow.append(cell);
        });
      tableHead.append(headerRow);

      const body = document.createElement('tbody');
      returnedShipments.forEach(shipment => {
        const row = document.createElement('tr');
        const sameShipmentReturn = Boolean(shipment.returnReason && !shipment.returnOfTrackingNumber);
        createCell(row, shipment.trackingNumber, true);
        createCell(row, shipment.returnOfTrackingNumber || 'Same shipment');
        createCell(row, sameShipmentReturn ? shipment.destinationRegion : shipment.sourceRegion);
        createCell(row, sameShipmentReturn ? shipment.sourceRegion : shipment.destinationRegion);
        createCell(row, shipment.returnReason || shipment.packageDescription);
        const statusCell = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = String(shipment.status || 'RETURNED').replaceAll('_', ' ');
        statusCell.append(badge);
        row.append(statusCell);
        createCell(row, formatDate(returnTimestamp(shipment)));
        body.append(row);
      });

      table.append(tableHead, body);
      wrapper.append(table);
      section.append(wrapper);
    }

    returnPanel.insertAdjacentElement('afterend', section);
  }

  renderHistory();
  new MutationObserver(() => renderHistory())
    .observe(document.body, { childList: true, subtree: true });
})();
