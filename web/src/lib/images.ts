import type { FoodImageData } from '../api/types.gen'

const FULL_SIZE = 1280
const THUMBNAIL_SIZE = 192
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
 * Shrinks a photo before upload (F-13): the image to at most 1280 px on its longest side, plus a
 * square thumbnail cropped from the centre. Large phone photos become a few hundred KB.
 */
export async function preparePhoto(file: Blob): Promise<FoodImageData> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  try {
    const scale = Math.min(1, FULL_SIZE / Math.max(bitmap.width, bitmap.height))
    const full = canvasFor(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale))
    full.context.drawImage(bitmap, 0, 0, full.canvas.width, full.canvas.height)

    const side = Math.min(bitmap.width, bitmap.height)
    const thumb = canvasFor(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
    thumb.context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)

    return { contentType: TYPE, image: await toBase64(full.canvas, 0.85), thumbnail: await toBase64(thumb.canvas, 0.8) }
  } finally {
    bitmap.close()
  }
}
