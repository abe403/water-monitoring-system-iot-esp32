import {
  Activity,
  Bell,
  Droplets,
  Gauge,
  Menu,
  Radio,
  RefreshCw,
  Server,
  X,
} from "lucide-react";
import { useEffect, useRef, useState, type PropsWithChildren } from "react";
import { NavLink } from "react-router-dom";
import { useLiveTelemetry } from "../live/useLiveTelemetry";
import { formatTime } from "../lib/format";

const navigation = [
  { to: "/", label: "Overview", icon: Gauge, end: true },
  { to: "/devices", label: "Devices", icon: Server },
  { to: "/alerts", label: "Alerts", icon: Bell },
];

export function AppShell({ children }: PropsWithChildren) {
  const [open, setOpen] = useState(false);
  const [now, setNow] = useState(() => new Date());
  const railRef = useRef<HTMLElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const utilityRef = useRef<HTMLElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  const mobileNavRef = useRef<HTMLElement>(null);
  const { status, lastEventAt, reconnect } = useLiveTelemetry();

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!open) return;

    const rail = railRef.current;
    const menuButton = menuButtonRef.current;
    const background = [utilityRef.current, mainRef.current, mobileNavRef.current];
    const previousOverflow = document.body.style.overflow;
    background.forEach((element) => element?.setAttribute("inert", ""));
    document.body.style.overflow = "hidden";

    const focusable = () => Array.from(
      rail?.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])') ?? [],
    );
    focusable()[0]?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setOpen(false);
        return;
      }
      if (event.key !== "Tab") return;
      const items = focusable();
      if (!items.length) return;
      const first = items[0]!;
      const last = items.at(-1)!;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      background.forEach((element) => element?.removeAttribute("inert"));
      document.body.style.overflow = previousOverflow;
      menuButton?.focus();
    };
  }, [open]);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to operations</a>
      <aside
        ref={railRef}
        id="primary-navigation-drawer"
        className={`side-rail ${open ? "side-rail--open" : ""}`}
        aria-label="Primary navigation"
        aria-modal={open ? "true" : undefined}
        role={open ? "dialog" : undefined}
      >
        <div className="brand-lockup">
          <Droplets aria-hidden="true" />
          <span>Waterline</span>
          <button className="icon-button rail-close" onClick={() => setOpen(false)} aria-label="Close navigation">
            <X aria-hidden="true" />
          </button>
        </div>
        <nav className="primary-nav">
          {navigation.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)}>
              <Icon aria-hidden="true" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="rail-foot">
          <span className="rail-foot__mark">WTM</span>
          <span>Operator console</span>
        </div>
      </aside>

      {open && <button className="rail-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} />}

      <header ref={utilityRef} className="utility-bar">
        <button
          ref={menuButtonRef}
          className="icon-button mobile-menu"
          onClick={() => setOpen(true)}
          aria-label="Open navigation"
          aria-expanded={open}
          aria-controls="primary-navigation-drawer"
        >
          <Menu aria-hidden="true" />
        </button>
        <div className="utility-clock">
          <span>Local time</span>
          <strong>{formatTime(now.toISOString())}</strong>
        </div>
        <div className={`live-indicator live-indicator--${status}`} role="status" aria-live="polite">
          {status === "live" ? <Radio aria-hidden="true" /> : <Activity aria-hidden="true" />}
          <div>
            <span>Transport</span>
            <strong>{status === "live" ? "Connected live" : status === "connecting" ? "Connecting" : "Live link offline"}</strong>
          </div>
        </div>
        <div className="utility-event">
          <span>Last live event</span>
          <strong>{lastEventAt ? formatTime(lastEventAt) : "Waiting"}</strong>
        </div>
        <button className="utility-reconnect" onClick={reconnect} disabled={status === "connecting"}>
          <RefreshCw aria-hidden="true" />
          Reconnect
        </button>
      </header>

      <main ref={mainRef} id="main-content" className="main-surface" tabIndex={-1}>{children}</main>

      <nav ref={mobileNavRef} className="mobile-nav" aria-label="Mobile navigation">
        {navigation.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end}>
            <Icon aria-hidden="true" />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
