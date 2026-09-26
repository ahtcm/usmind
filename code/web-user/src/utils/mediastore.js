// USMIND 媒体存储 (后端 API 版)
// 上传文件到后端, 返回 URL; 节点数据里直接存 URL, 无需 blob 物化/还原
import { fileApi } from '../api/file'

// 上传文件到后端, 返回可访问 URL
export async function uploadMedia(file) {
  const res = await fileApi.upload(file)
  return res.url
}

// 向后兼容: 旧 IndexedDB 接口名保留, 但直接走后端上传
export async function saveMedia(file) {
  return uploadMedia(file)
}

export async function mediaToUrl(ref) {
  // 已经是 http/https/blob:/data: URL, 直接返回
  if (typeof ref === 'string' && /^(https?:|blob:|data:|\/)/.test(ref)) return ref
  return ref
}

export function isMediaRef(v) {
  return false // 后端版直接存 URL, 无 media:// 引用
}

export async function materializeNodeData(node) {
  // 后端版 URL 直接可用, 无需物化
  return
}

export async function dematerializeNodeData(node) {
  // 后端版 URL 直接可用, 无需还原
  return
}

export function blobUrlToMediaRef(url) {
  return null
}

export async function replaceMedia(ref, file) {
  return uploadMedia(file)
}

// 计算图片显示尺寸
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
