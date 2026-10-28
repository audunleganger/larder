import type { InputHTMLAttributes } from 'react'
import { isNonNegative, parseDecimal } from '../lib/format'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> & {
  value: string
  onChange: (value: string) => void
}

/** Text input for decimals; accepts "," and "." (L-2). Marks itself invalid when unparsable. */
export function DecimalInput({ value, onChange, className = '', ...rest }: Props) {
  const parsed = parseDecimal(value)
  const invalid = parsed !== null && !isNonNegative(parsed)
  return (
    <input
      {...rest}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      className={`input ${invalid ? 'invalid' : ''} ${className}`}
      aria-invalid={invalid || undefined}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
