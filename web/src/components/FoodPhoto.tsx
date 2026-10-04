import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import * as endpoints from '../api/endpoints'
import { useApiMutation } from '../api/queries'
import type { FoodDto, FoodImageData } from '../api/types.gen'
import { preparePhoto } from '../lib/images'
import { imageIn, isTextField, linkIn } from '../lib/paste'
import { ConfirmButton, ErrorText } from './ui'

/** A food's photo as a data URL; versioned, so once loaded it never needs refetching (F-13). */
function useFoodImage(foodId: number, version: number | null, size: 'full' | 'thumbnail') {
  return useQuery({
    queryKey: ['images', foodId, version, size],
    queryFn: () => endpoints.foodImage(foodId, version!, size),
    enabled: version !== null,
    staleTime: Infinity,
    gcTime: 30 * 60_000,
  })
}

/**
 * A small square photo of a food for lists and pickers. Without a photo it shows the food's initial,
 * so names stay aligned. Decorative: the food's name is always shown next to it.
 */
export function FoodThumb({ foodId, version, name, size = 'small' }: { foodId: number; version: number | null; name: string; size?: 'small' | 'medium' }) {
  const image = useFoodImage(foodId, version, 'thumbnail')
  if (image.data) return <img className={`food-thumb ${size}`} src={image.data} alt="" loading="lazy" />
  return (
    <span className={`food-thumb placeholder ${size}`} aria-hidden="true">
      {name.trim().charAt(0).toUpperCase()}
    </span>
  )
}

/**
 * The food page's photo, with upload, replace and remove (F-13). A photo can also be pasted, as an image or
 * a link to one, into the field below it or anywhere on the page outside other fields; a link is
 * downloaded by the server, and either is then shrunk and uploaded like a chosen file.
 */
export function FoodPhotoEditor({ food }: { food: FoodDto }) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const image = useFoodImage(food.id, food.imageVersion, 'full')
  const [preparing, setPreparing] = useState(false)
  const [readError, setReadError] = useState<string | null>(null)
  const [link, setLink] = useState('')
  const [linkError, setLinkError] = useState<unknown>(null)
  const upload = useApiMutation((data: FoodImageData) => endpoints.setFoodImage(food.id, data))
  const remove = useApiMutation(() => endpoints.deleteFoodImage(food.id))
  const busy = preparing || upload.isPending || remove.isPending

  async function setPhoto(file: Blob | undefined) {
    if (!file) return
    setReadError(null)
    setLinkError(null)
    setPreparing(true)
    try {
      const data = await preparePhoto(file).catch(() => null)
      if (!data) return setReadError(t('photo.unreadable'))
      // Server errors are shown by ErrorText below.
      await upload.mutateAsync(data).catch(() => undefined)
    } finally {
      setPreparing(false)
      if (input.current) input.current.value = ''
    }
  }

  async function photoFromLink(url: string) {
    setReadError(null)
    setLinkError(null)
    setPreparing(true)
    let blob: Blob
    try {
      blob = await endpoints.fetchPhotoLink(url)
    } catch (e) {
      setLinkError(e)
      return
    } finally {
      setPreparing(false)
    }
    await setPhoto(blob)
    setLink('')
  }

  /** Uses a pasted image or link; false if the paste holds neither. */
  function paste(data: DataTransfer | null): boolean {
    const pasted = imageIn(data)
    if (pasted) {
      void setPhoto(pasted)
      return true
    }
    const url = linkIn(data?.getData('text') ?? '')
    if (!url) return false
    setLink(url)
    void photoFromLink(url)
    return true
  }

  // Pasting anywhere on the page, outside a field, also sets the photo.
  const pasteRef = useRef(paste)
  const busyRef = useRef(busy)
  useEffect(() => {
    pasteRef.current = paste
    busyRef.current = busy
  })
  useEffect(() => {
    function onPaste(event: ClipboardEvent) {
      if (busyRef.current || isTextField(event.target)) return
      if (pasteRef.current(event.clipboardData)) event.preventDefault()
    }
    document.addEventListener('paste', onPaste)
    return () => document.removeEventListener('paste', onPaste)
  }, [])

  return (
    <div className="photo-editor">
      {food.imageVersion !== null ? (
        image.data ? (
          // The whole photo in a square, with a blurred copy of it filling the space around it.
          <div className="food-photo">
            <img className="food-photo-backdrop" src={image.data} alt="" aria-hidden="true" />
            <img className="food-photo-image" src={image.data} alt={t('photo.alt', { name: food.displayName })} />
          </div>
        ) : (
          <div className="food-photo placeholder" />
        )
      ) : (
        <div className="food-photo placeholder empty">
          <span>{t('photo.none')}</span>
        </div>
      )}
      <div className="form-actions">
        <button type="button" className="btn" disabled={busy} onClick={() => input.current?.click()}>
          {preparing || upload.isPending ? t('photo.uploading') : food.imageVersion !== null ? t('photo.replace') : t('photo.add')}
        </button>
        {food.imageVersion !== null && (
          <ConfirmButton disabled={busy} onConfirm={() => remove.mutate(undefined)}>
            {t('photo.remove')}
          </ConfirmButton>
        )}
        <input ref={input} type="file" accept="image/*" hidden aria-label={t('photo.add')} onChange={(e) => void setPhoto(e.target.files?.[0])} />
      </div>
      <div className="form-row photo-link">
        <input
          className="input grow"
          inputMode="url"
          aria-label={t('photo.paste')}
          placeholder={t('photo.paste')}
          value={link}
          disabled={busy}
          onChange={(e) => setLink(e.target.value)}
          onPaste={(e) => {
            if (paste(e.clipboardData)) e.preventDefault()
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              if (link.trim()) void photoFromLink(link.trim())
            }
          }}
        />
        <button type="button" className="btn" disabled={busy || !link.trim()} onClick={() => void photoFromLink(link.trim())}>
          {t('photo.fetch')}
        </button>
      </div>
      <p className="field-hint">{t('photo.hint')}</p>
      {readError && <p className="error-text">{readError}</p>}
      <ErrorText error={linkError ?? upload.error ?? remove.error} />
    </div>
  )
}
