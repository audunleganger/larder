import { useTranslation } from 'react-i18next'
import type { SharedItem } from '../lib/shared'
import { Badge } from './ui'

/** Offers to show a hidden item whose name the user tried to create again. */
export function TakenHiddenNotice({ item, onShow }: { item: SharedItem; onShow: () => void }) {
  const { t } = useTranslation()
  return (
    <p className="notice" role="status">
      {t('common.takenHidden', { name: item.displayName })}{' '}
      <button type="button" className="btn btn-small" onClick={onShow}>
        {t('common.showIt')}
      </button>
    </p>
  )
}

/** Who made it, and a badge for the built-in ones. */
export function MadeBy({ item }: { item: SharedItem }) {
  const { t } = useTranslation()
  return item.builtIn ? <Badge>{t('common.builtIn')}</Badge> : <span className="muted">{item.createdBy}</span>
}

/** Why the edit form is missing: only the maker and admins can change it. */
export function ReadOnlyNote({ item }: { item: SharedItem }) {
  const { t } = useTranslation()
  return <p className="field-hint">{item.builtIn ? t('common.readOnlyBuiltIn') : t('common.readOnly', { owner: item.createdBy })}</p>
}
