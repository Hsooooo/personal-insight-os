import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Settings as SettingsIcon, Plus, Trash2, KeyRound } from 'lucide-react';

export default function Settings() {
  const queryClient = useQueryClient();
  const [isAdding, setIsAdding] = useState(false);
  const [newProvider, setNewProvider] = useState({
    providerName: 'OpenAI',
    apiKey: '',
    defaultChatModel: 'gpt-4o-mini',
    embeddingModel: '',
  });

  const { data: providers, isLoading } = useQuery({
    queryKey: ['llmProviders'],
    queryFn: api.llmProviders.list,
  });

  const createMutation = useMutation({
    mutationFn: api.llmProviders.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['llmProviders'] });
      setIsAdding(false);
      setNewProvider({
        providerName: 'OpenAI',
        apiKey: '',
        defaultChatModel: 'gpt-4o-mini',
        embeddingModel: '',
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.llmProviders.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['llmProviders'] }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Settings</h2>
        <p className="text-muted-foreground">Manage your LLM providers and preferences</p>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center gap-2">
          <KeyRound className="h-5 w-5 text-indigo-500" />
          <div>
            <CardTitle>LLM Providers</CardTitle>
            <CardDescription>Configure AI providers for insights generation</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {isLoading ? (
            <Skeleton className="h-32" />
          ) : (
            <>
              {providers?.length ? (
                <div className="space-y-3">
                  {providers.map((provider) => (
                    <div
                      key={provider.id}
                      className="flex items-center justify-between rounded-lg border p-4"
                    >
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-medium">{provider.providerName}</span>
                          <Badge variant={provider.enabled ? 'default' : 'secondary'}>
                            {provider.enabled ? 'Enabled' : 'Disabled'}
                          </Badge>
                        </div>
                        <p className="text-sm text-muted-foreground">
                          Model: {provider.defaultChatModel || 'Not set'}
                        </p>
                      </div>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => deleteMutation.mutate(provider.id)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No providers configured yet.</p>
              )}

              {isAdding ? (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    createMutation.mutate(newProvider);
                  }}
                  className="space-y-3 rounded-lg border p-4"
                >
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="space-y-2">
                      <Label>Provider</Label>
                      <select
                        value={newProvider.providerName}
                        onChange={(e) =>
                          setNewProvider({ ...newProvider, providerName: e.target.value })
                        }
                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                      >
                        <option value="OpenAI">OpenAI</option>
                        <option value="Anthropic">Anthropic</option>
                        <option value="Google">Google Gemini</option>
                      </select>
                    </div>
                    <div className="space-y-2">
                      <Label>Model</Label>
                      <Input
                        value={newProvider.defaultChatModel}
                        onChange={(e) =>
                          setNewProvider({ ...newProvider, defaultChatModel: e.target.value })
                        }
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>API Key</Label>
                    <Input
                      type="password"
                      value={newProvider.apiKey}
                      onChange={(e) =>
                        setNewProvider({ ...newProvider, apiKey: e.target.value })
                      }
                      placeholder="sk-..."
                      required
                    />
                  </div>
                  <div className="flex gap-2">
                    <Button type="submit" disabled={createMutation.isPending}>
                      Add Provider
                    </Button>
                    <Button variant="outline" onClick={() => setIsAdding(false)}>
                      Cancel
                    </Button>
                  </div>
                </form>
              ) : (
                <Button variant="outline" onClick={() => setIsAdding(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  Add Provider
                </Button>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center gap-2">
          <SettingsIcon className="h-5 w-5 text-slate-500" />
          <div>
            <CardTitle>About</CardTitle>
            <CardDescription>Personal Insight OS MVP</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 text-sm text-muted-foreground">
            <p>Version: 1.0.0 (MVP)</p>
            <p>Backend: Spring Boot 3.3 + Java 21</p>
            <p>Frontend: React 19 + TypeScript + Tailwind CSS</p>
            <p>Database: PostgreSQL 16 + Neo4j 5</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
