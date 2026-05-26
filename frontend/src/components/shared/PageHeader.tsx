type Props = {
  title: string;
  subtitle?: string;
  badge?: string;
};

export default function PageHeader({ title, subtitle, badge }: Props) {
  return (
    <header style={{ marginBottom: 28 }}>
      <div style={{ display: "flex", alignItems: "baseline", gap: 12 }}>
        <h1
          style={{
            fontSize: 28,
            fontWeight: 800,
            letterSpacing: "-0.03em",
            color: "var(--text-primary)",
            margin: 0,
          }}
        >
          {title}
        </h1>
        {badge && (
          <span
            style={{
              fontSize: 10,
              fontWeight: 700,
              padding: "3px 8px",
              borderRadius: 12,
              background: "rgba(59, 130, 246, 0.12)",
              color: "var(--accent-blue)",
              letterSpacing: "0.05em",
            }}
          >
            {badge}
          </span>
        )}
      </div>
      {subtitle && (
        <p style={{ marginTop: 6, color: "var(--text-secondary)", fontSize: 14 }}>{subtitle}</p>
      )}
    </header>
  );
}
