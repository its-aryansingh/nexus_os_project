"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  BarChart3,
  Bot,
  Boxes,
  Coins,
  GitBranch,
  Layers,
  LineChart,
  Plug,
  Settings,
  Sparkles,
  Workflow,
} from "lucide-react";

type NavItem = {
  href: string;
  label: string;
  icon: React.ReactNode;
  group: "core" | "build" | "ops" | "system";
};

const NAV: NavItem[] = [
  { href: "/", label: "Dashboard", icon: <Boxes size={16} />, group: "core" },
  { href: "/agents", label: "Agents", icon: <Bot size={16} />, group: "core" },
  { href: "/studio", label: "Agent Studio", icon: <Sparkles size={16} />, group: "build" },
  { href: "/workflows", label: "Workflows", icon: <Workflow size={16} />, group: "build" },
  { href: "/runs", label: "Workflow Runs", icon: <GitBranch size={16} />, group: "build" },
  { href: "/cost", label: "Cost", icon: <Coins size={16} />, group: "ops" },
  { href: "/observability", label: "Observability", icon: <Activity size={16} />, group: "ops" },
  { href: "/memory", label: "Memory", icon: <Layers size={16} />, group: "ops" },
  { href: "/integrations", label: "Integrations", icon: <Plug size={16} />, group: "system" },
  { href: "/settings", label: "Settings", icon: <Settings size={16} />, group: "system" },
];

const GROUP_LABEL: Record<NavItem["group"], string> = {
  core: "CORE",
  build: "BUILD",
  ops: "OPERATE",
  system: "SYSTEM",
};

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside
      style={{
        width: 240,
        flexShrink: 0,
        padding: "24px 16px",
        borderRight: "1px solid var(--border-subtle)",
        background: "var(--bg-secondary)",
        minHeight: "100vh",
        position: "sticky",
        top: 0,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 28 }}>
        <div
          style={{
            background: "var(--gradient-hero)",
            borderRadius: 10,
            padding: 8,
            display: "flex",
          }}
        >
          <Boxes size={20} color="#fff" />
        </div>
        <div>
          <div style={{ fontWeight: 800, letterSpacing: "-0.02em" }}>Nexus OS</div>
          <div style={{ fontSize: 11, color: "var(--text-muted)" }}>v0.1 Foundation</div>
        </div>
      </div>

      <nav>
        {(["core", "build", "ops", "system"] as const).map((group) => (
          <div key={group} style={{ marginBottom: 22 }}>
            <div
              style={{
                fontSize: 10,
                fontWeight: 700,
                color: "var(--text-muted)",
                letterSpacing: "0.08em",
                padding: "0 8px 6px",
              }}
            >
              {GROUP_LABEL[group]}
            </div>
            {NAV.filter((n) => n.group === group).map((item) => {
              const isActive =
                item.href === "/"
                  ? pathname === "/"
                  : pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 10,
                    padding: "8px 10px",
                    borderRadius: 8,
                    fontSize: 13,
                    fontWeight: 500,
                    color: isActive ? "var(--text-primary)" : "var(--text-secondary)",
                    background: isActive ? "var(--bg-card)" : "transparent",
                    borderLeft: isActive
                      ? "2px solid var(--accent-blue)"
                      : "2px solid transparent",
                    marginBottom: 2,
                    textDecoration: "none",
                  }}
                >
                  <span style={{ opacity: isActive ? 1 : 0.7, display: "flex" }}>{item.icon}</span>
                  {item.label}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      <div
        style={{
          position: "absolute",
          bottom: 20,
          left: 16,
          right: 16,
          padding: 12,
          borderRadius: 10,
          background: "var(--bg-card)",
          border: "1px solid var(--border-subtle)",
          fontSize: 11,
          color: "var(--text-muted)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
          <LineChart size={12} color="var(--accent-emerald)" />
          <span style={{ color: "var(--text-secondary)" }}>Tenant</span>
        </div>
        <div style={{ fontFamily: "var(--font-mono)", fontSize: 10 }}>00000000…0001 (dev)</div>
      </div>
    </aside>
  );
}
