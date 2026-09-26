// USMIND 媒体存储(IndexedDB)
// 节点内嵌图片/音频/视频/文件统一存 IndexedDB, 节点数据里只保存小引用 media://<id>
// 渲染前物化为 blob URL, 保存前还原为引用 —— localStorage 只存引用, 不爆容量
const DB_NAME = 'usmind-media'
const MEDIA_PREFIX = 'media://'
let dbPromise = null

function openDB() {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1)
      req.onupgradeneeded = () => {
        const db = req.result
        if (!db.objectStoreNames.contains('media')) {
          db.createObjectStore('media')
        }
      }
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error || new Error('IndexedDB 打开失败'))
    })
  }
  return dbPromise
}

// 保存媒体文件 -> media://<id>
export function saveMedia(file) {
  const id = 'm_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
  const ref = MEDIA_PREFIX + id
  return openDB().then(
    (db) =>
      new Promise((resolve, reject) => {
        const tx = db.transaction('media', 'readwrite')
        tx.objectStore('media').put(
          {
            blob: file,
            mime: file.type || 'application/octet-stream',
            name: file.name || 'file',
            size: file.size || 0
          },
          id
        )
        tx.oncomplete = () => resolve(ref)
        tx.onerror = () => reject(tx.error)
      })
  )
}

// 覆盖更新已有媒体(在线编辑附件保存用): 同 id 覆盖, 并失效 blob URL 缓存
export async function replaceMedia(ref, file) {
  const id = ref.slice(MEDIA_PREFIX.length)
  await openDB().then(
    (db) =>
      new Promise((resolve, reject) => {
        const tx = db.transaction('media', 'readwrite')
        tx.objectStore('media').put(
          {
            blob: file,
            mime: file.type || 'application/octet-stream',
            name: file.name || 'file',
            size: file.size || 0
          },
          id
        )
        tx.oncomplete = () => resolve()
        tx.onerror = () => reject(tx.error)
      })
  )
  const oldUrl = refToBlobUrl.get(ref)
  if (oldUrl) {
    URL.revokeObjectURL(oldUrl)
    blobUrlToRef.delete(oldUrl)
    refToBlobUrl.delete(ref)
  }
  return mediaToUrl(ref)
}

// media://<id> -> Blob
export function getMedia(ref) {
  const id = ref.slice(MEDIA_PREFIX.length)
  return openDB().then(
    (db) =>
      new Promise((resolve, reject) => {
        const req = db.transaction('media').objectStore('media').get(id)
        req.onsuccess = () => resolve(req.result ? req.result.blob : null)
        req.onerror = () => reject(req.error)
      })
  )
}

// blob URL 缓存与映射: 同一引用复用同一 blob URL, 保存时可还原回引用
const refToBlobUrl = new Map()
const blobUrlToRef = new Map()

export async function mediaToUrl(ref) {
  if (refToBlobUrl.has(ref)) return refToBlobUrl.get(ref)
  const blob = await getMedia(ref)
  if (!blob) return ref // 媒体缺失: 保留原引用(渲染为破图, 数据不丢)
  const url = URL.createObjectURL(blob)
  refToBlobUrl.set(ref, url)
  blobUrlToRef.set(url, ref)
  return url
}

export function isMediaRef(v) {
  return typeof v === 'string' && v.indexOf(MEDIA_PREFIX) === 0
}

export function blobUrlToMediaRef(url) {
  return blobUrlToRef.get(url) || null
}

// 计算图片显示尺寸(最长边 210, 适配节点框不过大)
export function imageSizeFor(url) {
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => {
      const w = img.naturalWidth || 1
      const h = img.naturalHeight || 1
      const max = 210
      const scale = Math.min(1, max / Math.max(w, h))
      resolve({ width: Math.max(1, Math.round(w * scale)), height: Math.max(1, Math.round(h * scale)), custom: true })
    }
    img.onerror = () => resolve({ width: 200, height: 120, custom: true })
    img.src = url
  })
}

// 物化富文本里的 media:// 引用 -> blob URL
async function materializeRichText(html) {
  const re = /(src|href)=["'](media:\/\/[^"']+)["']/g
  const subs = []
  let m
  while ((m = re.exec(html))) subs.push([m[0], m[1], m[2]])
  if (!subs.length) return html
  const replaced = []
  for (const [full, attr, ref] of subs) {
    replaced.push([full, attr + '="' + (await mediaToUrl(ref)) + '"'])
  }
  let out = html
  replaced.forEach(([f, r]) => {
    out = out.replace(f, r)
  })
  return out
}

// 物化整棵节点树: media:// -> blob URL
export async function materializeNodeData(node) {
  if (!node || !node.data) return
  const d = node.data
  if (isMediaRef(d.image)) {
    d.image = await mediaToUrl(d.image)
    if (!d.imageSize || !d.imageSize.custom) d.imageSize = await imageSizeFor(d.image)
  }
  if (isMediaRef(d.attachmentUrl)) d.attachmentUrl = await mediaToUrl(d.attachmentUrl)
  // 引擎富文本语义: richText=true 标志 + text 字段存 HTML; 兼容旧数据 richText 存 HTML 字符串
  if (typeof d.richText === 'string' && d.richText.indexOf(MEDIA_PREFIX) >= 0) {
    d.richText = await materializeRichText(d.richText)
  }
  if (d.richText === true && typeof d.text === 'string' && d.text.indexOf(MEDIA_PREFIX) >= 0) {
    d.text = await materializeRichText(d.text)
  }
  for (const c of node.children || []) await materializeNodeData(c)
}

// 还原整棵节点树: blob URL -> media:// (同步, 保存前调用)
export function dematerializeNodeData(node) {
  if (!node || !node.data) return
  const d = node.data
  if (typeof d.image === 'string' && d.image.indexOf('blob:') === 0) {
    d.image = blobUrlToMediaRef(d.image) || d.image
  }
  if (typeof d.attachmentUrl === 'string' && d.attachmentUrl.indexOf('blob:') === 0) {
    d.attachmentUrl = blobUrlToMediaRef(d.attachmentUrl) || d.attachmentUrl
  }
  if (typeof d.richText === 'string') {
    d.richText = d.richText.replace(/(src|href)=["']blob:[^"']+["']/g, (mm) => {
      const eq = mm.indexOf('="')
      const attr = mm.slice(0, eq)
      const url = mm.slice(eq + 2, -1)
      const ref = blobUrlToMediaRef(url)
      return ref ? attr + '="' + ref + '"' : mm
    })
  }
  if (d.richText === true && typeof d.text === 'string') {
    d.text = d.text.replace(/(src|href)=["']blob:[^"']+["']/g, (mm) => {
      const eq = mm.indexOf('="')
      const attr = mm.slice(0, eq)
      const url = mm.slice(eq + 2, -1)
      const ref = blobUrlToMediaRef(url)
      return ref ? attr + '="' + ref + '"' : mm
    })
  }
  for (const c of node.children || []) dematerializeNodeData(c)
}
