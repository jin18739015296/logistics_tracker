#!/usr/bin/env node
/**
 * 模拟配送员沿「起点 → 终点」持续上报 GPS，用于无真机/无真骑手时的联调与演示。
 *
 * 要求：
 * - 后端已启动，且 context-path 为 /api（与 application.yml 一致）
 * - 使用「配送员」账号登录（role=courier），该账号在系统中已被指派到 orderId 对应订单
 *
 * 用法示例（Windows PowerShell）：
 *   $env:API_BASE="http://localhost:8082/api"
 *   $env:COURIER_USER="你的配送员用户名"
 *   $env:COURIER_PASS="密码"
 *   $env:ORDER_ID="1"
 *   $env:FROM="22.5405,113.9345"
 *   $env:TO="22.5500,113.9500"
 *   node scripts/simulate-courier-track.mjs
 *
 * 或从订单自动取寄件/收件经纬度（推荐：已点「开始配送」后演示轨迹）：
 *   node scripts/simulate-courier-track.mjs --api http://localhost:8082/api --user courier1 --pass 123456 --order 5 --from-order
 *
 * 参数说明：
 *   --api      API 根路径，默认 http://localhost:8082/api
 *   --user     配送员登录用户名
 *   --pass     密码
 *   --order    订单 ID（与后台该配送员可配送的订单一致）
 *   --from     起点 "纬度,经度"
 *   --to       终点 "纬度,经度"
 *   --straight-step-m 仅「路网失败走直线兜底」时，沿大圆每多少米一个点（默认 40）。路网成功时点数完全由高德 polyline 决定，不做人为压缩。
 *   --interval 相邻两次上报间隔毫秒；未指定 --duration-min / --auto-duration 时默认 3000
 *   --duration-min 演示「从起点到终点」的总时长（分钟），自动均分到各段间隔（与 --interval 二选一，本参数优先）
 *   --auto-duration 按寄件→收件直线距离估算演示总时长（约 0.4 分钟/km，10～180 分钟），适合长途录屏
 *   --no-confirm-delivery 仅上报轨迹，到达后不调用确认送达（默认会确认，并使用终点经纬度）
 *   --from-order 登录后请求 GET /orders/{id}，用订单里的寄件、收件经纬度作为起点/终点（不必再传 --from --to）
 *   --mode     路网策略：riding（默认，v4 骑行）、driving（驾车）、walking（步行）
 *   --jitter   GPS 抖动范围（米），默认 5。设为 0 则使用精确坐标无抖动
 *   --base-speed 基础速度（m/s），默认按 mode：riding/bicycling=4.5, driving=8.3, walking=1.4
 *
 * 路网：脚本内已写死与 App 相同的高德 Web 服务 Key；失败时按 --straight-step-m 沿大圆加密直线点。
 */

const DEFAULT_API = 'http://localhost:8082/api';

/** 与前端 `mobileApp/src/components/AMapPicker.js` 中 `AMAP_WEBSERVICE_KEY` 一致（Web 服务 Key，用于路径规划 REST）。 */
const AMAP_WEBSERVICE_KEY = 'f2d8380599ed7d4473ac5b2cc0503a3e';

function parseArgs(argv) {
  const out = {
    api: process.env.API_BASE || DEFAULT_API,
    user: process.env.COURIER_USER || '',
    pass: process.env.COURIER_PASS || '',
    orderId: process.env.ORDER_ID || '',
    from: process.env.FROM || '',
    to: process.env.TO || '',
    fromOrder:
      process.env.FROM_ORDER === '1' ||
      process.env.FROM_ORDER === 'true',
    straightStepMeters: parseFloat(process.env.STRAIGHT_STEP_METERS || '40'),
    interval: parseInt(process.env.INTERVAL_MS || '3000', 10),
    mode: (process.env.AMAP_MODE || 'riding').toLowerCase(),
    durationMin: (() => {
      const v = process.env.DEMO_DURATION_MIN;
      if (v === undefined || v === '') return null;
      const n = parseFloat(v);
      return Number.isNaN(n) ? null : n;
    })(),
    autoDuration:
      process.env.AUTO_DEMO_DURATION === '1' ||
      process.env.AUTO_DEMO_DURATION === 'true',
    noConfirmDelivery:
      process.env.NO_CONFIRM_DELIVERY === '1' ||
      process.env.NO_CONFIRM_DELIVERY === 'true',
    jitterMeters: parseFloat(process.env.JITTER_METERS || '5'),
    baseSpeedMps: (() => {
      const v = process.env.BASE_SPEED_MPS;
      if (v === undefined || v === '') return null;
      const n = parseFloat(v);
      return Number.isNaN(n) ? null : n;
    })(),
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--api' && argv[i + 1]) out.api = argv[++i].replace(/\/$/, '');
    else if (a === '--user' && argv[i + 1]) out.user = argv[++i];
    else if (a === '--pass' && argv[i + 1]) out.pass = argv[++i];
    else if (a === '--order' && argv[i + 1]) out.orderId = argv[++i];
    else if (a === '--from' && argv[i + 1]) out.from = argv[++i];
    else if (a === '--to' && argv[i + 1]) out.to = argv[++i];
    else if (a === '--from-order') out.fromOrder = true;
    else if (a === '--straight-step-m' && argv[i + 1])
      out.straightStepMeters = parseFloat(argv[++i]);
    else if (a === '--interval' && argv[i + 1]) out.interval = parseInt(argv[++i], 10);
    else if (a === '--duration-min' && argv[i + 1]) out.durationMin = parseFloat(argv[++i]);
    else if (a === '--auto-duration') out.autoDuration = true;
    else if (a === '--no-confirm-delivery') out.noConfirmDelivery = true;
    else if (a === '--mode' && argv[i + 1]) out.mode = String(argv[++i]).toLowerCase();
    else if (a === '--jitter' && argv[i + 1]) out.jitterMeters = parseFloat(argv[++i]);
    else if (a === '--base-speed' && argv[i + 1]) out.baseSpeedMps = parseFloat(argv[++i]);
  }
  return out;
}

function parseLatLng(s) {
  const parts = String(s).split(',').map((x) => x.trim());
  if (parts.length !== 2) throw new Error(`坐标格式错误，应为 "纬度,经度": ${s}`);
  const lat = parseFloat(parts[0]);
  const lng = parseFloat(parts[1]);
  if (Number.isNaN(lat) || Number.isNaN(lng)) throw new Error(`坐标解析失败: ${s}`);
  return { lat, lng };
}

/** 球面大圆距离，单位米 */
function haversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const φ1 = toRad(lat1);
  const φ2 = toRad(lat2);
  const Δφ = toRad(lat2 - lat1);
  const Δλ = toRad(lon2 - lon1);
  const a =
    Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
    Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * 从点1看点2 的方位角，度，顺时针从正北 0~360
 */
function bearingDegrees(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const toDeg = (r) => (r * 180) / Math.PI;
  const φ1 = toRad(lat1);
  const φ2 = toRad(lat2);
  const Δλ = toRad(lon2 - lon1);
  const y = Math.sin(Δλ) * Math.cos(φ2);
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
  const θ = Math.atan2(y, x);
  return (toDeg(θ) + 360) % 360;
}

/** 地球上沿方位角前进 distanceM 米后的坐标（WGS84 球面近似） */
function destinationPointMeters(lat1, lon1, bearingDeg, distanceM) {
  const R = 6371000;
  const δ = distanceM / R;
  const θ = (bearingDeg * Math.PI) / 180;
  const φ1 = (lat1 * Math.PI) / 180;
  const λ1 = (lon1 * Math.PI) / 180;
  const sinφ1 = Math.sin(φ1);
  const cosφ1 = Math.cos(φ1);
  const sinδ = Math.sin(δ);
  const cosδ = Math.cos(δ);
  const sinθ = Math.sin(θ);
  const cosθ = Math.cos(θ);
  const sinφ2 = sinφ1 * cosδ + cosφ1 * sinδ * cosθ;
  const φ2 = Math.asin(sinφ2);
  const y = sinθ * sinδ * cosφ1;
  const x = cosδ - sinφ1 * sinφ2;
  const λ2 = λ1 + Math.atan2(y, x);
  const lat2 = (φ2 * 180) / Math.PI;
  const lon2 = ((((λ2 * 180) / Math.PI + 540) % 360) + 360) % 360 - 180;
  return { lat: lat2, lng: lon2 };
}

/**
 * 路网不可用：沿起点→终点大圆按固定弧长间距生成点，点数随实际距离增长（不做人为上限）。
 */
function buildStraightPathGeodesic(start, end, stepMeters) {
  const step = Math.max(5, Number(stepMeters) || 40);
  const D = haversineMeters(start.lat, start.lng, end.lat, end.lng);
  if (D < 0.5) {
    return dedupeConsecutiveLatLng([
      { latitude: start.lat, longitude: start.lng },
      { latitude: end.lat, longitude: end.lng },
    ]);
  }
  const nSeg = Math.max(1, Math.ceil(D / step));
  const brg = bearingDegrees(start.lat, start.lng, end.lat, end.lng);
  const pts = [];
  for (let i = 0; i <= nSeg; i++) {
    const dist = (i / nSeg) * D;
    const p = destinationPointMeters(start.lat, start.lng, brg, dist);
    pts.push({ latitude: p.lat, longitude: p.lng });
  }
  pts[pts.length - 1] = { latitude: end.lat, longitude: end.lng };
  return dedupeConsecutiveLatLng(pts);
}

function dedupeConsecutiveLatLng(pts) {
  const out = [];
  for (const p of pts) {
    const last = out[out.length - 1];
    if (
      !last ||
      last.latitude !== p.latitude ||
      last.longitude !== p.longitude
    ) {
      out.push(p);
    }
  }
  return out;
}

/**
 * 路径压缩：对近似共线的中间点进行稀疏化，保留起点、终点和明显拐点。
 * minSegMeters: 相邻保留点之间的最小直线距离（米），默认 80。
 */
function compressPath(pts, minSegMeters) {
  if (!pts || pts.length <= 2) return pts;
  const minM = Math.max(10, Number(minSegMeters) || 80);
  const out = [pts[0]];
  for (let i = 1; i < pts.length - 1; i++) {
    const last = out[out.length - 1];
    const d = haversineMeters(last.latitude, last.longitude, pts[i].latitude, pts[i].longitude);
    if (d >= minM) {
      out.push(pts[i]);
    }
  }
  const lastPt = pts[pts.length - 1];
  const dLast = haversineMeters(out[out.length - 1].latitude, out[out.length - 1].longitude, lastPt.latitude, lastPt.longitude);
  if (dLast > 0) {
    out.push(lastPt);
  }
  return out.length >= 2 ? out : pts;
}

/**
 * 请求高德 REST 路径规划，解析 steps[].polyline（lng,lat;...）
 * riding 走 v4 bicycling；driving/walking 走 v3
 */
async function fetchAmapRoadPath(start, end, key, mode) {
  const origin = `${start.lng},${start.lat}`;
  const dest = `${end.lng},${end.lat}`;
  let url;
  if (mode === 'riding' || mode === 'bicycling') {
    url = `https://restapi.amap.com/v4/direction/bicycling?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(dest)}&key=${encodeURIComponent(key)}`;
  } else if (mode === 'driving' || mode === 'walking') {
    url = `https://restapi.amap.com/v3/direction/${mode}?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(dest)}&key=${encodeURIComponent(key)}&extensions=base`;
  } else {
    throw new Error(`--mode 无效: ${mode}（可选 riding、bicycling、driving、walking）`);
  }
  const res = await fetch(url);
  const json = await res.json();
  let steps = null;
  if (mode === 'riding' || mode === 'bicycling') {
    if (Number(json.errcode) === 0 && json.data?.paths?.[0]?.steps) {
      steps = json.data.paths[0].steps;
    } else {
      throw new Error(json.errdetail || json.errmsg || JSON.stringify(json));
    }
  } else if (json.status === '1' && json.route?.paths?.[0]?.steps) {
    steps = json.route.paths[0].steps;
  } else {
    throw new Error(json.info || JSON.stringify(json));
  }
  const pathPoints = [];
  for (const step of steps) {
    const poly = step.polyline;
    if (!poly || typeof poly !== 'string') continue;
    for (const point of poly.split(';')) {
      const pair = point.split(',');
      if (pair.length < 2) continue;
      const lng = parseFloat(pair[0]);
      const lat = parseFloat(pair[1]);
      if (Number.isNaN(lat) || Number.isNaN(lng)) continue;
      pathPoints.push({ latitude: lat, longitude: lng });
    }
  }
  if (pathPoints.length < 2) {
    throw new Error('路网解析后有效点数不足 2');
  }
  return pathPoints;
}

/** 从订单详情取寄件→收件坐标（需该账号有权查看此单，配送员本人即可） */
async function fetchOrderEndpoints(apiBase, token, orderId) {
  const url = `${apiBase}/orders/${orderId}`;
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const json = await res.json();
  if (!res.ok) {
    throw new Error(`拉取订单 HTTP ${res.status}: ${JSON.stringify(json)}`);
  }
  if (json.code !== 0 || !json.data) {
    throw new Error(`拉取订单失败: ${json.message || JSON.stringify(json)}`);
  }
  const d = json.data;
  const slat = d.senderLatitude;
  const slng = d.senderLongitude;
  const rlat = d.receiverLatitude;
  const rlng = d.receiverLongitude;
  if (slat == null || slng == null || rlat == null || rlng == null) {
    throw new Error(
      '订单缺少寄件或收件经纬度（senderLatitude 等为空）。请先在下单/后台填写带坐标的地址。'
    );
  }
  const addrParts = [
    d.receiverProvince,
    d.receiverCity,
    d.receiverDistrict,
    d.receiverDetailAddress,
  ].filter((x) => x != null && String(x).trim() !== '');
  const receiverAddressText =
    (addrParts.length ? addrParts.join('') : null) ||
    (d.receiverAddress != null && String(d.receiverAddress).trim() !== ''
      ? String(d.receiverAddress).trim()
      : null);

  return {
    from: `${Number(slat)},${Number(slng)}`,
    to: `${Number(rlat)},${Number(rlng)}`,
    receiverAddressText,
  };
}

async function login(apiBase, username, password) {
  const url = `${apiBase}/auth/login`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json = await res.json();
  if (!res.ok) {
    throw new Error(`登录 HTTP ${res.status}: ${JSON.stringify(json)}`);
  }
  if (json.code !== 0 || !json.data?.accessToken) {
    throw new Error(`登录失败: ${json.message || JSON.stringify(json)}`);
  }
  return json.data.accessToken;
}

async function uploadLocation(apiBase, token, body) {
  const url = `${apiBase}/tracking/upload`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  const json = await res.json();
  if (!res.ok) {
    throw new Error(`上传 HTTP ${res.status}: ${JSON.stringify(json)}`);
  }
  if (json.code !== 0) {
    throw new Error(`上传失败: ${json.message || JSON.stringify(json)}`);
  }
  return json.data;
}

/** 与 App `courierApi.confirmDelivery` 一致：送达确认写入终点经纬度 */
async function confirmDeliveryAtEndpoint(apiBase, token, orderId, location, latitude, longitude) {
  const url = `${apiBase}/courier/orders/${orderId}/confirm-delivery`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      location,
      latitude,
      longitude,
      signatureImageUrl: null,
    }),
  });
  const json = await res.json();
  if (!res.ok) {
    throw new Error(`确认送达 HTTP ${res.status}: ${JSON.stringify(json)}`);
  }
  if (json.code !== 0) {
    throw new Error(`确认送达失败: ${json.message || JSON.stringify(json)}`);
  }
  return json.data;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/** 模拟真实 GPS 抖动：在坐标上叠加随机偏移（米级） */
function addGpsJitter(lat, lng, jitterMeters) {
  // 1 纬度 ≈ 111km，1 经度 ≈ 111km * cos(纬度)
  const latOffset = (Math.random() - 0.5) * 2 * (jitterMeters / 111000);
  const lngOffset =
    (Math.random() - 0.5) * 2 * (jitterMeters / (111000 * Math.cos((lat * Math.PI) / 180)));
  return { lat: lat + latOffset, lng: lng + lngOffset };
}

/** 模拟真实速度：基于路段距离和基础速度，叠加随机波动与偶尔停留 */
function simulateRealSpeed(segM, intervalMs, baseSpeedMps) {
  if (segM <= 0 || intervalMs <= 0) return 0;
  // 基础速度（m/s），骑行约 3-6 m/s
  let speed = baseSpeedMps;
  // 随机波动 ±30%
  speed *= 0.7 + Math.random() * 0.6;
  // 10% 概率遇到红灯/等待，速度接近 0
  if (Math.random() < 0.1) {
    speed *= 0.05 + Math.random() * 0.1;
  }
  // 根据实际路段距离校准（避免速度与实际位移严重不符）
  const expectedSpeed = segM / (intervalMs / 1000);
  // 如果校准速度和模拟速度差异过大，取加权平均
  speed = speed * 0.6 + expectedSpeed * 0.4;
  return Math.max(0, speed);
}

/** 模拟定位精度：开阔地带 5-10m，楼宇/路口 15-30m */
function simulateAccuracy() {
  const r = Math.random();
  if (r < 0.6) return 5 + Math.floor(Math.random() * 6); // 60% 精度较好 5-10
  if (r < 0.9) return 10 + Math.floor(Math.random() * 11); // 30% 一般 10-20
  return 20 + Math.floor(Math.random() * 21); // 10% 较差 20-40
}

async function main() {
  const opt = parseArgs(process.argv);
  if (!Number.isFinite(opt.straightStepMeters) || opt.straightStepMeters < 5) {
    opt.straightStepMeters = 40;
  }
  if (!opt.user || !opt.pass) {
    console.error('请设置 --user / --pass 或环境变量 COURIER_USER、COURIER_PASS');
    process.exit(1);
  }
  if (!opt.orderId) {
    console.error('请设置 --order 或环境变量 ORDER_ID');
    process.exit(1);
  }

  const orderId = parseInt(String(opt.orderId), 10);
  if (Number.isNaN(orderId)) {
    console.error('ORDER_ID 无效');
    process.exit(1);
  }

  const token = await login(opt.api, opt.user, opt.pass);

  let start;
  let end;
  /** 从订单接口拼出的收件地址文案，用于确认送达的 location 字段 */
  let receiverAddressText = null;

  if (opt.fromOrder) {
    const ep = await fetchOrderEndpoints(opt.api, token, orderId);
    receiverAddressText = ep.receiverAddressText;
    console.log('已按订单坐标: 寄件 → 收件');
    console.log('  FROM', ep.from, '\n  TO  ', ep.to, '\n');
    start = parseLatLng(ep.from);
    end = parseLatLng(ep.to);
  } else {
    if (!opt.from || !opt.to) {
      console.error(
        '请设置 --from / --to（纬度,经度），或使用 --from-order 从订单读取寄件/收件坐标'
      );
      process.exit(1);
    }
    start = parseLatLng(opt.from);
    end = parseLatLng(opt.to);
  }

  let path;
  let pathSource = '直线兜底';
  try {
    const raw = await fetchAmapRoadPath(start, end, AMAP_WEBSERVICE_KEY, opt.mode);
    path = dedupeConsecutiveLatLng(raw);
    pathSource = `高德路网 (${opt.mode})，全量折线顶点`;
  } catch (e) {
    console.warn('高德路径规划不可用，已回退大圆直线密化:', e.message || e);
    path = buildStraightPathGeodesic(start, end, opt.straightStepMeters);
    pathSource = `直线兜底（约每 ${opt.straightStepMeters} m 一点）`;
  }
  if (path.length < 2) {
    path = [
      { latitude: start.lat, longitude: start.lng },
      { latitude: end.lat, longitude: end.lng },
    ];
  }

  const distM = haversineMeters(start.lat, start.lng, end.lat, end.lng);
  const distKm = distM / 1000;

  let durationMin = opt.durationMin;
  if (durationMin != null && (Number.isNaN(durationMin) || durationMin <= 0)) {
    durationMin = null;
  }
  if (
    durationMin == null &&
    opt.autoDuration
  ) {
    durationMin = Math.min(180, Math.max(10, Math.round(distKm * 0.4)));
    console.log(
      `--auto-duration：直线距离约 ${distKm.toFixed(1)} km → 演示总时长约 ${durationMin} 分钟（≈0.4 分/km，上限 180 分）`
    );
  }

  let intervalMs = opt.interval;
  if (durationMin != null) {
    const segments = Math.max(1, path.length - 1);
    intervalMs = Math.max(200, Math.floor((durationMin * 60 * 1000) / segments));
    console.log(
      `按 ${durationMin} 分钟总时长均分：相邻上报间隔 ${intervalMs} ms（共 ${path.length} 个点）`
    );
  }

  // 为加速演示，对高密度点进行采样压缩（保留起点、终点及拐点，直线路径稀疏化）
  const compressedPath = compressPath(path, 80);
  if (compressedPath.length < path.length) {
    console.log(`路径压缩: ${path.length} → ${compressedPath.length} 点（稀疏化直线路段）`);
    path = compressedPath;
  }

  console.log('API:', opt.api);
  console.log('订单 ID:', orderId);
  console.log('路径来源:', pathSource);
  console.log('路径点数:', path.length, ' 基准间隔:', intervalMs, 'ms');
  console.log('直线距离约:', distM.toFixed(0), 'm\n');
  console.log('开始模拟上报…\n');

  // 基础速度配置（m/s）：riding=4.5(约16km/h), driving=8.3(约30km/h), walking=1.4(约5km/h)
  const baseSpeedByMode = { riding: 4.5, bicycling: 4.5, driving: 8.3, walking: 1.4 };
  const baseSpeed = opt.baseSpeedMps || baseSpeedByMode[opt.mode] || 4.5;
  const jitterMeters = Number.isFinite(opt.jitterMeters) && opt.jitterMeters >= 0 ? opt.jitterMeters : 5;

  for (let i = 0; i < path.length; i++) {
    const p = path[i];
    const prev = i > 0 ? path[i - 1] : null;
    const next = i < path.length - 1 ? path[i + 1] : null;

    // 航向角：有下一段用「当前→下一」，最后一点用「上一→当前」
    let dir;
    if (next) {
      dir = bearingDegrees(p.latitude, p.longitude, next.latitude, next.longitude);
    } else if (prev) {
      dir = bearingDegrees(prev.latitude, prev.longitude, p.latitude, p.longitude);
    } else {
      dir = bearingDegrees(start.lat, start.lng, end.lat, end.lng);
    }

    const segM = next
      ? haversineMeters(p.latitude, p.longitude, next.latitude, next.longitude)
      : 0;

    // 模拟真实速度和 GPS 抖动
    const speed = simulateRealSpeed(segM, intervalMs, baseSpeed);
    const jittered = jitterMeters > 0
      ? addGpsJitter(p.latitude, p.longitude, jitterMeters)
      : { lat: p.latitude, lng: p.longitude };
    const accuracy = simulateAccuracy();

    const payload = {
      orderId,
      latitude: Number(jittered.lat.toFixed(7)),
      longitude: Number(jittered.lng.toFixed(7)),
      accuracy,
      speed: Number(speed.toFixed(2)),
      direction: Number(dir.toFixed(1)),
    };

    process.stdout.write(`[${i + 1}/${path.length}] lat=${payload.latitude} lng=${payload.longitude} 方向≈${payload.direction}° 速度≈${payload.speed}m/s 精度≈${payload.accuracy}m `);
    try {
      const data = await uploadLocation(opt.api, token, payload);
      const eta = data?.remainingTime != null ? ` ETA ${data.remainingTime}s` : '';
      console.log('OK' + eta);
    } catch (e) {
      console.log('FAIL');
      console.error(e.message);
      process.exit(1);
    }
    if (i < path.length - 1) {
      await sleep(intervalMs);
    }
  }

  if (!opt.noConfirmDelivery) {
    const last = path[path.length - 1];
    const loc =
      receiverAddressText ||
      `快件送达（模拟终点 ${Number(last.latitude).toFixed(5)},${Number(last.longitude).toFixed(5)}）`;
    console.log('\n正在调用确认送达（终点坐标与最后一次上报一致）…');
    try {
      await confirmDeliveryAtEndpoint(
        opt.api,
        token,
        orderId,
        loc,
        Number(last.latitude.toFixed(7)),
        Number(last.longitude.toFixed(7))
      );
      console.log('确认送达成功。');
    } catch (e) {
      console.error(e.message);
      console.error(
        '提示：订单须处于「运输中」或「已到达收货地」。请先在同账号 App 内开始运输/配送后再跑脚本。'
      );
      process.exit(1);
    }
  } else {
    console.log('\n已跳过确认送达（--no-confirm-delivery）。');
  }

  console.log('\n模拟结束。可在用户端订单追踪页观察地图与 WebSocket 推送。');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
