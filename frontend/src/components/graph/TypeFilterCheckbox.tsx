interface TypeFilterCheckboxProps {
  label: string;
  color: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** node: 원형 스와치, edge: 선형 스와치 */
  variant: 'node' | 'edge';
}

export function TypeFilterCheckbox({ label, color, checked, onChange, variant }: TypeFilterCheckboxProps) {
  return (
    <label className="group flex cursor-pointer select-none items-center gap-2">
      <input
        type="checkbox"
        className="peer h-4 w-4 cursor-pointer rounded border border-input accent-primary"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <div
        className={variant === 'node' ? 'h-3 w-3 shrink-0 rounded-full' : 'h-0.5 w-4 shrink-0'}
        style={{ backgroundColor: color }}
        aria-hidden="true"
      />
      <span className="text-sm text-foreground transition-colors group-hover:text-primary">{label}</span>
    </label>
  );
}
