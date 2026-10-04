import { useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import * as endpoints from '../api/endpoints'
import { useFoods } from '../api/queries'
import { useQueryClient } from '@tanstack/react-query'
import { useDebounced } from '../lib/useDebounced'
import { errorMessage } from '../lib/errors'
import { FoodThumb } from './FoodPhoto'

export interface PickedFood {
  id: number
  name: string
}

interface Option {
  key: string
  label: string
  food?: PickedFood
  imageVersion?: number | null
  composite?: boolean
  create?: string
}

/**
 * Searchable food combobox. Archived foods are excluded (F-7), and so are the ids in [exclude] and, when
 * [forLogging], ingredient-only foods (F-15).
 * Typing a new name offers to create the food with only its name (F-1), so logging is never blocked
 * on catalog work.
 */
export function FoodPicker({
  value,
  onChange,
  autoFocus,
  ariaLabel,
  exclude,
  forLogging,
}: {
  value: PickedFood | null
  onChange: (food: PickedFood | null) => void
  autoFocus?: boolean
  ariaLabel?: string
  exclude?: ReadonlySet<number>
  forLogging?: boolean
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const id = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState(value?.name ?? '')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [lastValue, setLastValue] = useState(value)

  // Keep the text in sync when the parent changes the value (e.g. editing another entry).
  if (value !== lastValue) {
    setLastValue(value)
    setText(value?.name ?? '')
  }

  const query = useDebounced(text.trim(), 120)
  const foods = useFoods(open ? query : '', false)
  const matches = (foods.data ?? []).filter((f) => !exclude?.has(f.id) && !(forLogging && f.ingredientOnly)).slice(0, 12)
  const exact = (foods.data ?? []).some((f) => f.name.toLowerCase() === text.trim().toLowerCase())
  const options: Option[] = [
    ...matches.map((f) => ({ key: `f${f.id}`, label: f.name, food: { id: f.id, name: f.name }, imageVersion: f.imageVersion, composite: f.composite })),
    ...(text.trim() && !exact ? [{ key: 'create', label: t('foodPicker.create', { name: text.trim() }), create: text.trim() }] : []),
  ]

  async function choose(option: Option) {
    setError(null)
    if (option.food) {
      onChange(option.food)
      setText(option.food.name)
    } else if (option.create) {
      try {
        // New foods start with the remembered reference amount (F-12), like on the foods page.
        const ref = await queryClient.fetchQuery({ queryKey: ['foods', 'ref-default'], queryFn: endpoints.foodRefDefault })
        const created = await endpoints.createFood({ name: option.create, refAmount: ref.refAmount, refUnitId: ref.refUnitId })
        await queryClient.invalidateQueries({ queryKey: ['foods'] })
        onChange({ id: created.id, name: created.name })
        setText(created.name)
      } catch (e) {
        setError(errorMessage(e))
      }
    }
    setOpen(false)
  }

  return (
    <div className="combobox">
      <input
        ref={inputRef}
        id={`${id}-input`}
        className="input"
        role="combobox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-autocomplete="list"
        aria-label={ariaLabel}
        aria-activedescendant={open && options[active] ? `${id}-${options[active].key}` : undefined}
        placeholder={t('foodPicker.placeholder')}
        autoComplete="off"
        autoFocus={autoFocus}
        value={text}
        onChange={(event) => {
          setText(event.target.value)
          setOpen(true)
          setActive(0)
          if (value) onChange(null)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            setOpen(true)
            setActive((a) => Math.min(a + 1, options.length - 1))
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            setActive((a) => Math.max(a - 1, 0))
          } else if (event.key === 'Enter' && open && options[active]) {
            event.preventDefault()
            void choose(options[active])
          } else if (event.key === 'Escape') {
            setOpen(false)
          }
        }}
      />
      {open && options.length > 0 && (
        <ul className="combobox-list" id={`${id}-list`} role="listbox">
          {options.map((option, index) => (
            <li
              key={option.key}
              id={`${id}-${option.key}`}
              role="option"
              aria-selected={index === active}
              className={`combobox-option ${index === active ? 'active' : ''} ${option.create ? 'create' : ''}`}
              onMouseDown={(event) => {
                event.preventDefault()
                void choose(option)
              }}
              onMouseEnter={() => setActive(index)}
            >
              {option.food && <FoodThumb foodId={option.food.id} version={option.imageVersion ?? null} name={option.label} />}
              {option.label}
              {option.composite && <span className="badge badge-info">{t('composite.badge')}</span>}
            </li>
          ))}
        </ul>
      )}
      {error && <p className="error-text">{error}</p>}
    </div>
  )
}
