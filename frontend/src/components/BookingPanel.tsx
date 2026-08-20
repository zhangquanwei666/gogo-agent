import { useState } from 'react'

type TabKey = 'flight' | 'hotel' | 'train' | 'car'

interface TabDef {
  key: TabKey
  icon: string
  label: string
  /** 三个输入框的标签和占位符，按业务类型换 */
  fields: [string, string][]
}

const TABS: TabDef[] = [
  {
    key: 'flight',
    icon: '✈',
    label: '机票',
    fields: [
      ['出发城市', '北京'],
      ['到达城市', '上海'],
      ['出发日期', '请选择日期'],
    ],
  },
  {
    key: 'hotel',
    icon: '🏨',
    label: '酒店',
    fields: [
      ['目的地', '上海'],
      ['入住日期', '请选择日期'],
      ['退房日期', '请选择日期'],
    ],
  },
  {
    key: 'train',
    icon: '🚄',
    label: '火车票',
    fields: [
      ['出发城市', '北京'],
      ['到达城市', '上海'],
      ['出发日期', '请选择日期'],
    ],
  },
  {
    key: 'car',
    icon: '🚗',
    label: '用车',
    fields: [
      ['上车地点', '请输入地点'],
      ['下车地点', '请输入地点'],
      ['用车时间', '请选择时间'],
    ],
  },
]

interface Props {
  userName: string
  onSearch: (label: string) => void
}

/** 预订面板：机票 / 酒店 / 火车票 / 用车 四个 Tab，目前是纯 UI，没接后端 */
export default function BookingPanel({ userName, onSearch }: Props) {
  const [active, setActive] = useState<TabKey>('flight')
  const tab = TABS.find((t) => t.key === active)!

  return (
    <section className="booking">
      <h1>{userName}，欢迎回来</h1>
      <p className="sub">今天想去哪儿？差旅标准已按公司政策自动匹配</p>

      <div className="booking-tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={t.key === active ? 'active' : ''}
            onClick={() => setActive(t.key)}
          >
            <span>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {/* key 让切 Tab 时输入框重新挂载，不会把上一个业务填的值带过来 */}
      <div className="booking-form" key={active}>
        {tab.fields.map(([label, placeholder]) => (
          <div className="booking-field" key={label}>
            <label>{label}</label>
            <input placeholder={placeholder} />
          </div>
        ))}
        <button className="booking-search" onClick={() => onSearch(tab.label)}>
          搜索
        </button>
      </div>
    </section>
  )
}
