import Sidebar from "./Sidebar";

/**
 * Two-column shell: persistent sidebar + main content. Server-renderable —
 * sidebar opts into client at its own boundary via "use client".
 */
export default function PageShell({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", minHeight: "100vh" }}>
      <Sidebar />
      <main style={{ flex: 1, padding: "32px 40px", maxWidth: 1400 }}>{children}</main>
    </div>
  );
}
