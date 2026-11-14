import { useLocation, useNavigate } from 'react-router-dom';
import { GitGraph } from 'lucide-react';
import { LoginForm } from '@/components/auth/LoginForm';
import { getRedirectTarget } from '@/lib/authRedirect';

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2">
          <GitGraph className="h-8 w-8 text-indigo-500" aria-hidden="true" />
          <span className="text-2xl font-bold tracking-tight">Personal Insight OS</span>
        </div>
        <LoginForm onSuccess={() => navigate(getRedirectTarget(location.state), { replace: true })} />
      </div>
    </div>
  );
}
