import { describe, expect, it } from 'vitest';
import { getTagColor, isRunningType, SORT_OPTIONS, TAG_PRESETS } from './utils';

describe('activities utils', () => {
  it('getTagColor picks a color per race distance', () => {
    expect(getTagColor(null)).toBe('');
    expect(getTagColor('5K / 레이스')).toContain('blue');
    expect(getTagColor('10K / 레이스')).toContain('indigo');
    expect(getTagColor('하프 / 레이스')).toContain('purple');
    expect(getTagColor('풀 / 레이스')).toContain('orange');
    expect(getTagColor('회복주')).toContain('slate');
  });

  it('every preset tag gets a non-default color', () => {
    for (const tag of TAG_PRESETS) expect(getTagColor(tag)).not.toContain('slate');
  });

  it('isRunningType', () => {
    expect(isRunningType('RUNNING')).toBe(true);
    expect(isRunningType('trail_running')).toBe(true);
    expect(isRunningType('strength_training')).toBe(false);
    expect(isRunningType(null)).toBe(false);
  });

  it('sort options are "field,dir" pairs', () => {
    for (const o of SORT_OPTIONS) expect(o.value).toMatch(/^\w+,(asc|desc)$/);
  });
});
