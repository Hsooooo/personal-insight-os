import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '@/types';

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: User) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      setAuth: (token, user) => set({ token, user, isAuthenticated: true }),
      logout: () => {
        set({ token: null, user: null, isAuthenticated: false });
        try {
          localStorage.removeItem('pios-auth');
        } catch {
          // ignore storage errors
        }
      },
    }),
    {
      name: 'pios-auth',
      // access 토큰은 메모리에만 보관 (XSS 시 탈취 범위 축소).
      // 새로고침 후에는 httpOnly refresh 쿠키로 재발급된다.
      version: 1,
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
      migrate: (persisted) => {
        const { user = null, isAuthenticated = false } = (persisted ?? {}) as Partial<AuthState>;
        return { user, isAuthenticated };
      },
    }
  )
);
