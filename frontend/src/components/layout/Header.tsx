import { useState } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet';
import { SidebarNav } from './Sidebar';
import { LogOut, Menu } from 'lucide-react';

export function Header() {
  const { user, logout } = useAuthStore();
  const [sheetOpen, setSheetOpen] = useState(false);

  const handleLogout = async () => {
    try {
      await api.auth.logout();
    } catch {
      // ignore server errors on logout
    } finally {
      logout();
      window.location.href = '/login';
    }
  };

  return (
    <header className="flex h-16 items-center justify-between border-b bg-card px-4 md:px-8">
      <div className="flex items-center gap-3">
        <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="md:hidden">
              <Menu className="h-5 w-5" />
              <span className="sr-only">Open menu</span>
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-64 p-0">
            <div className="flex h-16 items-center border-b px-6">
              <span className="text-lg font-bold tracking-tight">Insight OS</span>
            </div>
            <div className="p-4">
              <SidebarNav onNavigate={() => setSheetOpen(false)} />
            </div>
            <div className="border-t p-4">
              <p className="text-xs text-muted-foreground">Personal Insight OS v1.0</p>
            </div>
          </SheetContent>
        </Sheet>

        <div>
          <h1 className="text-lg font-semibold tracking-tight md:text-xl">Personal Insight OS</h1>
          <p className="hidden text-xs text-muted-foreground md:block">Your data, your insights</p>
        </div>
      </div>

      <div className="flex items-center gap-3 md:gap-4">
        {user && (
          <>
            <div className="flex items-center gap-2">
              <Avatar className="h-8 w-8">
                <AvatarFallback className="bg-indigo-100 text-xs font-bold text-indigo-700">
                  {user.displayName?.charAt(0) || user.email.charAt(0).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="hidden max-w-[160px] md:block lg:max-w-xs">
                <p className="truncate text-sm font-medium">
                  {user.displayName || user.email}
                </p>
                <p className="truncate text-xs text-muted-foreground">{user.email}</p>
              </div>
            </div>
            <Button variant="ghost" size="icon" onClick={handleLogout}>
              <LogOut className="h-4 w-4" />
            </Button>
          </>
        )}
      </div>
    </header>
  );
}
