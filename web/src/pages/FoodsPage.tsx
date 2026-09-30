import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useFoods, useUnits } from '../api/queries'
import { Badge, Card, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { formatNumber } from '../lib/format'
import { useDebounced } from '../lib/useDebounced'

export function FoodsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [newName, setNewName] = useState('')
  const foods = useFoods(useDebounced(search.trim(), 150), showArchived)
  const units = useUnits(true)
  const unitName = (id: number | null) => units.data?.find((u) => u.id === id)?.name ?? ''
  const create = useApiMutation(endpoints.createFood)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const food = await create.mutateAsync({ name: newName })
    setNewName('')
    navigate(`/foods/${food.id}`)
  }

  return (
    <div className="page">
      <PageHeader title={t('foods.title')} subtitle={t('foods.subtitle')} />
      <Card title={t('foods.new')}>
        <form className="inline-form" onSubmit={(e) => void submit(e).catch(() => undefined)}>
          <input className="input grow" placeholder={t('foods.namePlaceholder')} value={newName} onChange={(e) => setNewName(e.target.value)} required />
          <button type="submit" className="btn btn-primary" disabled={create.isPending}>
            {t('common.create')}
          </button>
        </form>
        <p className="field-hint">{t('foods.newHint')}</p>
        <ErrorText error={create.error} />
      </Card>
      <Card
        title={t('foods.all')}
        actions={
          <label className="checkbox">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> {t('common.showArchived')}
          </label>
        }
      >
        <input className="input" type="search" placeholder={t('foods.search')} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t('foods.search')} />
        <QueryView query={foods}>
          {(data) =>
            data.length === 0 ? (
              <Empty>{search ? t('foods.noMatches') : t('foods.empty')}</Empty>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('foods.reference')}</th>
                    <th className="num">{t('foods.nutrientCount')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((food) => (
                    <tr key={food.id}>
                      <td>
                        <Link to={`/foods/${food.id}`}>{food.name}</Link> {food.archived && <Badge>{t('common.archived')}</Badge>}
                      </td>
                      <td>
                        {food.refAmount !== null ? (
                          `${t('foods.per')} ${formatNumber(food.refAmount, 3)} ${unitName(food.refUnitId)}`
                        ) : (
                          <Badge tone="warning">{t('foods.noReference')}</Badge>
                        )}
                      </td>
                      <td className="num">{food.nutrientCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          }
        </QueryView>
      </Card>
    </div>
  )
}
