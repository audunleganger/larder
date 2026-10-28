import { useTranslation } from 'react-i18next'
import type { UnitDto, UsableUnit } from '../api/types.gen'
import { unitLabel } from '../lib/names'

interface Common {
  value: number | ''
  /** '' when the first option, [placeholder], is chosen. */
  onChange: (unitId: number | '') => void
  placeholder: string
  /** Whether the placeholder can be chosen again once a unit is. */
  placeholderDisabled?: boolean
  /** The amount the unit is for, so names are singular or plural (U-8); null: singular. */
  quantity?: number | null
  className?: string
  disabled?: boolean
  'aria-label'?: string
}

function toUnitId(value: string): number | '' {
  return value ? Number(value) : ''
}

/** A picker of [units], as given: callers leave out the hidden ones they don't want offered. */
export function UnitSelect({ units, value, onChange, placeholder, placeholderDisabled, quantity = null, className = 'input', ...rest }: Common & { units: UnitDto[] }) {
  return (
    <select className={className} value={value} onChange={(e) => onChange(toUnitId(e.target.value))} {...rest}>
      <option value="" disabled={placeholderDisabled}>
        {placeholder}
      </option>
      {units.map((u) => (
        <option key={u.id} value={u.id}>
          {unitLabel(u.displayName, u.displayPlural, quantity)}
        </option>
      ))}
    </select>
  )
}

/**
 * A picker of units for one food: the food's [usable] units first (⚠ for one without a size), then the other
 * units the user shows, plus the chosen one if it's hidden.
 */
export function FoodUnitSelect({ usable, units, value, onChange, placeholder, placeholderDisabled, quantity = null, className = 'input', ...rest }: Common & { usable: UsableUnit[]; units: UnitDto[] }) {
  const { t } = useTranslation()
  const usableIds = new Set(usable.map((u) => u.unitId))
  const otherUnits = units.filter((u) => !usableIds.has(u.id) && (!u.hidden || u.id === value))
  return (
    <select className={className} value={value} onChange={(e) => onChange(toUnitId(e.target.value))} {...rest}>
      <option value="" disabled={placeholderDisabled}>
        {placeholder}
      </option>
      {usable.length > 0 && (
        <optgroup label={t('entry.unitsForFood')}>
          {usable.map((u) => (
            <option key={u.unitId} value={u.unitId}>
              {unitLabel(u.name, u.plural, quantity)}
              {u.amountInRefUnit === null ? ' ⚠' : ''}
            </option>
          ))}
        </optgroup>
      )}
      {otherUnits.length > 0 && (
        <optgroup label={t('entry.otherUnits')}>
          {otherUnits.map((u) => (
            <option key={u.id} value={u.id}>
              {unitLabel(u.displayName, u.displayPlural, quantity)}
            </option>
          ))}
        </optgroup>
      )}
    </select>
  )
}
