/** The first image in pasted or dropped data, if any (F-13). */
export function imageIn(data: DataTransfer | null): File | null {
  if (!data) return null
  for (const item of Array.from(data.items ?? [])) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) return file
    }
  }
  return Array.from(data.files ?? []).find((file) => file.type.startsWith('image/')) ?? null
}

/** [text] as an http(s) link, or null: a pasted link may have spaces or line breaks around it. */
export function linkIn(text: string): string | null {
  const trimmed = text.trim()
  if (!/^https?:\/\/\S+$/i.test(trimmed)) return null
  try {
    return new URL(trimmed).href
  } catch {
    return null
  }
}

/** Whether a paste on [target] is meant for that field, rather than for the page. */
export function isTextField(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || target instanceof HTMLTextAreaElement || (target instanceof HTMLInputElement && !['checkbox', 'radio', 'button', 'submit', 'file'].includes(target.type))
}
