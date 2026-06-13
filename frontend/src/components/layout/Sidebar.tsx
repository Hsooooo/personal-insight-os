import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Database,
  Activity,
  HeartPulse,
  GitGraph,
  MessageSquare,
  Lightbulb,
  Target,
  Settings,
  Bot,
} from 'lucide-react';
import { cn } from '@/lib/utils';

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/data-sources', icon: Database, label: 'Data Sources' },
  { to: '/activities', icon: Activity, label: 'Activities' },
  { to: '/health', icon: HeartPulse, label: 'Health' },
  { to: '/graph', icon: GitGraph, label: 'Graph' },
  { to: '/ask', icon: MessageSquare, label: 'Ask My Data' },
  { to: '/insights', icon: Lightbulb, label: 'Insights' },
  { to: '/goals', icon: Target, label: 'Goals' },
  { to: '/mcp-settings', icon: Bot, label: 'MCP 연동' },
  { to: '/settings', icon: Settings, label: 'Settings' },
];

interface SidebarNavProps {
  onNavigate?: () => void;
  className?: string;
}

export function SidebarNav({ onNavigate, className }: SidebarNavProps) {
  return (
    <nav className={cn('space-y-1', className)}>
      {navItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
              isActive
                ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/50 dark:text-indigo-300'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground'
            )
          }
        >
          <item.icon className="h-4 w-4" />
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

export function Sidebar() {
  return (
    <aside className="fixed left-0 top-0 z-30 hidden h-screen w-64 flex-col border-r bg-card md:flex">
      <div className="flex h-16 items-center border-b px-6">
        <GitGraph className="mr-2 h-6 w-6 text-indigo-500" />
        <span className="text-lg font-bold tracking-tight">Insight OS</span>
      </div>
      <div className="flex-1 p-4">
        <SidebarNav />
      </div>
      <div className="border-t p-4">
        <p className="text-xs text-muted-foreground">Personal Insight OS v1.0</p>
      </div>
    </aside>
  );
}
