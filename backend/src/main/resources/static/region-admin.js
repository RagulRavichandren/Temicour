(() => {
  const originalRender = window.render;
  const originalPageContent = window.pageContent;

  function districtAccounts() {
    return regions.filter(region => (
      String(region.accountType || '').toUpperCase() === 'DISTRICT'
      || (!region.accountType && !region.parentDistrict)
    ));
  }

  function regionAccounts() {
    return regions.filter(region => String(region.accountType || '').toUpperCase() === 'REGION');
  }

  function districtLabel(region) {
    return region.district || String(region.name || '').replace(/^temi\s*[-:]\s*/i, '');
  }

  function contactField(contact = '') {
    return `<label>Contact (optional)<input name="contact" value="${esc(contact)}" inputmode="numeric" pattern="[0-9]{10}" maxlength="10" title="Enter exactly 10 digits"></label>`;
  }

  function accountRow(region, type) {
    return `<div class="row" style="grid-template-columns:${type === 'DISTRICT' ? '1.5fr 1fr 1fr .8fr .8fr' : '1.4fr 1fr 1fr 1fr 1fr .8fr .8fr'}">
      <div><b>${esc(region.name)}</b><span>${type === 'REGION' ? `Under ${esc(region.parentDistrict || '-')}` : esc(region.code || '')}</span></div>
      <div>${esc(districtLabel(region) || '-')}</div>
      ${type === 'REGION' ? `<div>${esc(region.area || '-')}</div><div>${esc(region.contact || '-')}</div>` : ''}
      <div><b>${esc(region.username || '-')}</b></div>
      <button class="btn light" onclick="editManagedRegion(${region.id})">Edit</button>
      <button class="btn light" onclick="deleteManagedRegion(${region.id})">Delete</button>
    </div>`;
  }

  function managedAccountsPage(type) {
    const districts = districtAccounts();
    const accounts = type === 'DISTRICT' ? districts : regionAccounts();
    const heading = type === 'DISTRICT' ? 'District logins' : 'Region logins';
    const locationFields = type === 'DISTRICT'
      ? '<label>State<input name="state" required></label><label>Country<input name="country" value="India" required></label>'
      : `<label>District<input id="regionDistrict" readonly></label>
         <label>State<input id="regionState" readonly></label>
         <label>Country<input id="regionCountry" readonly></label>
         <label>Area (optional)<input name="area"></label>`;
    const districtPicker = type === 'REGION'
      ? `<label class="wide">District login
           <input id="districtLoginSearch" list="districtLoginOptions" placeholder="Search and choose a district login" autocomplete="off" oninput="selectRegionDistrict(this.value)" required>
           <datalist id="districtLoginOptions">${districts.map(district => `<option value="${esc(district.name)}">${esc(district.district || district.name)}</option>`).join('')}</datalist>
           <input id="selectedParentDistrict" type="hidden" name="parentDistrict">
         </label>`
      : '';
    return `<section class="panel">
      <div class="panelHead"><div><span class="eyebrow">LOGIN ADMINISTRATION</span><h3>${heading}</h3>
        <p>${type === 'DISTRICT' ? 'Create and manage district administrator accounts.' : 'Choose a district login to create a region under that district.'}</p>
      </div></div>
      <form class="formGrid" onsubmit="addManagedRegion(event,'${type}')">
        ${districtPicker}
        <label>${type === 'DISTRICT' ? 'District login name' : 'Region name'}<input name="name" required></label>
        ${locationFields}
        ${type === 'DISTRICT' ? '' : contactField()}
        <label>Username<input name="username" required></label>
        <label>Password<input name="password" type="password" required></label>
        <input type="hidden" name="accountType" value="${type}">
        <div class="modalFoot" style="grid-column:1/-1"><button class="btn dark">＋ Add ${type === 'DISTRICT' ? 'district' : 'region'} login</button></div>
      </form>
      <div class="table" style="margin-top:16px">${accounts.length
        ? accounts.map(region => accountRow(region, type)).join('')
        : `<div class="empty">No ${type.toLowerCase()} logins yet.</div>`}</div>
    </section>`;
  }

  window.pageContent = function (...args) {
    if (userRole === 'ADMIN' && (current === 'Districts' || current === 'Regions')) {
      return managedAccountsPage(current === 'Districts' ? 'DISTRICT' : 'REGION');
    }
    if (userRole === 'DISTRICT_ADMIN' && current === 'Regions') {
      return `<section class="panel">
        <div class="panelHead"><div><span class="eyebrow">LOGIN ADMINISTRATION</span><h3>Region logins</h3>
          <p>Create regional accounts under ${esc(userRegion)} district.</p>
        </div></div>
        <form class="formGrid" onsubmit="addManagedRegion(event,'REGION')">
          <label>Region name<input name="name" required></label>
          <label>District<input value="${esc(districtName || userRegion)}" readonly></label>
          <label>State<input value="${esc(districtState)}" readonly></label>
          <label>Country<input name="country" value="India"></label>
          <label>Area (optional)<input name="area"></label>
          ${contactField()}
          <label>Username<input name="username" required></label>
          <label>Password<input name="password" type="password" required></label>
          <input type="hidden" name="parentDistrict" value="${esc(userRegion)}">
          <input type="hidden" name="accountType" value="REGION">
          <div class="modalFoot" style="grid-column:1/-1"><button class="btn dark">＋ Add region login</button></div>
        </form>
        <div class="table" style="margin-top:16px">${regionAccounts().length
          ? regionAccounts().map(region => accountRow(region, 'REGION')).join('')
          : '<div class="empty">No region logins yet.</div>'}</div>
      </section>`;
    }
    return originalPageContent.apply(this, args);
  };

  window.render = function (...args) {
    originalRender.apply(this, args);
    if (!loggedIn || userRole !== 'ADMIN' || userRegion !== 'ALL') return;

    const accountsButton = [...document.querySelectorAll('.side > .nav')]
      .find(button => button.getAttribute('onclick') === "nav('Regions')");
    if (!accountsButton) return;
    accountsButton.setAttribute('onclick', "nav('Districts')");
    accountsButton.setAttribute('aria-label', 'Districts');
    accountsButton.classList.toggle('active', current === 'Districts');
    accountsButton.querySelector('span:last-of-type').textContent = 'Districts';
    if (!document.querySelector('.side > .nav[data-module="regions"]')) {
      const regionsButton = document.createElement('button');
      regionsButton.className = `nav${current === 'Regions' ? ' active' : ''}`;
      regionsButton.dataset.module = 'regions';
      regionsButton.setAttribute('aria-label', 'Regions');
      regionsButton.innerHTML = '<span>◇</span><span>Regions</span>';
      regionsButton.addEventListener('click', () => nav('Regions'));
      accountsButton.insertAdjacentElement('afterend', regionsButton);
    }
    document.querySelector('.side > .nav[data-module="regions"]')
      ?.classList.toggle('active', current === 'Regions');
    const regionsButton = document.querySelector('.side > .nav[data-module="regions"]');
    const adminButton = [...document.querySelectorAll('.side > .nav')]
      .find(button => button.getAttribute('onclick') === "nav('Profile')");
    const reportsButton = [...document.querySelectorAll('.side > .nav')]
      .find(button => button.getAttribute('onclick') === "nav('Reports')");
    const dashboardButton = [...document.querySelectorAll('.side > .nav')]
      .find(button => button.getAttribute('onclick') === "nav('Dashboard')");
    accountsButton.querySelector('span:last-of-type').textContent = 'District';
    adminButton?.querySelector('span:last-of-type')?.replaceChildren('Admin panel');
    reportsButton?.querySelector('span:last-of-type')?.replaceChildren('Report');
    [
      dashboardButton,
      adminButton,
      accountsButton,
      regionsButton,
      reportsButton
    ].filter(Boolean).forEach(button => document.querySelector('.side').insertBefore(button, document.querySelector('.sideBottom')));
  };

  window.selectRegionDistrict = function (name) {
    const selected = districtAccounts().find(district => district.name.toLowerCase() === name.trim().toLowerCase());
    const parent = document.getElementById('selectedParentDistrict');
    const district = document.getElementById('regionDistrict');
    const state = document.getElementById('regionState');
    const country = document.getElementById('regionCountry');
    if (parent) parent.value = selected?.name || '';
    if (district) district.value = selected ? districtLabel(selected) : '';
    if (state) state.value = selected?.state || '';
    if (country) country.value = selected?.country || '';
  };

  window.addManagedRegion = async function (event, type) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const body = Object.fromEntries(new FormData(form).entries());
    if (type === 'REGION' && userRole === 'ADMIN' && !body.parentDistrict) {
      toast('Choose a district login before creating the region');
      return;
    }
    const response = await fetch('/api/regions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Role': userRole === 'DISTRICT_ADMIN' ? 'REGION_ADMIN' : userRole,
        'X-Operating-Region': userRegion
      },
      body: JSON.stringify(body)
    });
    if (!response.ok) {
      let message = type === 'DISTRICT' ? 'District login could not be created' : 'Region login could not be created';
      try {
        const error = await response.json();
        if (error.message) message = error.message;
      } catch {}
      toast(message);
      return;
    }
    toast(`${type === 'DISTRICT' ? 'District' : 'Region'} login added`);
    await load();
    nav(type === 'DISTRICT' ? 'Districts' : 'Regions');
  };

  window.editManagedRegion = function (id) {
    const region = regions.find(item => item.id === id);
    if (!region) return;
    if (userRole !== 'ADMIN') {
      $('modal')?.remove();
      document.body.insertAdjacentHTML('beforeend', `<div class="modal" id="modal"><form class="modalBox" onsubmit="saveManagedRegion(event,${id},false)">
        <div class="modalHead"><div><span class="eyebrow">LOGIN ADMINISTRATION</span><h2>Edit region login</h2></div>
          <button type="button" class="close" onclick="$('modal').remove()">×</button></div>
        <div class="formGrid">
          <label>Region name<input name="name" value="${esc(region.name)}" required></label>
          <label>Area (optional)<input name="area" value="${esc(region.area || '')}"></label>
          <label>District<input value="${esc(districtLabel(region))}" readonly></label>
          <label>State<input name="state" value="${esc(region.state || '')}" readonly></label>
          <label>Country<input name="country" value="${esc(region.country || '')}"></label>
          ${contactField(region.contact || '')}
          <label>Username<input name="username" value="${esc(region.username || '')}" required></label>
          <label>New password<input name="password" type="password" placeholder="Leave blank to keep current"></label>
        </div><div class="modalFoot"><button type="button" class="btn light" onclick="$('modal').remove()">Cancel</button>
          <button class="btn dark">Save changes</button></div>
      </form></div>`);
      return;
    }
    const isDistrict = String(region.accountType || '').toUpperCase() === 'DISTRICT'
      || (!region.accountType && !region.parentDistrict);
    const districtOptions = districtAccounts().map(district => (
      `<option value="${esc(district.name)}" ${district.name === region.parentDistrict ? 'selected' : ''}>${esc(district.name)}</option>`
    )).join('');
    $('modal')?.remove();
    document.body.insertAdjacentHTML('beforeend', `<div class="modal" id="modal"><form class="modalBox" onsubmit="saveManagedRegion(event,${id},${isDistrict})">
      <div class="modalHead"><div><span class="eyebrow">LOGIN ADMINISTRATION</span><h2>Edit ${isDistrict ? 'district' : 'region'} login</h2></div>
        <button type="button" class="close" onclick="$('modal').remove()">×</button></div>
      <div class="formGrid">
        <label>${isDistrict ? 'District login name' : 'Region name'}<input name="name" value="${esc(region.name)}" required></label>
        ${isDistrict ? `<label>State (optional)<input name="state" value="${esc(region.state || '')}"></label>
          <label>Country (optional)<input name="country" value="${esc(region.country || '')}"></label>`
          : `<label class="wide">District login<select name="parentDistrict" required>${districtOptions}</select></label>
             <label>District<input value="${esc(districtLabel(region))}" readonly></label>
             <label>State<input value="${esc(region.state || '')}" readonly></label>
             <label>Country<input value="${esc(region.country || '')}" readonly></label>
             <label>Area (optional)<input name="area" value="${esc(region.area || '')}"></label>
             ${contactField(region.contact || '')}`}
        <label>Username<input name="username" value="${esc(region.username || '')}" required></label>
        <label>New password<input name="password" type="password" placeholder="Leave blank to keep current"></label>
      </div><div class="modalFoot"><button type="button" class="btn light" onclick="$('modal').remove()">Cancel</button>
        <button class="btn dark">Save changes</button></div>
    </form></div>`);
    const selector = document.querySelector('#modal select[name="parentDistrict"]');
    selector?.addEventListener('change', () => {
      const parent = districtAccounts().find(item => item.name === selector.value);
      const inputs = document.querySelectorAll('#modal input[readonly]');
      if (inputs[0]) inputs[0].value = parent ? districtLabel(parent) : '';
      if (inputs[1]) inputs[1].value = parent?.state || '';
      if (inputs[2]) inputs[2].value = parent?.country || '';
    });
  };

  window.saveManagedRegion = async function (event, id, isDistrict) {
    event.preventDefault();
    const adminRole = userRole === 'DISTRICT_ADMIN' ? 'REGION_ADMIN' : userRole;
    const response = await fetch(`/api/regions/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Role': adminRole,
        'X-Operating-Region': userRegion
      },
      body: JSON.stringify(Object.fromEntries(new FormData(event.currentTarget).entries()))
    });
    if (!response.ok) {
      toast('Login details are invalid or already in use');
      return;
    }
    $('modal')?.remove();
    toast(`${isDistrict ? 'District' : 'Region'} login updated`);
    await load();
    nav(isDistrict ? 'Districts' : 'Regions');
  };

  window.deleteManagedRegion = async function (id) {
    if (!confirm('Delete this login?')) return;
    const response = await fetch(`/api/regions/${id}`, {
      method: 'DELETE',
      headers: {
        'X-Admin-Role': userRole === 'DISTRICT_ADMIN' ? 'REGION_ADMIN' : userRole,
        'X-Operating-Region': userRegion
      }
    });
    if (!response.ok) {
      toast('Login could not be deleted');
      return;
    }
    toast('Login deleted');
    await load();
    nav(current);
  };
})();
