import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const FUTURE_PROVIDERS = ['Strava', 'Apple Health', 'Oura Ring', 'Withings', 'Fitbit'];

export function ComingSoonCard() {
  return (
    <Card className="md:col-span-2">
      <CardHeader>
        <CardTitle>Coming Soon</CardTitle>
        <CardDescription>Additional wearable sources are planned later — not available in this release</CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-5">
          {FUTURE_PROVIDERS.map((name) => (
            <li key={name} className="flex items-center justify-between rounded-lg border border-dashed p-3 opacity-60">
              <span className="text-sm font-medium text-muted-foreground">{name}</span>
              <Badge variant="outline">Unavailable</Badge>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
