import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { Goal } from '@/types';
import { GOAL_TEMPLATES, RACE_DISTANCES, formatRaceTime, parseRaceTime } from './raceGoal';


const CUSTOM_DISTANCE = 'custom';

function initialDistance(goal?: Goal): { preset: string; custom: string } {
  const km = goal?.raceDistanceKm;
  if (km == null) return { preset: '21.0975', custom: '' };
  const preset = RACE_DISTANCES.find((d) => Number(d.value) === Number(km));
  return preset ? { preset: preset.value, custom: '' } : { preset: CUSTOM_DISTANCE, custom: String(km) };
}

interface GoalFormProps {
  heading: string;
  submitLabel: string;
  initial?: Goal;
  pending?: boolean;
  error?: string | null;
  onSubmit: (goal: Partial<Goal>) => void;
  onCancel: () => void;
}

export function GoalForm({ heading, submitLabel, initial, pending, error, onSubmit, onCancel }: GoalFormProps) {
  const isKnownType = GOAL_TEMPLATES.some((t) => t.value === initial?.goalType);
  const [goalType, setGoalType] = useState<string>(initial?.goalType ?? 'RACE');
  const [title, setTitle] = useState(initial?.title ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [targetValue, setTargetValue] = useState(() => {
    if (initial?.targetValue == null) return '';
    return initial.goalType === 'RACE' ? formatRaceTime(initial.targetValue) : String(initial.targetValue);
  });
  const [startDate, setStartDate] = useState(initial?.startDate ?? '');
  const [targetDate, setTargetDate] = useState(initial?.targetDate ?? '');
  const [distance, setDistance] = useState(() => initialDistance(initial));
  const [localError, setLocalError] = useState<string | null>(null);

  const isRace = goalType === 'RACE';
  const template = GOAL_TEMPLATES.find((t) => t.value === goalType);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    const payload: Partial<Goal> = {
      title,
      goalType,
      description: description || undefined,
      startDate: startDate || undefined,
      targetDate: targetDate || undefined,
    };
    if (isRace) {
      const seconds = parseRaceTime(targetValue);
      if (seconds == null) {
        setLocalError('목표 기록은 시:분 또는 시:분:초 형식으로 입력하세요 (예: 1:35 또는 0:22:30).');
        return;
      }
      const km = Number(distance.preset === CUSTOM_DISTANCE ? distance.custom : distance.preset);
      if (!Number.isFinite(km) || km <= 0) {
        setLocalError('대회 거리를 km 단위로 입력하세요.');
        return;
      }
      payload.targetValue = seconds;
      payload.targetUnit = 's';
      payload.raceDistanceKm = km;
    } else {
      payload.targetValue = targetValue ? Number(targetValue) : undefined;
      payload.targetUnit = template?.unit;
    }
    onSubmit(payload);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{heading}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="goal-title">Title</Label>
              <Input
                id="goal-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder={isRace ? 'e.g., 동아마라톤 3:30 under' : 'e.g., 주간 러닝 40km'}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="goal-type">Type</Label>
              <Select value={goalType} onValueChange={setGoalType}>
                <SelectTrigger id="goal-type">
                  <SelectValue placeholder="목표 유형" />
                </SelectTrigger>
                <SelectContent>
                  {!isKnownType && initial?.goalType && (
                    <SelectItem value={initial.goalType}>{initial.goalType}</SelectItem>
                  )}
                  {GOAL_TEMPLATES.map((t) => (
                    <SelectItem key={t.value} value={t.value}>
                      {t.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {isRace && (
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="goal-race-distance">Distance</Label>
                <Select
                  value={distance.preset}
                  onValueChange={(preset) => setDistance({ ...distance, preset })}
                >
                  <SelectTrigger id="goal-race-distance">
                    <SelectValue placeholder="대회 거리" />
                  </SelectTrigger>
                  <SelectContent>
                    {RACE_DISTANCES.map((d) => (
                      <SelectItem key={d.value} value={d.value}>
                        {d.label}
                      </SelectItem>
                    ))}
                    <SelectItem value={CUSTOM_DISTANCE}>직접 입력</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {distance.preset === CUSTOM_DISTANCE && (
                <div className="space-y-2">
                  <Label htmlFor="goal-race-distance-custom">Distance (km)</Label>
                  <Input
                    id="goal-race-distance-custom"
                    type="number"
                    step="0.001"
                    min="0.1"
                    value={distance.custom}
                    onChange={(e) => setDistance({ ...distance, custom: e.target.value })}
                    placeholder="30"
                    required
                  />
                </div>
              )}
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="goal-target">
                {isRace ? 'Target time (시:분:초)' : `Target (${template?.unit || 'value'})`}
              </Label>
              <Input
                id="goal-target"
                type={isRace ? 'text' : 'number'}
                inputMode={isRace ? 'numeric' : undefined}
                step={isRace ? undefined : '0.1'}
                value={targetValue}
                onChange={(e) => setTargetValue(e.target.value)}
                placeholder={template?.placeholder}
                required={isRace || isKnownType || !initial}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="goal-start">Start date</Label>
              <Input id="goal-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="goal-target-date">{isRace ? 'Race date' : 'Target date'}</Label>
              <Input
                id="goal-target-date"
                type="date"
                value={targetDate}
                onChange={(e) => setTargetDate(e.target.value)}
                required={isRace}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="goal-description">Description</Label>
            <Input
              id="goal-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={isRace ? '대회명, 코스 특징 등' : 'Describe your goal'}
            />
          </div>
          {isRace && (
            <p className="text-xs text-muted-foreground">
              최근 6주 러닝 중 가장 좋은 기록을 대회 거리로 환산해 예상 기록을 보여줍니다 (Riegel 공식).
            </p>
          )}
          {(localError || error) && (
            <p role="alert" className="text-sm text-destructive">
              {localError || error}
            </p>
          )}
          <div className="flex gap-2">
            <Button type="submit" disabled={pending}>
              {submitLabel}
            </Button>
            <Button variant="outline" type="button" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
