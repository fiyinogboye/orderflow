let session;
let selectedOrder;
let refreshing = false;
const $ = id => document.getElementById(id);
const money = cents => new Intl.NumberFormat('en-CA', {style:'currency',currency:'CAD'}).format(cents / 100);

// Use textContent for data, so product names cannot sneak HTML into the page.
function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function notice(message, error = false) {
  $('notice').textContent = message;
  $('notice').className = error ? 'error' : '';
}
async function api(path, method = 'GET', body, headers = {}) {
  const response = await fetch(path, {
    method,
    headers: { ...(body ? {'Content-Type':'application/json'} : {}),
      ...(method !== 'GET' ? {[session.csrfHeader]:session.csrfToken} : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined
  });
  if (response.status === 401) { location.href = '/login'; throw new Error('Please sign in again.'); }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || `Request failed (${response.status}).`);
  return data;
}
function newKey() { $('key').value = crypto.randomUUID(); }
async function timeline() {
  if (!selectedOrder) return;
  const events = await api(`/api/orders/${selectedOrder}/events`);
  $('selected').textContent = selectedOrder.slice(0,8);
  $('timeline').replaceChildren(...events.map(event => {
    const item = element('li', event.message);
    item.prepend(element('time', new Date(event.createdAt).toLocaleTimeString()));
    return item;
  }));
}
async function adminAction(order, action) {
  if (action === 'cancel' && !confirm('Cancel this DEAD order and return its stock?')) return;
  try {
    await api(`/api/admin/orders/${order.id}/${action}`, 'POST');
    selectedOrder = order.id;
    notice('Action saved. Watch the timeline for the next step.');
    await refresh();
  } catch (error) { notice(error.message, true); }
}
function orderCard(order, products) {
  const card = element('article', undefined, 'order');
  const top = element('div', undefined, 'order-top');
  const product = products.find(p => p.id === order.productId);
  const title = element('button', `${order.quantity} × ${product?.name || 'Product'}`, 'order-title');
  title.onclick = async () => { selectedOrder = order.id; try { await timeline(); } catch(e) { notice(e.message,true); } };
  top.append(title,element('span',order.status,'badge ' + order.status));
  card.append(top,element('p',`${order.id.slice(0,8)} · ${money(order.totalCents)} · ${order.attempts} attempt(s) · ${order.username}`),
    element('p',order.scenario.replaceAll('_',' ').toLowerCase()));
  if (session.operator) {
    const actions = element('div',undefined,'actions');
    const available = order.status === 'DEAD' ? [['recover','Provider recovered → retry'],['cancel','Cancel order']] :
      order.status === 'PENDING' ? [['redeliver','Redeliver job']] : [];
    for (const [action,label] of available) {
      const button = element('button',label,'quiet');
      button.onclick = () => adminAction(order,action);
      actions.append(button);
    }
    card.append(actions);
  }
  return card;
}
async function refresh() {
  if (refreshing) return;
  refreshing = true;
  try {
    const [products,orders] = await Promise.all([api('/api/products'),api('/api/orders')]);
    const previous = $('product').value;
    $('product').replaceChildren(...products.map(product => {
      const option = element('option',`${product.name} · ${money(product.priceCents)} · ${product.stock} left`);
      option.value = product.id;
      return option;
    }));
    if (previous) $('product').value = previous;
    $('orders').replaceChildren(...(orders.length ? orders.map(o => orderCard(o,products)) : [element('p','No orders yet. Your first experiment starts on the left.','hint')]));
    if (session.operator) {
      const metrics = await api('/api/admin/metrics');
      $('metrics').hidden = false;
      $('metrics').replaceChildren(...Object.entries(metrics).map(([label,value]) => {
        const box = element('div',undefined,'metric');
        box.append(element('strong',value),element('span',label === 'unsent' ? 'unsent jobs' : label));
        return box;
      }));
    }
    await timeline();
  } catch(error) { notice(error.message,true); }
  finally { refreshing = false; }
}
$('new-key').onclick = newKey;
$('refresh').onclick = refresh;
$('scenario').onchange = () => {
  const explanations = {
    SUCCESS:'One charge, one completed order.',
    DECLINE:'The order fails and its reserved stock comes back.',
    TIMEOUT_ONCE:'The first attempt fails. A background job tries again.',
    ALWAYS_TIMEOUT:'After three timeouts, the order becomes DEAD. Stock stays held for operator review.',
    CHARGE_THEN_TIMEOUT:'A receipt is saved, but the reply is lost. The retry should find the receipt, not charge again.'
  };
  $('scenario-help').textContent = explanations[$('scenario').value];
};
$('checkout').onsubmit = async event => {
  event.preventDefault();
  $('submit').disabled = true;
  try {
    const order = await api('/api/orders','POST',{
      productId:Number($('product').value),quantity:Number($('quantity').value),scenario:$('scenario').value
    },{'Idempotency-Key':$('key').value});
    selectedOrder = order.id;
    notice(`Order ${order.id.slice(0,8)} returned. Same key + same details returns this same order.`);
    await refresh();
  } catch(error) { notice(error.message,true); }
  finally { $('submit').disabled = false; }
};
$('logout').onclick = async () => {
  await fetch('/logout',{method:'POST',headers:{[session.csrfHeader]:session.csrfToken}});
  location.href = '/login';
};
async function start() {
  try {
    session = await api('/api/session');
    $('identity').textContent = `${session.username} · ${session.operator ? 'operator' : 'shopper'}`;
    $('mode').textContent = session.transport === 'rabbit' ? 'RabbitMQ + PostgreSQL' : 'Local demo';
    newKey();
    await refresh();
    setInterval(refresh,2000);
  } catch(error) { notice(error.message,true); }
}
start();
