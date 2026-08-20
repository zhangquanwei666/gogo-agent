interface Feature {
  num: string
  label: string
}

interface Props {
  title: React.ReactNode
  subtitle: React.ReactNode
  features: Feature[]
}

/** 左侧品牌展示区，登录和注册页共用，只是文案不同 */
export default function BrandPanel({ title, subtitle, features }: Props) {
  return (
    <section className="brand-panel">
      <div className="brand-logo">
        <div className="mark">G</div>
        <div className="name">GoGo 智能商旅</div>
      </div>
      <h1 className="brand-title">{title}</h1>
      <p className="brand-sub">{subtitle}</p>
      <div className="brand-features">
        {features.map((f) => (
          <div className="feature" key={f.label}>
            <div className="num">{f.num}</div>
            <div className="label">{f.label}</div>
          </div>
        ))}
      </div>
    </section>
  )
}
