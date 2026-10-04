import type { FoodImageData } from '../api/types.gen'

const FULL_SIZE = 1280
const THUMBNAIL_SIZE = 192
const BLUR_SIZE = 6
const TYPE = 'image/jpeg'

function canvasFor(width: number, height: number) {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')!
  // JPEG has no transparency; transparent pixels become white rather than black.
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, width, height)
  context.imageSmoothingQuality = 'high'
  return { canvas, context }
}

function toBase64(canvas: HTMLCanvasElement, quality: number): Promise<string> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) return reject(new Error('encode failed'))
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]*,/, ''))
        reader.onerror = () => reject(reader.error)
        reader.readAsDataURL(blob)
      },
      TYPE,
      quality,
    )
  })
}

/**
 * Draws [image] whole and centred in a square canvas. The space around a non-square image is filled
 * with a blurred, enlarged copy of it, which suits light and dark mode better than a solid colour.
 */
function squareThumbnail(image: CanvasImageSource, width: number, height: number) {
  const thumb = canvasFor(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
  // Blur without canvas filters (not in every browser): shrink the cropped centre to a few pixels,
  // then scale that back up with smoothing.
  const side = Math.min(width, height)
  const tiny = canvasFor(BLUR_SIZE, BLUR_SIZE)
  tiny.context.drawImage(image, (width - side) / 2, (height - side) / 2, side, side, 0, 0, BLUR_SIZE, BLUR_SIZE)
  thumb.context.drawImage(tiny.canvas, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)

  const scale = THUMBNAIL_SIZE / Math.max(width, height)
  const w = width * scale
  const h = height * scale
  thumb.context.drawImage(image, (THUMBNAIL_SIZE - w) / 2, (THUMBNAIL_SIZE - h) / 2, w, h)
  return thumb.canvas
}

/**
 * Shrinks a photo before upload (F-13): the image to at most 1280 px on its longest side, plus a
 * square thumbnail of the whole image. Large phone photos become a few hundred KB.
 */
export async function preparePhoto(file: Blob): Promise<FoodImageData> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  try {
    const scale = Math.min(1, FULL_SIZE / Math.max(bitmap.width, bitmap.height))
    const full = canvasFor(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale))
    full.context.drawImage(bitmap, 0, 0, full.canvas.width, full.canvas.height)
    const thumb = squareThumbnail(bitmap, bitmap.width, bitmap.height)
    return { contentType: TYPE, image: await toBase64(full.canvas, 0.85), thumbnail: await toBase64(thumb, 0.8) }
  } finally {
    bitmap.close()
  }
}

/**
 * A new thumbnail for a stored photo, which is kept as it is. Thumbnails made before they showed the
 * whole image were cropped to a square.
 */
export async function redoThumbnail(stored: FoodImageData): Promise<FoodImageData> {
  const bytes = Uint8Array.from(atob(stored.image), (c) => c.charCodeAt(0))
  const bitmap = await createImageBitmap(new Blob([bytes], { type: stored.contentType }))
  try {
    const thumb = squareThumbnail(bitmap, bitmap.width, bitmap.height)
    return { ...stored, thumbnail: await toBase64(thumb, 0.8) }
  } finally {
    bitmap.close()
  }
}
